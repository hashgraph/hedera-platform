/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.state;

import com.swirlds.common.notification.Notification;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteNotification;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.threading.ThreadUtils;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.platform.ConsensusRound;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.components.SignatureExpander;
import com.swirlds.platform.components.SystemTransactionHandler;
import com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.eventhandling.SwirldStateSingleTransactionPool;
import com.swirlds.platform.stats.SwirldStateStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static com.swirlds.common.utility.Units.NANOSECONDS_TO_MICROSECONDS;
import static com.swirlds.logging.LogMarker.ERROR;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.QUEUES;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.STARTUP;

/**
 * <p>Manages all interactions with the 3 state objects required by {@link SwirldState}.</p>
 *
 * <p>Three threads modify states in this class: pre-consensus event handler, consensus event handler, and thread-work
 * (managed by this class). Transactions are submitted by a different thread. Other threads can access parts of the
 * states by calling {@link #getCurrentSwirldState()} and {@link #getConsensusState()}. Sync threads access state to
 * check if there is an active freeze period. Careful attention must be paid to changes in this class regarding locking
 * and synchronization in this class and its utility classes.</p>
 */
public class SwirldStateManagerSingle implements SwirldStateManager, ReconnectCompleteListener {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();

	/** The component name for threads managed by this class. */
	private static final String COMPONENT_NAME = "swirld-state-manager-single";

	/** the number of threads that modify state in this class. */
	private static final int THREAD_NUM_SWIRLD_STATE_SINGLE = 3;

	/** this class's name. Used for log output. */
	private static final String CLASS_NAME = SwirldStateManagerSingle.class.getName();

	/** The initial size of the work event queue. */
	private static final int INITIAL_WORK_EVENT_QUEUE_CAPACITY = 100;

	/** The maximum buffer size for the threadWork queue */
	private static final int THREAD_WORK_MAX_BUFFER_SIZE = 100;

	/** A provider of static settings. */
	private final SettingsProvider settings;

	/** This node's id. */
	private final NodeId selfId;

	/** Stats relevant to SwirldState operations. */
	private final SwirldStateStats stats;

	/** Contains self transactions in several queues to be applied to the various states. */
	private final SwirldStateSingleTransactionPool transactionPool;

	/** queue to swap with forWork when shuffling */
	private volatile BlockingQueue<EventImpl> forNext;

	/**
	 * applies event in the forWork queue to stateWork then adds them to forNext. Marked as volatile so its queue can be
	 * shuffled.
	 */
	private volatile QueueThread<EventImpl> threadWork;

	/** reflects all known consensus events */
	private volatile StateInfo stateCons;

	/** current (most recent) state */
	private volatile StateInfo stateCurr;

	/** working state that will replace stateCurr once it catches up */
	private volatile StateInfo stateWork;

	/** used to make threadCons, threadCurr, threadWork all wait during a shuffle */
	private final CyclicBarrier shuffleBarrier;

	/** when the last shuffle happened */
	private volatile Instant lastShuffle;

	/** A supplier of an estimated consensus time for transactions not yet in an event. */
	private final Supplier<Instant> consEstimateSupplier;

	/**
	 * Used to perform a "shuffle" of states and lists in order to update the current state with consensus ordered
	 * transactions.
	 */
	private final Shuffler shuffler;

	/**
	 * Keeps the state returned to the user app to ensure that it doesn't get deleted.
	 * Must only be accessed in synchronized blocks.
	 */
	private State stateCurrReturned = null;

	/** a Semaphore that ensures that only one stateCurr will be passed to the user app at a time */
	private final Semaphore getStateSemaphore = new Semaphore(1);

	/** Handle transactions by applying them to a state */
	private final TransactionHandler transactionHandler;

	/** Expands signatures on pre-consensus transactions. */
	private final SignatureExpander signatureExpander;

	/** Supplies the current size of the queue of transactions waiting to be applied to stateCurr. */
	private final IntSupplier currSizeSupplier;

	/** Supplies the current size of the queue of transactions waiting to be applied to stateWork. */
	private final IntSupplier workSizeSupplier;

	/** Removes and returns a single transaction from the queue of transactions to be applied to stateCurr. */
	private final Supplier<Transaction> pollCurr;

