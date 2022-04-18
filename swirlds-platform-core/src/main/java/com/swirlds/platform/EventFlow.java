/*
 * (c) 2016-2022 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */
package com.swirlds.platform;

import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.SwirldState;
import com.swirlds.common.SwirldState.SwirldState2;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.threading.StoppableThread;
import com.swirlds.common.threading.StoppableThreadConfiguration;
import com.swirlds.platform.components.SystemTransactionHandler;
import com.swirlds.platform.eventhandling.SignedStateEventsAndGenerations;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.StateInfo;
import com.swirlds.platform.stats.EventFlowStats;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.swirlds.common.Units.NANOSECONDS_TO_MICROSECONDS;
import static com.swirlds.common.Units.NANOSECONDS_TO_MILLISECONDS;
import static com.swirlds.common.Units.NANOSECONDS_TO_SECONDS;
import static com.swirlds.logging.LogMarker.EVENT_CONTENT;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.QUEUES;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.SIGNED_STATE;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT;
import static com.swirlds.platform.Settings.minTransTimestampIncrNanos;
import static com.swirlds.platform.event.EventUtils.toShortString;

/**
 * An EventFlow object is created by a Platform to manage its 3 SwirldState objects, the 7 queues that hold
 * the Events flowing between them, and the 4 threads that cause those events to flow between the states.
 */
public class EventFlow extends AbstractEventFlow {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/** how many of {threadCons, threadCurr, threadWork} exist in SwirldState2 */
	private static final int THREAD_NUM_SWIRLD_STATE_TWO = 2;

	/** how many of {threadCons, threadCurr, threadWork} exist in non-SwirldState2 */
	private static final int THREAD_NUM_SWIRLD_STATE = 3;

	private static final String COMPONENT_NAME = "event-flow";

	/** used to make threadCons, threadCurr, threadWork all wait during a shuffle */
	private final CyclicBarrier shuffleBarrier;

	/** lists of transactions by self (both system and non-system). See docs for TransLists. */
	private final TransLists transLists;