	/** Removes and returns a single transaction from the queue of transactions to be applied to stateWork. */
	private final Supplier<Transaction> pollWork;

	// Used of creating mock instances in unit testing
	public SwirldStateManagerSingle() {
		selfId = null;
		stats = null;
		consEstimateSupplier = null;
		shuffler = null;
		shuffleBarrier = null;
		settings = null;
		transactionPool = null;
		transactionHandler = null;
		signatureExpander = null;
		currSizeSupplier = null;
		workSizeSupplier = null;
		pollCurr = null;
		pollWork = null;
	}

	/**
	 * Creates a new instance with the provided state and starts the work thread.
	 *
	 * @param selfId
	 * 		this node's id
	 * @param systemTransactionHandler
	 * 		the handler for system transactions
	 * @param stats
	 * 		statistics relevant to this class
	 * @param settings
	 * 		a static settings provider
	 * @param consEstimateSupplier
	 * 		an estimator of consensus time for self transactions
	 * @param initialState
	 * 		the initial state of this application
	 */
	public SwirldStateManagerSingle(
			final NodeId selfId,
			final SystemTransactionHandler systemTransactionHandler,
			final SwirldStateStats stats,
			final SettingsProvider settings,
			final Supplier<Instant> consEstimateSupplier,
			final SignatureExpander signatureExpander,
			final State initialState) {
		this.selfId = selfId;
		this.stats = stats;
		this.settings = settings;
		this.consEstimateSupplier = consEstimateSupplier;
		this.signatureExpander = signatureExpander;

		this.transactionPool = new SwirldStateSingleTransactionPool(settings);
		this.transactionHandler = new TransactionHandler(selfId, systemTransactionHandler, stats);
		setState(initialState);

		this.currSizeSupplier = this.transactionPool::getCurrSize;
		this.workSizeSupplier = this.transactionPool::getWorkSize;
		this.pollCurr = this.transactionPool::pollCurr;
		this.pollWork = this.transactionPool::pollWork;

		forNext = newQueue();

		shuffler = new Shuffler();

		shuffleBarrier = new CyclicBarrier(THREAD_NUM_SWIRLD_STATE_SINGLE, () -> {
			try {
				shuffler.shuffle();
			} catch (final Exception e) {
				LOG.error(EXCEPTION.getMarker(), "shuffle exception {}",
						() -> e + Arrays.toString(e.getStackTrace()));
			}
		});

		start();
	}

	/**
	 * {@inheritDoc}
	 */
	public SwirldStateSingleTransactionPool getTransactionPool() {
		return transactionPool;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void expandSignatures(final EventImpl event) {
		signatureExpander.expandSignatures(event.getTransactions(), stateCons.getState());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handlePreConsensusEvent(final EventImpl event) {
		final long startTime = System.nanoTime();

		// Shuffle, if it is time to do so
		shuffleIfTimeToShuffle(stateCurr);

		// Handle our own transactions first
		handleSelfTransactions();

		// This event could have reached consensus while waiting to be handled.
		// Remember isConsensus, in case a thread changes it during this. Better
		// to check the consensus time instead of the isConsensus flag because
		// the time is set after the flag.
		final boolean isConsensus = event.getConsensusTimestamp() != null;

		if (isConsensus && !shouldConsEventBeHandled(event, stateCurr)) {
			stats.preConsensusHandleTime(startTime, System.nanoTime());
			return;
		}

		final Instant consensusTime = getConsensusTime(isConsensus, event);

		transactionHandler.handleEventTransactions(event, consensusTime, isConsensus, stateCurr.getState());

		// Add this event to the threadWork queue it to handle
		if (!threadWork.offer(event)) {
			// this should never happen, because threadWork should have unlimited capacity.
			LOG.error(ERROR.getMarker(), "Offer to threadWork failed");
		}
		stats.preConsensusHandleTime(startTime, System.nanoTime());
	}

	/**
	 * Use consensus time if available. Otherwise, use an estimated time.
	 *
	 * @param isConsensus
	 * 		indicates if the {@code event} has reached consensus
	 * @param event
	 * 		the event
	 * @return the estimated or actual consensus time
	 */
	private Instant getConsensusTime(final boolean isConsensus, final EventImpl event) {
		if (isConsensus) {
			return event.getConsensusTimestamp();
		}
		// time was estimated before, but we update it again here to estimate with the latest information
		event.estimateTime(selfId, stats.getAvgSelfCreatedTimestamp(), stats.getAvgOtherReceivedTimestamp());
		return event.getEstimatedTime();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public InterruptableRunnable getPreConsensusWaitForWorkRunnable() {
		return () -> {
			// Shuffle, if it is time to do so
			shuffleIfTimeToShuffle(stateCurr);

			handleSelfTransactions();
		};
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public InterruptableRunnable getConsensusWaitForWorkRunnable() {
		// Shuffle, if it is time to do so
		return () -> shuffleIfTimeToShuffle(stateCons);
	}

	/**
	 * Prevent self events from being added to the pre-consensus queue because self transactions are processed through
	 * the {@link SwirldStateSingleTransactionPool} {@code transCurr} queue.
	 *
	 * @param event
	 * 		the event to evaluate for adding to the pre-consensus queue
	 * @return true if the event should be added
	 */
	@Override
	public boolean discardPreConsensusEvent(final EventImpl event) {
		return event.getCreatorId() == selfId.getId();
	}

	/**
	 * If there is no event in the forWork event queue, shuffle it is it time, and handle self transactions while
	 * waiting for an item to become available.
	 */
	private void forWorkWaitForItem() {
		// Shuffle, if it is time to do so
		if (shuffleIfTimeToShuffle(stateWork)) {
			return;
		}

		handleWorkTransactions();
	}

	/**
	 * Applies self transactions to the current state.
	 */
	private void handleSelfTransactions() {
		transactionHandler.handleTransactions(currSizeSupplier, pollCurr, consEstimateSupplier,
				stateCurr.getState());
	}

	/**
	 * Applies self transactions to the work state.
	 */
	private void handleWorkTransactions() {
		transactionHandler.handleTransactions(workSizeSupplier, pollWork, consEstimateSupplier,
				stateWork.getState());
	}

	/**
	 * @return Returns the number of milliseconds until the next shuffle is due to occur.
	 */
	private long getMillisUntilNextShuffle() {
		return settings.getDelayShuffle() - lastShuffle.until(Instant.now(), ChronoUnit.MILLIS);
	}

	/**
	 * Performs a shuffle if it is time to do so.
	 *
	 * @param stateInfo
	 * 		the state modified by the calling thread
	 */
	private boolean shuffleIfTimeToShuffle(final StateInfo stateInfo) {
		// block until something is available, or until it's time for a new shuffle
		if (getMillisUntilNextShuffle() <= 0) {
			// wait until all 3 threads are waiting, then call shuffle(), then allow all 3 to continue

			try {
				// if it ever breaks, it will eventually come here and be fixed
				if (shuffleBarrier.isBroken()) {
					shuffleBarrier.reset();
				}
				shuffleBarrier.await(); // wait for all 3 threads, shuffle, continue
				return true;
			} catch (final InterruptedException | BrokenBarrierException e) {
				LOG.error(EXCEPTION.getMarker(),
						"shuffleBarrier interrupted or broken for state {}", stateInfo, e);
				Thread.currentThread().interrupt();
			}
		}
		return false;
	}

	/**
	 * <p>Executed for each event in the threadWork queue. Handles transactions in the work queue before applying the
	 * event to stateWork.</p>
	 *
	 * <p>Unlike the methods to handle events in the pre-consensus and consensus queues, this method does not check to
	 * see if it is time to shuffle. This is because threadQueue removes events from the queue in bulk, then holds
	 * them in a buffer, passing them to this method one by one. If threadWork enters a shuffle here, any events in the
	 * buffer would not be applied to stateWork prior to the shuffle.</p>
	 *
	 * <p>Because the first thing the shuffle does it drain the work transactions and threadWork queue, it is ok to only
	 * check if it is time to shuffle when the threadWork queue is empty. When threadCurr is waiting at the shuffle
	 * barrier, threadWork will continue draining its queue. When it is empty (and threadCurr has stopped putting events
	 * in because it is waiting), threadWork will execute the {@link #forWorkWaitForItem} runnable and see that it is
	 * time to shuffle.</p>
	 *
	 * @param event
	 * 		the event to apply to stateWork
	 */
	private void doWork(final EventImpl event) {
		handleWorkTransactions();

		forWorkEvent(event);
	}

	/**
	 * Applies the event to the work state then adds the event to the forNext queue.
	 *
	 * @param event
	 * 		the event to apply
	 */
	private void forWorkEvent(final EventImpl event) {

		// Discard null events and events with no transactions.
		if (event == null || event.isEmpty()) {
			return;
		}

		// This event could have reached consensus while waiting to be handled.
		// Remember isConsensus, in case a thread changes it during this
		final boolean isConsensus = event.isConsensus();

		if (isConsensus && !shouldConsEventBeHandled(event, stateWork)) {
			return;
		}

		final Instant consensusTime = getConsensusTime(isConsensus, event);

		transactionHandler.handleEventTransactions(event, consensusTime, isConsensus, stateWork.getState());

		// Add this event to the forNext queue so that it can be 
		// applied to the new forWork state after the next shuffle
		if (!forNext.offer(event)) {
			// this should never happen, because forNext should have unlimited capacity.
			LOG.error(EXCEPTION.getMarker(), "forNext.offer returned false");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handleConsensusRound(final ConsensusRound round) {
		for (final EventImpl event : round.getConsensusEvents()) {

			// Shuffle, if it is time to do so
			shuffleIfTimeToShuffle(stateCons);

			// Discard events with no transactions.
			if (event.isEmpty()) {
				continue;
			}

			// Discard events that have already been applied to stateCons
			if (!shouldConsEventBeHandled(event, stateCons)) {
				LOG.error(ERROR.getMarker(),
						"Encountered out of order consensus event! Event Order = {}, stateCons lastCons = {}",
						event.getConsensusOrder(), stateCons.getLastCons());
				continue;
			}

			// if this is a self event, then remove each self transaction from the
			// list of transactions that will be sent to the new stateWork to handle
			// after the next shuffle.
			final Runnable postHandle = selfId.equalsMain(event.getCreatorId()) ? transactionPool::pollCons : null;

			transactionHandler.handleEventTransactions(event, event.getConsensusTimestamp(), true,
					stateCons.getState(), postHandle);
		}
	}

	/**
	 * Check if this event has a consensus time before or equal to the latest consensus event already applied to the
	 * state.
	 *
	 * @param event
	 * 		the event to check
	 * @param stateInfo
	 * 		the state to check
	 * @return true if the event should be applied to the state according to consensus time
	 */
	private static boolean shouldConsEventBeHandled(final EventImpl event, final StateInfo stateInfo) {
		if (!event.isConsensus()) {
			return true;
		}

		// Do not handle this event if it is before the previously handled
		// consensus event according to consensus order. This is how events
		// in the forNext queue get discarded so that they are not applied to stateWork twice.
		if (stateInfo.getLastCons() != null
				&& event.getConsensusOrder() <= stateInfo.getLastCons().getConsensusOrder()) {
			return false;
		}
		stateInfo.setLastCons(event);
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public State getStateForSigning() {
		return SwirldStateManagerUtils.getStateForSigning(stateCons, stats);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void savedStateInFreezePeriod() {
		// set consensus DualState's lastFrozenTime to be current freezeTime
		stateCons.getState().getPlatformDualState().setLastFrozenTimeToBeCurrentFreezeTime();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setState(final State state) {
		state.throwIfReleased("state must not be released");
		state.throwIfImmutable("state must be mutable");

		if (stateCons != null) {
			stateCons.release();
		}
		stateCons = new StateInfo(state, null, false);

		if (stateCurr != null) {
			stateCurr.release();
		}
		stateCurr = new StateInfo(state.copy(), null, false);

		if (stateWork != null) {
			stateWork.release();
		}
		stateWork = new StateInfo(state.copy(), null, false);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void prepareForReconnect() throws InterruptedException {
		// When reconnecting, we need the consensus state to do the reconnect so it will not be cleared.
		ThreadUtils.stopThreads(threadWork);

		LOG.info(RECONNECT.getMarker(), "prepareForReconnect: releasing states");

		// delete the states
		releaseState(stateWork);
		releaseState(stateCurr);

		stateWork = null;

		LOG.info(RECONNECT.getMarker(), "prepareForReconnect: clearing forWork");
		threadWork.clear();

		LOG.info(RECONNECT.getMarker(), "prepareForReconnect: clearing forNext");
		forNext.clear();

		// clear the transactions
		LOG.info(RECONNECT.getMarker(), "prepareForReconnect: clearing transactionPool");
		transactionPool.clear();

		LOG.info(RECONNECT.getMarker(), "prepareForReconnect: {} is now cleared", CLASS_NAME);
	}

	private static void releaseState(final StateInfo stateInfo) {
		try {
			if (stateInfo != null) {
				// There should be no threads modifying the state at this point,
				// so these operations are safe to do serially
				stateInfo.getState().getSwirldState().noMoreTransactions();
				stateInfo.getState().release();
			}
		} catch (final Exception e) {
			// defensive: catch exceptions from a bad app
			LOG.error(EXCEPTION.getMarker(), "exception in {} trying to release state {}:", CLASS_NAME, stateInfo, e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean submitTransaction(final Transaction transaction) {
		return transactionPool.submitTransaction(transaction);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clearFreezeTimes() {
		// It is possible, though unlikely, that this operation is executed multiple times. Each failed attempt will
		// leak a state, but since this is only called during recovery after which the node shuts down, it is
		// acceptable. This leak will be eliminated with ticket swirlds/swirlds-platform/issues/5256.
		stateCons.updateState(s -> {
			s.getPlatformDualState().setFreezeTime(null);
			s.getPlatformDualState().setLastFrozenTimeToBeCurrentFreezeTime();
			return s;
		});
	}

	/**
	 * Called for each {@link Notification} that this listener should handle.
	 *
	 * @param data
	 * 		the notification to be handled
	 */
	@Override
	public void notify(final ReconnectCompleteNotification data) {
		start();
		LOG.info(STARTUP.getMarker(), "SwirldStateManagerSingle received ReconnectCompleteNotification, " +
						"forNext.size: {}, forWork.size: {}",
				forNext.size(),
				threadWork.size());
	}

	/**
	 * Starts the threadWork thread. Should only be invoked when this class is first created or after {@link
	 * #prepareForReconnect()} has been invoked.
	 */
	private void start() {
		threadWork = new QueueThreadConfiguration<EventImpl>()
				.setNodeId(selfId.getId())
				.setComponent(COMPONENT_NAME)
				.setThreadName("thread-work")
				.setInterruptable(true)
				.setMaxBufferSize(THREAD_WORK_MAX_BUFFER_SIZE)
				.setQueue(newQueue())
				.setHandler(this::doWork)
				.setWaitForItemRunnable(this::forWorkWaitForItem)
				.build();

		lastShuffle = Instant.now();
		threadWork.start();
	}

	private static BlockingQueue<EventImpl> newQueue() {
		return new PriorityBlockingQueue<>(INITIAL_WORK_EVENT_QUEUE_CAPACITY, EventUtils::consensusPriorityComparator);
	}

	/**
	 * {@inheritDoc}
	 */
	public SwirldState getCurrentSwirldState() {
		try {
			getStateSemaphore.acquire();
		} catch (final InterruptedException e1) {
			Thread.currentThread().interrupt();
			return null;
		}
		synchronized (this) {
			if (stateCurr == null) {
				return null;
			}

			// stateCurrReturned becomes the current one
			stateCurrReturned = stateCurr.getState();
			return stateCurrReturned.getSwirldState();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public State getConsensusState() {
		return stateCons.getState();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TransThrottleSyncAndCreateRuleResponse shouldSyncAndCreate() {
		return SwirldStateManagerUtils.shouldSyncAndCreate(getConsensusState());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isInterruptable() {
		// This should be changed to false (or this method removed entirely) with ticket swirlds/swirlds-platform#4876
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void releaseCurrentSwirldState() {
		getStateSemaphore.release();
		if (stateCurrReturned != null && (stateCurr == null || stateCurrReturned != stateCurr.getState())) {
			// if the one returned is not the current one, we can delete it
			try {
				stateCurrReturned.release();
			} catch (final Exception e) {
				// defensive: catch exceptions from a bad app
				LOG.error(EXCEPTION.getMarker(), "exception in app during delete:", e);
			}
		}
		stateCurrReturned = null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isInFreezePeriod(final Instant timestamp) {
		return SwirldStateManagerUtils.isInFreezePeriod(timestamp, getConsensusState());
	}

	/**
	 * Performs a "shuffle" of states, event queues, and transaction queues in order to update the current state with
	 * consensus ordered transactions without requiring any one instance of the state to handle any transactions more
	 * than once.
	 */
	private class Shuffler {

		/**
		 * do a shuffle, where stateCurr is discarded and replaced with stateWork, and stateWork is replaced
		 * with a copy of stateCons, and transactionPool shuffles accordingly. This is called by the shuffleBarrier
		 * while all 3 threads are waiting: thread-curr, thread-work, thread-cons
		 */
		public void shuffle() {
			final long startShuffle = System.nanoTime();

			// Handle all transactions in transactionPool for stateWork
			handleWorkTransactions();

			// Handle all transactions in all events in forWork
			drainForWorkQueue();

			// both queues feeding stateWork are now empty, and will stay empty (except for new transactions
			// that won't hurt us because they will never get a chance to be handled by the old stateCurr, and
			// they'll go into transactionPool queues for both forCurr and forWork). All 3 threads are waiting now,
			// so they won't interfere.
			try {
				// freeze stateCurr, then discard it so that it will be garbage collected
				stateCurr.setFrozen(true);
				stateCurr.getState().getSwirldState().noMoreTransactions();
			} catch (final Exception e) {
				// defensive: catch exceptions from a bad app
				LOG.error(EXCEPTION.getMarker(), "exception in app during noMoreTransactions:", e);
			}

			// this method will delete stateCurr if needed and replace it with stateWork.
			deleteCurrStateAndReplaceWithWork();

			stateWork = stateCons.copy();

			transactionPool.shuffle(); // move and copy the lists of transactions, too
			LOG.debug(QUEUES.getMarker(), "SHUFFLE stateWork:{}", stateWork.getState().getSwirldState());

			if (!threadWork.isEmpty()) {
				LOG.error(STARTUP.getMarker(), "threadWork is not empty after draining!");
			}

			// threadWork's queue is empty, so simply add the forNext events, then clear forNext. There doesn't seem to
			// be a performance difference between this implementation and swapping pointers to queues.
			threadWork.addAll(forNext);
			forNext.clear();

			lastShuffle = Instant.now(); // don't shuffle again, for a while

			stats.shuffleMicros((System.nanoTime() - startShuffle) * NANOSECONDS_TO_MICROSECONDS);
		}

		private void drainForWorkQueue() {
			while (!threadWork.isEmpty()) {
				// Do not allow a shuffle because we are already shuffling
				forWorkEvent(threadWork.poll());
			}
		}

		/**
		 * <p>Deletes stateCurr if it is not the last one returned by
		 * {@link SwirldStateManagerSingle#getCurrentSwirldState()}. If it is, then it will not delete now and that
		 * state will be deleted on the next {@link SwirldStateManagerSingle#releaseCurrentSwirldState()} invocation.
		 * Also replaces stateCurr with stateWork.</p>
		 *
		 * <p>Synchronize on the outer class because that is where threads not involved in the shuffle
		 * could access stateCurr by calling {@link SwirldStateManagerSingle#getCurrentSwirldState()}.</p>
		 */
		private void deleteCurrStateAndReplaceWithWork() {
			synchronized (SwirldStateManagerSingle.class) {
				if (stateCurrReturned != stateCurr.getState()) {
					// if they are not the same, we can delete this one
					try {
						stateCurr.release();
					} catch (final Exception e) {
						// defensive: catch exceptions from a bad app
						LOG.error(EXCEPTION.getMarker(), "exception in app during release of state:", e);
					}
				}

				stateCurr = stateWork;
			}
		}
	}
}