	/**
	 * a RunningHash object which calculates running hash of all consensus events so far
	 * with their transactions handled by stateCons
	 */
	private RunningHash eventsConsRunningHash = new RunningHash(
			new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]));

	/** number of events that have had their transactions handled by stateCons so far. */
	private final AtomicLong numEventsCons = new AtomicLong(0);

	/** current (most recent) state */
	private volatile StateInfo stateCurr;
	/** reflects all known consensus events */
	private volatile StateInfo stateCons;
	/** working state that will replace stateCurr once it catches up */
	private volatile StateInfo stateWork;

	/**
	 * Keeps the state returned to the user app to ensure that it doesn't get deleted.
	 * Must only be accessed in synchronized blocks.
	 */
	private State stateCurrReturned = null;
	/** a Semaphore that ensures that only one stateCurr will be passed to the user app at a time */
	private final Semaphore getStateSemaphore = new Semaphore(1);

	// Other classes SHOULD NOT ACCESS forCurr. The one exception is that
	// TransLists should access forCurr, but only to add TransLists.NoEvent to unblock it when the
	// queue is empty and transactions are waiting to be handled).

	/** Events to send to stateCurr. */
	private final BlockingQueue<EventImpl> forCurr;
	/** consensus events to send to stateCons */
	private final BlockingQueue<EventImpl> forCons;
	/** events to send to stateWork */
	private volatile BlockingQueue<EventImpl> forWork;
	/** queue to swap with forWork when shuffling */
	private volatile BlockingQueue<EventImpl> forNext;
	/** forCurr --> stateCurr */
	private StoppableThread threadCurr;
	/** forCons --> stateCons */
	private StoppableThread threadCons;
	/** forWork --> stateWork */
	private StoppableThread threadWork;

	/** when the last shuffle happened */
	private volatile Instant lastShuffle;

	/** does the app's state inherit from SwirldState2? */
	private final boolean swirldState2;

	/**
	 * indicates whether a state was saved in the current freeze period. we are only saving the first state
	 * in the freeze period. this variable is only used by threadCons so there is no synchronization needed
	 */
	private boolean savedStateInFreeze = false;

	/** Stores consensus events and round generations that need to be saved in state */
	private final SignedStateEventsAndGenerations eventsAndGenerations;

	/** how many of {threadCons, threadCurr, threadWork} exist (either 2 or 3) */
	private final int numThreads;

	/**
	 * This node's id.
	 */
	private final NodeId selfId;

	/**
	 * Statistics required by and updated by {@link EventFlow}.
	 */
	private final EventFlowStats stats;

	/**
	 * Stores consensus events in the event stream.
	 */
	private final EventStreamManager<EventImpl> eventStreamManager;

	/**
	 * Indicates if this node is a zero staked node.
	 */
	private final boolean isZeroStakedNode;

	/**
	 * Handles all system transactions.
	 */
	private final SystemTransactionHandler systemTransactionHandler;

	/**
	 * The address book of every known member in the swirld.
	 */
	private final AddressBook addressBook;

	/**
	 * A supplier of an estimated consensus time.
	 */
	private final Supplier<Instant> consEstimateSupplier;

	/**
	 * A provider of settings required by {@link EventFlow}
	 */
	private final SettingsProvider settings;

	/** A queue that accepts signed states for hashing and signature collection. */
	private final BlockingQueue<SignedState> stateHashSignQueue;

	/**
	 * is used for unit testing.
	 * this 0-arg constructor is required to create a mock instance
	 */
	EventFlow() {
		shuffleBarrier = null;
		transLists = null;
		forCurr = null;
		forCons = null;
		swirldState2 = true;
		numThreads = THREAD_NUM_SWIRLD_STATE_TWO;
		eventsAndGenerations = null;
		selfId = null;
		stats = null;
		eventStreamManager = null;
		isZeroStakedNode = false;
		systemTransactionHandler = null;
		addressBook = null;
		consEstimateSupplier = null;
		settings = null;
		stateHashSignQueue = null;
	}

	/**
	 * Instantiate, but don't start any threads yet. The Platform should first instantiate the EventFlow,
	 * which creates the 3 states and 6 queues and 3 threads. Then the Platform should call startAll to
	 * start the 3 threads.
	 *
	 * @param selfId
	 * 		the id of this node
	 * @param stats
	 * 		statistics updated by {@link EventFlow}
	 * @param addressBook
	 * 		the address book for the network
	 * @param consEstimateSupplier
	 * 		a supplier for the estimated consensus time of a transaction created at the time of invocation
	 * @param eventStreamManager
	 * 		the event stream manager to send consensus events to
	 * @param isZeroStakedNode
	 * 		true if this node is a zero-stake node
	 * @param systemTransactionHandler
	 * 		the handler for all system transactions
	 * @param initialState
	 * 		the SwirldState that should be used (along with 2 fast copies of it) to initialize all 3
	 * 		of the states stored here. This initial state will be used by EventFlow internally, so it
	 * 		should not be used after being passed to it
	 */
	public EventFlow(
			final NodeId selfId,
			final EventFlowStats stats,
			final AddressBook addressBook,
			final EventStreamManager<EventImpl> eventStreamManager,
			final boolean isZeroStakedNode,
			final SystemTransactionHandler systemTransactionHandler,
			final BlockingQueue<SignedState> stateHashSignQueue,
			final Supplier<Instant> consEstimateSupplier,
			final SettingsProvider settings,
			final State initialState) {

		this.selfId = selfId;
		this.stats = stats;
		this.eventStreamManager = eventStreamManager;
		this.isZeroStakedNode = isZeroStakedNode;
		this.systemTransactionHandler = systemTransactionHandler;
		this.addressBook = addressBook;
		this.consEstimateSupplier = consEstimateSupplier;
		this.settings = settings;
		this.swirldState2 = (initialState.getSwirldState() instanceof SwirldState2);
		this.stateHashSignQueue = stateHashSignQueue;

		this.transLists = new TransLists(this);
		lastShuffle = Instant.now(); // wait a while before first shuffle, so maybe queues aren't empty

		this.setState(initialState);

		Comparator<EventImpl> cmp = (x, y) -> {
			if (x == null || y == null || x.getConsensusTimestamp() == null
					|| y.getConsensusTimestamp() == null) {
				return 0;
			}
			return x.getConsensusTimestamp()
					.compareTo(y.getConsensusTimestamp());
		};

		forCurr = new PriorityBlockingQueue<>(100, cmp);
		forCons = settings.getMaxEventQueueForCons() == 0
				? new LinkedBlockingQueue<>()
				: new ArrayBlockingQueue<>(settings.getMaxEventQueueForCons());

		if (!swirldState2) {
			forWork = new PriorityBlockingQueue<>(100, cmp);
			forNext = new PriorityBlockingQueue<>(100, cmp);
		}

		if (!swirldState2) {
			// in this case, there would be one thread more than swirldState2 for running doWork
			numThreads = THREAD_NUM_SWIRLD_STATE;
		} else {
			numThreads = THREAD_NUM_SWIRLD_STATE_TWO;
		}
		shuffleBarrier = new CyclicBarrier(numThreads, () -> {
			try {
				shuffle();
			} catch (Exception e) {
				log.error(EXCEPTION.getMarker(), "shuffle exception {}",
						() -> e + Arrays.toString(e.getStackTrace()));
			}
		});

		eventsAndGenerations = new SignedStateEventsAndGenerations(Settings.state);
	}

	/** is the app's state extending SwirldState2 (as opposed to SwirldState)? */
	boolean isSwirldState2() {
		return swirldState2;
	}

	/**
	 * Return the current state of the app. It changes frequently, so this needs to be called frequently. This method
	 * also keeps track of which state was last returned, so that it can guarantee that that state will not be deleted.
	 *
	 * @return the current app state
	 */
	public SwirldState getCurrStateAndKeep() {
		if (swirldState2) {
			return stateCurr.getState().getSwirldState();
		}

		try {
			getStateSemaphore.acquire();
		} catch (InterruptedException e1) {
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
	 * Releases the state that was previously returned, so that another one can be obtained from getCurrStateAndKeep(),
	 * also deletes it if it's not the current state being used
	 */
	public synchronized void releaseStateCurr() {
		if (swirldState2) {
			return;
		}

		getStateSemaphore.release();
		if (stateCurrReturned != null && (stateCurr == null || stateCurrReturned != stateCurr.getState())) {
			// if the one returned is not the current one, we can delete it
			try {
				stateCurrReturned.release();
			} catch (Exception e) {
				// defensive: catch exceptions from a bad app
				log.error(EXCEPTION.getMarker(), "exception in app during delete:", e);
			}
		}
		stateCurrReturned = null;
	}

	/**
	 * Deletes the stateCurr if it is not the last one returned by getCurrStateAndKeep(). If it is, then it will not
	 * delete now and that state will be deleted on the next releaseStateCurr() invocation. Also replaces stateCurr
	 * with stateWork
	 */
	private synchronized void deleteCurrStateAndReplaceWithWork() {
		if (stateCurrReturned != stateCurr.getState()) {
			// if they are not the same, we can delete this one
			try {
				stateCurr.release();
			} catch (Exception e) {
				// defensive: catch exceptions from a bad app
				log.error(EXCEPTION.getMarker(), "exception in app during release of state:", e);
			}
		}

		stateCurr = stateWork;
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
	public int getForConsSize() {
		return forCons.size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TransLists getTransLists() {
		return transLists;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	int getSignedStateEventsSize() {
		return eventsAndGenerations.getNumberOfEvents();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getForCurrSize() {
		return forCurr.size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	int getStateToHashSignSize() {
		return stateHashSignQueue.size();
	}

	@Override
	public void preConsensusEvent(EventImpl event) {
		// Expand signatures for events received from sync operations
		// Additionally, we should enqueue any signatures for verification
		// Signature expansion should be the last thing that is done. It can be fairly time consuming, so we don't
		// want to delay the verification of an event for it because other events depend on this one being valid.
		// Furthermore, we don't want to validate signatures contained in an event that is invalid.
		expandSignatures(event.getTransactions());
		forCurrPut(event);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void forCurrPut(EventImpl event) {
		if (event.isEmpty() || isCreatedBySelf(event)) {
			// stateCurr / stateWork don't need the empty events.
			// and they don't need events by self (since those transactions are in transLists)
			return;
		}
		try {
			event.estimateTime(selfId, stats.getAvgSelfCreatedTimestamp(),
					stats.getAvgOtherReceivedTimestamp());
			// update the estimate now, so the queue can sort on it
			forCurr.put(event);
		} catch (InterruptedException e) {
			log.error(EXCEPTION.getMarker(), "error:{} event:{}", e, event);
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * check whether this event is created by current node
	 */
	private boolean isCreatedBySelf(final EventImpl event) {
		return selfId.equalsMain(event.getCreatorId());
	}

	/**
	 * if forCurr is empty, then put noEvent to unblock threadCurr, otherwise don't (the check and put are
	 * not atomic together, but that's ok), and also do the same for forWork.
	 **/
	void unblockCurrWork() {
		if (forCurr.peek() == null) { // unblock threadCurr, if it's blocked
			forCurr.offer(transLists.noEvent);// this unblocks it (then noEvent is later ignored)
		}
		if (forWork != null && forWork.peek() == null) { // unblock threadCurr, if it's blocked
			forWork.offer(transLists.noEvent);// this unblocks it (then noEvent is later ignored)
		}
	}

	/**
	 * Add the given event (which just became consensus) to the forCons queue, to later be sent to
	 * stateCons.
	 * <p>
	 * When Hashgraph first finds consensus for an event, it puts the event into {@link EventStreamManager}'s queue,
	 * which will then call this method. If the queue is full, then this will block until it isn't full,
	 * which will block whatever thread called Hashgraph.consRecordEvent.
	 * <p>
	 * Thread interruptions are ignored.
	 *
	 * @param event
	 * 		the new event to record
	 */
	@Override
	public void consensusEvent(EventImpl event) {
		try {
			// adds this consensus event to eventStreamHelper,
			// which will put it into a queue for calculating runningHash, and a queue for event streaming when enabled
			eventStreamManager.addEvent(event);
			// this may block until the queue isn't full
			forCons.put(event);
		} catch (InterruptedException e) {
			log.error(RECONNECT.getMarker(), "forConsPut interrupted");
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Stop the provided threads, and block until they have all stopped.
	 *
	 * @param threadsToStop
	 * 		an array of threads to stop. Must not be null.
	 */
	private static void stopThreads(final StoppableThread... threadsToStop) throws InterruptedException {
		for (final StoppableThread thread : threadsToStop) {
			if (thread != null) {
				log.info(RECONNECT.getMarker(), "stopping thread {}", thread.getName());
				thread.stop();
			}
		}

		for (final StoppableThread thread : threadsToStop) {
			if (thread != null) {
				log.info(RECONNECT.getMarker(), "joining thread {}", thread.getName());
				thread.join();
			}
		}
		log.info(RECONNECT.getMarker(), "threads are now terminated");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	synchronized void stopAndClear() throws InterruptedException {
		// stopAndClear() is currently only used to reconnect. when reconnecting, we need the consensus state to do
		// the reconnect, that's why it will not be cleared.
		stopThreads(threadCons, threadCurr, threadWork);

		log.info(RECONNECT.getMarker(), "stopAndClear: releasing states");

		// delete the states
		try {
			if (!swirldState2) {
				if (stateWork != null) {
					stateWork.getState().getSwirldState().noMoreTransactions();
					stateWork.getState().release();
				}
				if (stateCurr != null) {
					stateCurr.getState().getSwirldState().noMoreTransactions();
					stateCurr.getState().release();
				}
			}

		} catch (Exception e) {
			// defensive: catch exceptions from a bad app
			log.error(EXCEPTION.getMarker(), "exception in SwirldState:", e);
		}

		stateWork = null;


		// clear all the queues
		log.info(RECONNECT.getMarker(), "stopAndClear: clearing stateHashSignQueueThread");
		clearStateHashSignQueueThread();

		log.info(RECONNECT.getMarker(), "stopAndClear: clearing forCurr");
		forCurr.clear();

		log.info(RECONNECT.getMarker(), "stopAndClear: clearing forCons");
		forCons.clear();

		if (!swirldState2) {
			log.info(RECONNECT.getMarker(), "stopAndClear: clearing forWork");
			forWork.clear();

			log.info(RECONNECT.getMarker(), "stopAndClear: clearing forNext");
			forNext.clear();
		}

		// clear the transactions
		log.info(RECONNECT.getMarker(), "stopAndClear: clearing transLists");
		transLists.clear();

		// clear running Hash info
		eventsConsRunningHash = new RunningHash(
				new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]));
		numEventsCons.set(0);

		// used by doCons
		eventsAndGenerations.clear();

		log.info(RECONNECT.getMarker(), "stopAndClear: event flow is now cleared");
	}

	/**
	 * Clears and releases any signed states in the {@code stateHashSignQueueThread} queue.
	 */
	private void clearStateHashSignQueueThread() {
		SignedState signedState = stateHashSignQueue.poll();
		while (signedState != null) {
			signedState.release();
			signedState = stateHashSignQueue.poll();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void loadDataFromSignedState(SignedState signedState, boolean isReconnect) {
		eventsAndGenerations.loadDataFromSignedState(signedState);
		// nodes not reconnecting expired eventsAndGenerations right after creating a signed state
		// we expire here right after receiving it to align ourselves with other nodes for the next round
		eventsAndGenerations.expire();

		// set initialHash of the RunningHash to be the hash loaded from signed state
		eventsConsRunningHash = new RunningHash(signedState.getHashEventsCons());

		numEventsCons.set(signedState.getNumEventsCons());


		log.info(STARTUP.getMarker(), " doConsMinGenFamous after startup {}",
				Arrays.toString(signedState.getMinGenInfo().toArray()));

		// get startRunningHash from signedState
		Hash initialHash = new Hash(signedState.getHashEventsCons());
		eventStreamManager.setInitialHash(initialHash);

		log.info(STARTUP.getMarker(), " initialHash after startup {}", () -> initialHash);
		eventStreamManager.setStartWriteAtCompleteWindow(isReconnect);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void setState(State state) {
		state.throwIfReleased("state must not be released");
		state.throwIfImmutable("state must be mutable");
		if (stateCons != null) {
			stateCons.release();
		}
		stateCons = new StateInfo(state, null, false);
		if (swirldState2) {
			stateCurr = stateCons; // in this mode there's only one state
		} else {
			// this mode has 3 different states
			if (stateCurr != null) {
				stateCurr.release();
			}
			stateCurr = new StateInfo(state.copy(), null, false);
			if (stateWork != null) {
				stateWork.release();
			}
			stateWork = new StateInfo(state.copy(), null, false);
		}
	}

	/**
	 * remove the list of transactions from the transLists, so they can be put into a new Event
	 *
	 * @return
	 */
	@Override
	public Transaction[] getTransactions() {
		return transLists.pollTransForEvent();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int numUserTransForEvent() {
		return transLists.numUserTransForEvent();
	}

	@Override
	public int numFreezeTransEvent() {
		return transLists.numFreezeTransEvent();
	}

	/**
	 * Store a new transaction to be included in a future Event, that will be created during the next sync.
	 * <p>
	 * This is called by the app's thread (SwirldMain.run starts app which calls
	 * Platform.createTransaction).
	 * <p>
	 * It is also called by the Platform's thread (which calls SwirldMain.preEvent, which calls
	 * Platform.createTransaction).
	 * <p>
	 * It is also called by SignedStateMgr.newSelfSigned() to create the system transaction in which self
	 * signs a new signed state.
	 * <p>
	 * Thread interruptions are ignored.
	 * <p>
	 * If transaction==null this call does nothing, and returns false.
	 * <p>
	 * If this is not a system transaction, and the transaction queues are full, then this will do nothing,
	 * and will return false. Also, if it is larger than Settings.transactionMaxBytes it will do nothing and
	 * return false.
	 *
	 * @param transaction
	 * 		the new transaction being created locally (optionally passed as several parts to
	 * 		concatenate)
	 * @return true if successful
	 */
	public boolean createTransaction(final Transaction transaction) {
		// Refuse to create any type of transaction if the beta mirror is enabled and this node has zero stake
		if (settings.isEnableBetaMirror() && isZeroStakedNode) {
			return false;
		}

		if (transaction == null) {
			return false;
		}

		// check if system transaction serialized size is above the required threshold
		if (transaction.getSize() > settings.getTransactionMaxBytes()) {
			return false;
		}

		return transLists.offer(transaction, transaction.isSystem());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void startAll() {
		threadCons = new StoppableThreadConfiguration()
				.setNodeId(selfId.getId())
				.setComponent(COMPONENT_NAME)
				.setThreadName("thread-cons")
				.setWork(this::doCons)
				.build();
		threadCurr = new StoppableThreadConfiguration()
				.setNodeId(selfId.getId())
				.setComponent(COMPONENT_NAME)
				.setThreadName("thread-curr")
				.setWork(this::doCurr)
				.build();

		if (!swirldState2) {
			threadWork = new StoppableThreadConfiguration()
					.setNodeId(selfId.getId())
					.setComponent(COMPONENT_NAME)
					.setThreadName("thread-work")
					.setWork(this::doWork)
					.build();
		}

		lastShuffle = Instant.now();
		threadCons.start();
		threadCurr.start();

		if (!swirldState2) {
			threadWork.start();
		}

		log.info(STARTUP.getMarker(), "EventFlow.startAll(), " +
						"forCons.size: {}, forCurr.size: {}, forNext.size: {}, forSigs.size: {}, forWork.size: {}",
				forCons == null ? null : forCons.size(),
				forCurr == null ? null : forCurr.size(),
				forNext == null ? null : forNext.size(),
				eventsAndGenerations.getNumberOfEvents(),
				forWork == null ? null : forWork.size());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	BlockingQueue<EventImpl> getForCurr() {
		return forCurr;
	}

	//////////////////////////////////////////////////////////
	// below here are all the private methods, including the 4 called repeatedly by the 4 threads here

	/**
	 * Handle one transaction by sending it to the SwirldState object (if it's not a system transaction) or
	 * to the platform (if it is). This is the only place that the SwirldState.handleTransaction is ever
	 * called. And the only place that Platform.handleSystemTransaction is ever called.
	 *
	 * The app will be told the consensus timestamp for the event, or an estimate of it. In both cases, the
	 * timestamp will be baseTime plus timeInc nanoseconds. This is used to ensure that each transaction in
	 * consensus order is at least one nanosecond later than previous one.
	 *
	 * @param selfId
	 * @param stats
	 * @param systemTransactionHandler
	 * @param ignoreSystem
	 * 		should we ignore system transactions and not handle them?
	 * @param isSystem
	 * 		is this a system transaction?
	 * @param state
	 * 		the SwirldState object to send it to (ignored if system is true)
	 * @param isConsensus
	 * 		should the state be told that this transaction is part of the consensus?
	 * @param event
	 * 		the event containing this transaction, or null if it is a self-transaction without an
	 * 		event
	 * @param trans
	 * 		the single transaction to handle (which will be one element of event.transactions).
	 * @param baseTime
	 * 		timestamp (consensus or estimated) of the event, before adding timeInc.
	 * @param timeInc
	 * 		number of nanoseconds to add to baseTime, before passing it to the user as the estimated
	 * 		or actual timestamp
	 */
	private static void handleTransaction(final NodeId selfId, final EventFlowStats stats,
			final SystemTransactionHandler systemTransactionHandler,
			boolean ignoreSystem,
			boolean isSystem, StateInfo state, boolean isConsensus, EventImpl event,
			Transaction trans, Instant baseTime, long timeInc) {
		if (ignoreSystem && isSystem) { // don't handle system transactions if they should be ignored
			return;
		}

		if (state.isFrozen()) { // don't send a state any transactions after we promised not to.
			log.error(
					"ERROR: EventFlow.handleTransaction was given a transaction for a frozen state");
			return;
		}

		// let the state handle this transaction
		try {// guard against bad apps crashing the browser
			/** the creator of the event containing this transaction */
			long creator = event == null ? selfId.getId() // selfId assumed to be main
					: event.getCreatorId();

			/** The claimed creation time of the event holding this transaction (or now, if no event) */
			Instant timeCreated = (event == null ? Instant.now()
					: event.getTimeCreated());

			/**
			 * Estimate of what will be this transaction's consensus timestamp. The first transaction in an
			 * event gets the event's timestamp, and then each successive transaction in that event has a
			 * timestamp {@link Settings#minTransTimestampIncrNanos} nanosecond later. If the event already has
			 * consensus, the estimate is exact.
			 */
			Instant estConsTime = baseTime.plusNanos(timeInc);

			if (isSystem) {// send system transactions to Platform (they come from doCons)
				systemTransactionHandler.handleSystemTransaction(creator, isConsensus,
						timeCreated, estConsTime, trans);
				if (event != null && event.getReachedConsTimestamp() != null) {
					// we only add this stat for transactions that have reached consensus
					stats.consensusToHandleTime(
							event.getReachedConsTimestamp().until(Instant.now(),
									ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
				}
			} else { // send user transactions to SwirldState
				SwirldTransaction swirldTransaction = (SwirldTransaction) trans;
				// If we have consensus, validate any signatures present and wait if necessary
				if (isConsensus) {
					for (TransactionSignature sig : swirldTransaction.getSignatures()) {
						Future<Void> future = sig.waitForFuture();

						// Block & Ignore the Void return
						future.get();
					}
				}
				long startTime = System.nanoTime();

				state.getState().getSwirldState().handleTransaction(creator, isConsensus, timeCreated,
						estConsTime, swirldTransaction, state.getState().getSwirldDualState());

				// clear sigs to free up memory, since we don't need them anymore
				if (isConsensus) {
					swirldTransaction.clearSignatures();
				}

				// we only add these stats for transactions that have reached consensus
				if (event != null && event.getReachedConsTimestamp() != null) {
					stats.consensusTransHandleTime((System.nanoTime() - startTime) * NANOSECONDS_TO_SECONDS);
					stats.consensusTransHandled();
					stats.consensusToHandleTime(
							event.getReachedConsTimestamp().until(Instant.now(),
									ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
				}
			}
		} catch (InterruptedException ex) {
			log.info(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
					"EventFlow::handleTransaction Interrupted [ nodeId = {} ]. " +
							"This should happen only during a reconnect",
					selfId.getId());
			Thread.currentThread().interrupt();
		} catch (Exception ex) {
			log.error(EXCEPTION.getMarker(),
					"error while calculating parameters to send {} or while calling it with event {}",
					((state == null || state.getState() == null)
							? "platform.handleTransaction"
							: "the app's SwirldState.handleTransaction"),
					toShortString(event), ex);

			log.error(EVENT_CONTENT.getMarker(),
					"error calculating parameters while calling it using a context \nwith event: {}\nwith trans: {}",
					() -> event, trans::toString);
		}
	}

	@Override
	public void addMinGenInfo(long round, long minGeneration) {
		eventsAndGenerations.addRoundGeneration(round, minGeneration);
	}

	/**
	 * doCurr is repeatedly called by the threadCurr thread. Each time, it removes one event from the
	 * forCurr queue, adds it to the forWork queue, and feeds its transactions to the stateCurr object
	 * (which is a SwirldState representing the effect of all known transactions so far).
	 */
	private void doCurr() {
		takeHandlePut(true, forCurr, stateCurr, forWork);
	}

	/**
	 * doWork is repeatedly called by the threadWork thread. Each time, it removes one event from the
	 * forWork queue, adds it to the forNext queue, and feeds its transactions to the stateWork object
	 * (which is a SwirldState that started as a recent copy of stateCons, and is now being fed all the
	 * transactions that weren't already sent to stateCons before the copy was made).
	 */
	private void doWork() {
		takeHandlePut(true, forWork, stateWork, forNext);
	}

	/**
	 * doCons is repeatedly called by the threadCons thread. Each time, it removes one event from the
	 * forCons queue, adds it to the forSigs queue, and feeds its transactions to the stateCons object
	 * (which is a SwirldState representing the effect of all consensus transactions so far). If that event
	 * happens to be the last in the set of events with that received round, then this also creates the
	 * signed state for it (if Settings.signedStateFreq > 0 and this is a round for which is should be
	 * done).
	 *
	 * @throws InterruptedException
	 */
	private void doCons() throws InterruptedException {
		EventImpl event;

		final long startTakeHandle = System.nanoTime();
		event = takeHandlePut(true, forCons, stateCons, null);

		if (event == null) {
			return;
		}

		stats.consShuffleMicros((System.nanoTime() - startTakeHandle) * NANOSECONDS_TO_MICROSECONDS);

		final long startSignedState = System.nanoTime();

		if (!event.isConsensus()) {
			log.error(EXCEPTION.getMarker(), "EventFlow forCons had nonconensus event {}", event);
		}

		eventsAndGenerations.addEvent(event);

		// count events that have had all their transactions handled by stateCons
		numEventsCons.incrementAndGet();

		final long startEventHash = System.nanoTime();

		if (event.getHash() == null) {
			CryptoFactory.getInstance().digestSync(event);
		}
		// update the running hash object
		eventsConsRunningHash = event.getRunningHash();

		stats.consEventHashMicros((System.nanoTime() - startEventHash) * NANOSECONDS_TO_MICROSECONDS);

		if (event.isLastOneBeforeShutdown() || // if eventstream is shuting down
				event.isLastInRoundReceived()  // if we have a new shared state to sign
						&& settings.getSignedStateKeep() > 0 // we are keeping states
						&& settings.getSignedStateFreq() > 0 // and we are signing states
						&& (event.getRoundReceived() == 1 // the first round should be signed
						// and every Nth should be signed, where N is signedStateFreq
						|| event.getRoundReceived() % settings.getSignedStateFreq() == 0)) {

			// the consensus timestamp for the signed state should be the timestamp of the last transaction
			// in the last event. if the last event has no transactions, then it will be the timestamp of
			// the event
			Instant ssConsTime = event.getLastTransTime();

			boolean signThisState = true;

			if (isInFreezePeriod(ssConsTime)) {
				if (!savedStateInFreeze) {
					// we are saving the first state in the freeze period
					signThisState = true;
					savedStateInFreeze = true;
					synchronized (stateCons) {
						// set current DualState's lastFrozenTime to be current freezeTime
						stateCons.getState().getPlatformDualState().setLastFrozenTimeToBeCurrentFreezeTime();
					}
				} else {
					signThisState = false;
				}
			} else {
				// once the freeze period has ended, this variable is reset
				savedStateInFreeze = false;
			}

			if (signThisState) {
				// create a new signed state, sign it, and send out a new transaction with the signature
				// the signed state keeps a copy that never changes.
				final long startCopy = System.nanoTime();
				final State immutableStateCons;

				synchronized (stateCons) {
					immutableStateCons = stateCons.getState();
					// we increment the refCount so the state doesn't get deleted until we put it into a signed state
					immutableStateCons.incrementReferenceCount();
					stateCons.setState(stateCons.getState().copy());
				}

				stats.stateCopyMicros((System.nanoTime() - startCopy) * NANOSECONDS_TO_MICROSECONDS);

				// this state will be saved, so there will be no more transactions sent to it
				try {
					immutableStateCons.getSwirldState().noMoreTransactions();
				} catch (Exception e) {
					// defensive: catch exceptions from a bad app
					log.error(EXCEPTION.getMarker(), "exception in app during noMoreTransactions:", e);
				}

				EventImpl[] events = eventsAndGenerations.getEventsForSignedState();

				log.info(SIGNED_STATE.getMarker(), "finished adding events, about to create a minGen list");
				// create a minGen list with only the rounds up until this round received
				final List<Pair<Long, Long>> minGen = eventsAndGenerations.getMinGenForSignedState();

				// The doCons thread will not wait for a state to be signed, it will put it into this queue to be done
				// in the background. If the hashing cannot be done before the next state needs to be signed, doCons
				// will block and wait.
				log.info(SIGNED_STATE.getMarker(),
						"about to put a NewSignedStateInfo to stateToHashSign for round:{} , event: {}",
						event.getRoundReceived(),
						toShortString(event));

				final long startRunningHash = System.nanoTime();

				final Hash runningHash = eventsConsRunningHash.getFutureHash().get();

				stats.consRunningHashMicros((System.nanoTime() - startRunningHash) * NANOSECONDS_TO_MICROSECONDS);

				final SignedState signedState = new SignedState(
						immutableStateCons,
						event.getRoundReceived(),
						numEventsCons.get(),
						runningHash,
						addressBook.copy(),
						events,
						ssConsTime,
						savedStateInFreeze,
						minGen
				);

				final long startStateHashAdmit = System.nanoTime();

				stateHashSignQueue.put(signedState);

				stats.consStateSignAdmitMicros((System.nanoTime() - startStateHashAdmit) * NANOSECONDS_TO_MICROSECONDS);


				immutableStateCons.decrementReferenceCount();
			}
		}

		stats.consBuildStateMicros((System.nanoTime() - startSignedState) * NANOSECONDS_TO_MICROSECONDS);

		if (event.isLastInRoundReceived()) {
			final long startForSigClean = System.nanoTime();
			// the next part removes events and generations that are not needed
			eventsAndGenerations.expire();
			stats.consForSigCleanMicros((System.nanoTime() - startForSigClean) * NANOSECONDS_TO_MICROSECONDS);
		}
	}


	/**
	 * do a shuffle, where stateCurr is discarded and replaced with stateWork, and stateWork is replaced
	 * with a copy of stateCons, and transLists shuffles accordingly. This is called by the shuffleBarrier
	 * while all 3 threads are waiting: threadCons, threadCurr, threadWork
	 */
	private void shuffle() {
		// Handle all transactions in transLists for stateWork
		takeHandlePut(false, null, stateWork, null);
		// Handle all transactions in all events in forWork
		while (true) { // handle all in forWork
			EventImpl event = takeHandlePut(false, forWork, stateWork,
					forNext);
			if (event == null) {
				break;
			}
		}
		// both queues feeding stateWork are now empty, and will stay empty (except for new transactions
		// that won't hurt us because they will never get a chance to be handled by the old stateCurr, and
		// they'll go into transLists queues for both forCurr and forWork). All 3 threads are waiting now,
		// so they won't interfere.
		try {
			// freeze stateCurr, then discard it so it will be garbage collected
			stateCurr.setFrozen(true);
			stateCurr.getState().getSwirldState().noMoreTransactions();
		} catch (Exception e) {
			// defensive: catch exceptions from a bad app
			log.error(EXCEPTION.getMarker(), "exception in app during noMoreTransactions:", e);
		}

		// this method will delete stateCurr if needed and replace it with stateWork. it needs to be synchronized
		// because of getCurrStateAndKeep
		deleteCurrStateAndReplaceWithWork();
		stateWork = stateCons.copy();
		transLists.shuffle(); // move and copy the lists of transactions, too
		log.debug(QUEUES.getMarker(), "SHUFFLE stateWork:{}",
				stateWork.getState().getSwirldState());
		BlockingQueue<EventImpl> t = forWork;
		forWork = forNext;
		forNext = t;
		lastShuffle = Instant.now(); // don't shuffle again, for a while
	}

	/**
	 * Take an event from fromQueue, then send all the transactions in it to
	 * stateInfo.state.handleTransaction(), then put the event into toQueue.
	 *
	 * If there are non-event transactions in transLists that need to be handled, they will be handled just
	 * before the event transactions. All of this is done within an appropriate synchronized block.
	 *
	 * The event is discarded rather than being passed on to the next queue, if it is null or noEvent or if
	 * the event was already handled by doCons for this state.
	 *
	 * This method is the only place that handleTransaction() is ever called.
	 *
	 * The take will wait until either the queue has an event in it, or until it is time for the next
	 * shuffle. When it is time for the next shuffle, a CyclicBarrier is used to make all 3 threads wait
	 * (threadCons, threadCurr, threadWork), and then a shuffle is done, and then all 3 threads are
	 * unblocked and allowed to continue.
	 *
	 * @param allowShuffle
	 * 		if true, and if it is time for a shuffle, then use shuffleBarrier to make all 3 threads
	 * 		wait, and call shuffle()
	 * @param fromQueue
	 * 		the queue that the event is taken from
	 * @param stateInfo
	 * 		the state that will handle the transactions
	 * @param toQueue
	 * 		the queue to put the event in after handling the transactions
	 * @return the event taken from fromQueue, or null if it was empty or timed out or had a noEvent
	 */
	EventImpl takeHandlePut(final boolean allowShuffle, final BlockingQueue<EventImpl> fromQueue,
			final StateInfo stateInfo, final BlockingQueue<EventImpl> toQueue) {
		Transaction[] transactions;
		Instant baseTime;
		EventImpl event = null;

		// time to wait until the next shuffle
		long wait = settings.getDelayShuffle()
				- lastShuffle.until(Instant.now(), ChronoUnit.MILLIS);

		if (stateInfo.getState().getSwirldState() instanceof SwirldState2) {
			// wait forever if we will never shuffle
			// (actually it wakes up then goes back to waiting, once every 15 seconds)
			wait = 15000;
		}
		// block until something is available, or until it's time for a new shuffle
		if (allowShuffle && wait <= 0) {
			// wait until all 3 threads are waiting, then call shuffle(), then allow all 3 to continue
			try {
				// if it ever breaks, it will eventually come here and be fixed
				if (shuffleBarrier.isBroken()) {
					shuffleBarrier.reset();
				}
				shuffleBarrier.await(); // wait for all 3 threads, shuffle, continue
			} catch (InterruptedException | BrokenBarrierException e) {
				log.error(EXCEPTION.getMarker(),
						"shuffleBarrier interrupted or broken for state {}",
						() -> stateInfo);
				Thread.currentThread().interrupt();
			}
			return null;
		}

		// get the next event which might be an Event, or noEvent, or null
		if (fromQueue != null) {
			try {
				event = fromQueue.poll(wait, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				event = null;
				log.info(RECONNECT.getMarker(),
						"takeHandlePut() fromQueue.poll() interrupted");
				Thread.currentThread().interrupt();
				return null;
			}
		}

		// handle transactions by self that aren't yet in an event, but are waiting to be handled
		// find the number of transactions outside the event to handle
		// stateCons doesn't handle transactions outside event
		int numTrans =
				(stateInfo == stateWork)
						? transLists.getWorkSize()
						: (stateInfo == stateCurr)
						? transLists.getCurrSize()
						: 0;

		// the timestamp that we estimate the transactions will have after
		// being put into an event and having consensus reached on them
		baseTime = consEstimateSupplier.get();

		for (int i = 0; i < numTrans; i++) {
			Transaction trans =
					(stateInfo == stateWork)
							? transLists.pollWork()
							: (stateInfo == stateCurr)
							? transLists.pollCurr()
							: null;
			if (trans == null) {
				// this shouldn't be necessary, but it's here just for safety
				break;
			}

			synchronized (stateInfo) {
				// handle this self-transaction that isn't yet in an event
				handleTransaction(
						selfId,
						stats,
						systemTransactionHandler,
						true, // ignoreSystem: yes, though there won't be any
						trans.isSystem(), // isSystem: always false (system transactions aren't in the queue)
						stateInfo, // state: contains the state that will handle the transaction
						false, // isConsensus: this isn't in any event, so isn't yet consensus
						null, // event: this transaction isn't in an event (so timeCreated will be now)
						trans, // trans: the transaction to handle
						baseTime, // baseTime: the timestamp (consensus or estimated) of the event
						i * minTransTimestampIncrNanos); // timeInc: number of nanoseconds to add to baseTime to get
				// consensus or est time
			}
		}

		// now all the transList transactions are handled, so handle the actual event transactions
		// Discard events that are null or that represent a non-event.
		if (event == null || event == transLists.noEvent) {
			return null;
		}

		// If we are handling consensus events, we should not handle any events after the freeze state, and before the
		// end of the freeze period
		if (fromQueue == forCons && savedStateInFreeze) {
			return event;
		}
		
		//remember isConsensus, in case a thread changes it during this
		boolean isConsensusEvent = event.getConsensusTimestamp() != null;

		if (isConsensusEvent) {
			baseTime = event.getConsensusTimestamp();
		} else {
			// estimate the timestamp that this event will have
			event.estimateTime(selfId, stats.getAvgSelfCreatedTimestamp(), stats.getAvgOtherReceivedTimestamp());
			baseTime = event.getEstimatedTime();
		}

		transactions = event.getTransactions();

		// Discard events with no transactions.
		if (transactions == null
				|| transactions.length == 0
				|| (!isSwirldState2() && isConsensusEvent
				&& stateInfo.getLastCons() != null
				&& event.getConsensusOrder() <= stateInfo.getLastCons()
				.getConsensusOrder())) {
			return event;
		}
		if (isConsensusEvent) {
			stateInfo.setLastCons(event);// not used for SwirldsState2
		}

		// for SwirldState2, we say events from states other than stateCons are
		// not consensus, even if they are, to keep the contract that every
		// transaction is handled twice, once marked as consensus and once not.
		boolean allowIsConsensusTrue = !(stateInfo.getState().getSwirldState() instanceof SwirldState2)
				|| (fromQueue == forCons);
		// if doCons just handled a self-transaction, then remove it from the
		// list of transactions that will be sent to the new stateWork to handle
		// after the next shuffle.
		boolean selfConsTrans = stateInfo == stateCons
				&& selfId.equalsMain(event.getCreatorId());
		synchronized (stateInfo) {
			for (int i = 0; i < transactions.length; i++) {
				boolean isConsensus = isConsensusEvent && allowIsConsensusTrue;

				// this is the only place where handleTransaction is called on system
				// transactions with ignoreSystem == false
				handleTransaction(//
						selfId,
						stats,
						systemTransactionHandler,
						fromQueue != forCons,// All system transactions are handled twice, once with consensus
						// false, and once with true. This is the second time a system
						// transaction is handled when its consensus is known. The
						// first time it was handled is in Hashgraph.addEvent3()
						transactions[i].isSystem(), // isSystem: is this a system transaction?
						stateInfo, //// state: contains the state that will handle the transaction
						isConsensus, // isConsensus: does this event have a consensus order?
						event, // event: the event containing the transaction (whose time gives timeCreated)
						transactions[i], // trans: the transaction to handle
						baseTime, // baseTime: the timestamp (consensus or estimated) of the event
						i * minTransTimestampIncrNanos); // timeInc: number of nanoseconds to add to baseTime to get
				// consensus or est time

				if (selfConsTrans || isSwirldState2()) {
					transLists.pollCons();
				}
			}
		}
		if (toQueue != null) {
			event.estimateTime(selfId, stats.getAvgSelfCreatedTimestamp(), stats.getAvgOtherReceivedTimestamp());
			// update the estimate now, so the queue can sort on it
			if (!toQueue.offer(event)) {
				// this should never happen, because the queue passed
				// in should always be unlimited capacity.
				log.error(EXCEPTION.getMarker(),
						"queue.offer returned false for queue {}",
						() -> toQueue);
			}
		}
		return event;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void expandSignatures(final Transaction[] transactions) {
		// Expand signatures for the given transactions
		// Additionally, we should enqueue any signatures for verification
		final long startTime = System.nanoTime();
		final List<TransactionSignature> signatures = new LinkedList<>();

		double expandTime = 0;

		for (Transaction t : transactions) {
			try {
				if (!t.isSystem()) {
					SwirldTransaction swirldTransaction = (SwirldTransaction) t;
					final long expandStart = System.nanoTime();
					if (getConsensusState() != null) {
						getConsensusState().getSwirldState().expandSignatures(swirldTransaction);
					}
					final long expandEnd = (System.nanoTime() - expandStart);
					expandTime += expandEnd * NANOSECONDS_TO_MILLISECONDS;

					// expand signatures for application transaction
					List<TransactionSignature> sigs = swirldTransaction.getSignatures();

					if (sigs != null && !sigs.isEmpty()) {
						signatures.addAll(sigs);
					}
				}
			} catch (Exception ex) {
				log.error(EXCEPTION.getMarker(),
						"expandSignatures threw an unhandled exception", ex);
			}
		}

		CryptoFactory.getInstance().verifyAsync(signatures);

		final double elapsedTime = (System.nanoTime() - startTime) * NANOSECONDS_TO_MILLISECONDS;
		CryptoStatistics.getInstance().setPlatformSigIntakeValues(elapsedTime, expandTime);
	}
}
