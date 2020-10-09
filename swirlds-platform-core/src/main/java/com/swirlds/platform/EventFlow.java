/*
 * (c) 2016-2020 Swirlds, Inc.
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

import com.swirlds.common.Address;
import com.swirlds.common.SwirldState;
import com.swirlds.common.SwirldState.SwirldState2;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.Hash;
import com.swirlds.logging.LogMarker;
import com.swirlds.common.merkle.hash.FutureMerkleHash;
import com.swirlds.common.merkle.hash.MerkleHashChecker;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.StateInfo;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.swirlds.logging.LogMarker.EVENT_CONTENT;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.QUEUES;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.SIGNED_STATE;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT;
import static com.swirlds.platform.event.EventUtils.toShortString;

/**
 * An EventFlow object is created by a Platform to manage its 3 SwirldState objects, the 7 queues that hold
 * the Events flowing between them, and the 4 threads that cause those events to flow between the states.
 */
class EventFlow extends AbstractEventFlow {
	/** used to make threadCons, threadCurr, threadWork all wait during a shuffle */
	private final CyclicBarrier shuffleBarrier;

	/** lists of transactions by self (both system and non-system). See docs for TransLists. */
	private final TransLists transLists;

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/** Platform that instantiates/uses this */
	private final AbstractPlatform platform;

	// hashEventsCons is volatile rather than something like AtomicIntArray, because elements of it
	// never change. The array is always replaced with an entirely new Hash.
	/** running hash of all consensus events so far with their transactions handled by stateCons */
	private volatile Hash hashEventsCons = null;

	/** number of events that have had their transactions handled by stateCons so far. */
	private final AtomicLong numEventsCons = new AtomicLong(0);

	/** current (most recent) state */
	private volatile StateInfo stateCurr;
	/** reflects all known consensus events */
	private volatile StateInfo stateCons;
	/** working state that will replace stateCurr once it catches up */
	private volatile StateInfo stateWork;

	/** keeps the state returned to the user app to ensure that it doesn't get deleted */
	private volatile SwirldState stateCurrReturned = null;
	/** a Semaphore that ensures that only one stateCurr will be passed to the user app at a time */
	private final Semaphore getStateSemaphore = new Semaphore(1);

	// Other classes SHOULD NOT ACCESS forCurr. The one exception is that
	// TransLists should access forCurr, but only to add TransLists.NoEvent to unblock it when the
	// queue is empty and transactions are waiting to be handled).

	/** Events to send to stateCurr. */
	private final BlockingQueue<EventImpl> forCurr;
	/** consensus events to send to stateCons */
	private final BlockingQueue<EventImpl> forCons;
	/** old events to delete after signed state */
	private final BlockingQueue<EventImpl> forSigs;
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
	/** A thread that hashes and signs a state as a background task */
	private StoppableThread threadStateHashSign;

	/** Used for hashing the signed state */
	private final Cryptography cryptography;

	/** when the last shuffle happened */
	private volatile Instant lastShuffle;

	/** does the app's state inherit from SwirldState2? */
	private final boolean swirldState2;

	/**
	 * indicates whether a state was saved in the current freeze period. we are only saving the first state
	 * in the freeze period. this variable is only used by threadCons so there is no synchronization needed
	 */
	private boolean savedStateInFreeze = false;

	/**
	 * an array that keeps the last consensus event by each member.
	 * this variable is only used by threadCons so there is no synchronization needed, it is volatile in case
	 * stopAndClear() gets called.
	 */
	private volatile EventImpl[] lastConsEventByMember;
	/**
	 * an array that keeps track of whose last event is in forSigs
	 * this variable is only used by threadCons so there is no synchronization needed, it is volatile in case
	 * stopAndClear() gets called.
	 */
	private volatile boolean[] lastConsEventInForSigs;

	/** A deque used by doCons to store the minimum generation of famous witnesses per round */
	private final Deque<Pair<Long, Long>> doConsMinGenFamous = new ConcurrentLinkedDeque<>();

	/** A queue that holds only 1 object about a state that needs to be hashed and signed. */
	private final BlockingQueue<SignedState> stateToHashSign = new ArrayBlockingQueue<>(1);

	/** how many of {threadCons, threadCurr, threadWork} exist (either 2 or 3) */
	private final int numThreads;

	/**
	 * Instantiate, but don't start any threads yet. The Platform should first instantiate the EventFlow,
	 * which creates the 3 states and 6 queues and 3 threads. Then the Platform should call startAll to
	 * start the 3 threads.
	 *
	 * @param platform
	 * 		the Platform that instantiated this, and that this should call
	 * @param initialState
	 * 		the SwirldState that should be used (along with 2 fast copies of it) to initialize all 3
	 * 		of the states stored here. This initial state will be used by EventFlow internally, so it
	 * 		should not be used after being passed to it
	 */
	EventFlow(AbstractPlatform platform, SwirldState initialState) {
		this.platform = platform;
		this.swirldState2 = (initialState instanceof SwirldState2);
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
		forCons = Settings.maxEventQueueForCons == 0
				? new LinkedBlockingQueue<>()
				: new ArrayBlockingQueue<>(Settings.maxEventQueueForCons);

		forSigs = new LinkedBlockingQueue<>();

		if (!swirldState2) {
			forWork = new PriorityBlockingQueue<>(100, cmp);
			forNext = new PriorityBlockingQueue<>(100, cmp);
		}

		if (!swirldState2) {
			// in this case, there would be one thread more than swirldState2 for running doWork
			numThreads = 3;
		} else {
			numThreads = 2;
		}
		shuffleBarrier = new CyclicBarrier(numThreads, () -> {
			try {
				shuffle();
			} catch (Exception e) {
				log.error(EXCEPTION.getMarker(), "shuffle exception {}",
						() -> e + Arrays.toString(e.getStackTrace()));
			}
		});

		lastConsEventByMember = new EventImpl[platform.getNumMembers()];
		lastConsEventInForSigs = new boolean[platform.getNumMembers()];

		cryptography = CryptoFactory.getInstance();
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
	SwirldState getCurrStateAndKeep() {
		if (swirldState2) {
			return stateCurr.getState();
		}

		try {
			getStateSemaphore.acquire();
		} catch (InterruptedException e1) {
			return null;
		}
		synchronized (this) {
			if (stateCurr == null) {
				return null;
			}
			// stateCurrReturned becomes the current one
			stateCurrReturned = stateCurr.getState();
		}

		return stateCurrReturned;
	}

	/**
	 * Releases the state that was previously returned, so that another one can be obtained from getCurrStateAndKeep(),
	 * also deletes it if it's not the current state being used
	 */
	synchronized void releaseStateCurr() {
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
				stateCurr.getState().release();
			} catch (Exception e) {
				// defensive: catch exceptions from a bad app
				log.error(EXCEPTION.getMarker(), "exception in app during delete:", e);
			}
		}

		stateCurr = stateWork;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	SwirldState getConsensusState() {
		return this.stateCons.getState();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	int getForConsSize() {
		return forCons.size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	TransLists getTransLists() {
		return transLists;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	int getForSigsSize() {
		return forSigs.size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	int getForCurrSize() {
		return forCurr.size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void forCurrPut(EventImpl event) {
		try {
			event.estimateTime(platform.getSelfId(), platform.getStats().avgSelfCreatedTimestamp.getWeightedMean(),
					platform.getStats().avgOtherReceivedTimestamp.getWeightedMean());
			// update the estimate now, so the queue can sort on it
			forCurr.put(event);
		} catch (InterruptedException e) {
			log.error(EXCEPTION.getMarker(), "error:{} event:{}", e, event);
		}
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
	 * {@inheritDoc}
	 */
	@Override
	void forConsPut(EventImpl event) {
		try {
			// this may block until the queue isn't full
			forCons.put(event);
		} catch (InterruptedException e) {
			log.error(RECONNECT.getMarker(), "forConsPut interrupted");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	synchronized void stopAndClear() {
		// stopAndClear() is currently only used to reconnect. when reconnecting, we need the consensus state to do
		// the reconnect, that's why it will not be cleared.

		// tell the threads they should stop
		threadStateHashSign.stop();
		threadCons.stop();
		threadCurr.stop();

		if (!swirldState2) {
			threadWork.stop();
		}

		// delete the states
		try {
			//if (stateCons != null) {
			//	stateCons.state.noMoreTransactions();
			//	stateCons.state.delete();
			//}
			if (!swirldState2) {
				if (stateWork != null) {
					stateWork.getState().noMoreTransactions();
					stateWork.getState().release();
				}
				if (stateCurr != null) {
					stateCurr.getState().noMoreTransactions();
					stateCurr.getState().release();
				}
			}

		} catch (Exception e) {
			// defensive: catch exceptions from a bad app
			log.error(EXCEPTION.getMarker(), "exception in SwirldState:", e);
		}

		stateWork = null;


		// clear all the queues
		stateToHashSign.clear();
		forCurr.clear();
		forCons.clear();
		forSigs.clear();
		if (!swirldState2) {
			forWork.clear();
			forNext.clear();
		}

		// clear the transactions
		transLists.clear();

		// clear running transaction info
		hashEventsCons = null;
		numEventsCons.set(0);

		// used by doCons
		lastConsEventByMember = new EventImpl[platform.getNumMembers()];
		lastConsEventInForSigs = new boolean[platform.getNumMembers()];
		doConsMinGenFamous.clear();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void loadDataFromSignedState(SignedState signedState, boolean isReconnect) {
		EventImpl[] lastConsEventByMember = new EventImpl[platform.getNumMembers()];
		boolean[] lastConsEventInForSigs = new boolean[platform.getNumMembers()];

		for (int i = 0; i < signedState.getEvents().length; i++) {
			EventImpl event = signedState.getEvents()[i];
			boolean added = forSigs.offer(event);

			if (!added) {// this should never happen
				log.error(EXCEPTION.getMarker(),
						"forSigs full! EventFlow.forSigsPut()");
				break;
			}

			// keep track of the last event created by each member
			lastConsEventByMember[(int) event.getCreatorId()] = event;
			lastConsEventInForSigs[(int) event.getCreatorId()] = true;
		}

		this.lastConsEventByMember = lastConsEventByMember;
		this.lastConsEventInForSigs = lastConsEventInForSigs;

		hashEventsCons = signedState.getHashEventsCons();
		numEventsCons.set(signedState.getNumEventsCons());

		doConsMinGenFamous.addAll(signedState.getMinGenInfo());
		log.info(STARTUP.getMarker(), " doConsMinGenFamous after startup {}",
				Arrays.toString(signedState.getMinGenInfo().toArray()));

		// get initialRunningHash from signedState
		Hash initialHash = new Hash(signedState.getHashEventsCons());
		platform.getRunningHashCalculator().setInitialHash(initialHash);

		log.info(STARTUP.getMarker(), " initialHash after startup {}", () -> initialHash);
		if (Settings.enableEventStreaming) {
			platform.getRunningHashCalculator().setStartWriteAtCompleteWindow(isReconnect);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void setState(SwirldState state) {
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
			stateCurr = new StateInfo(state, null, false);
			if (stateWork != null) {
				stateWork.release();
			}
			stateWork = new StateInfo(state, null, false);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Transaction[] pollTransListsForEvent() {
		return transLists.pollTransForEvent();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	int numUserTransForEvent() {
		return transLists.numUserTransForEvent();
	}

	@Override
	int numFreezeTransEvent() {
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
	 * @param system
	 * 		is this a system transaction which should NOT be sent to the app?
	 * @param transaction
	 * 		the new transaction being created locally (optionally passed as several parts to
	 * 		concatenate)
	 * @return true if successful
	 */
	boolean createTransaction(boolean system, Transaction transaction) {
		// Refuse to create any type of transaction if the beta mirror is enabled and this node has zero stake
		if (Settings.enableBetaMirror && platform.isZeroStakeNode()) {
			return false;
		}

		if (transaction == null
				|| transaction.getLength() > Settings.transactionMaxBytes) {
			return false;
		}

		return transLists.offer(transaction, system);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void startAll() {
		threadStateHashSign = new StoppableThread("stateHash",
				this::stateHashSign, platform.getSelfId());
		threadCons = new StoppableThread("threadCons",
				this::doCons, platform.getSelfId());
		threadCurr = new StoppableThread("threadCurr",
				this::doCurr, platform.getSelfId());

		if (!swirldState2) {
			threadWork = new StoppableThread("threadWork",
					this::doWork, platform.getSelfId());
		}

		lastShuffle = Instant.now();
		threadStateHashSign.start();
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
				forSigs == null ? null : forSigs.size(),
				forWork == null ? null : forWork.size());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	BlockingQueue<EventImpl> getForCurr() {
		return forCurr;
	}

	/**
	 * For debugging, return a string giving the current number of elements in each of the 5 queues, in the
	 * order forCurr, forWork, forNext, forCons, forSigs
	 *
	 * @return
	 */
	String getQueueSizes() {
		return String.format(
				"%4d=forCurr %4d=forWork %4d=forNext %4d=forCons %8d=forSigs",
				forCurr.size(), forWork.size(), forNext.size(), forCons.size(),
				forSigs.size());
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
	 * @param platform
	 * 		the platform managing this event, state, and hashgraph
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
	 * @param address
	 * 		address of person to add as a new member in the address book, or null if none
	 * @param baseTime
	 * 		timestamp (consensus or estimated) of the event, before adding timeInc.
	 * @param timeInc
	 * 		number of nanoseconds to add to baseTime, before passing it to the user as the estimated
	 * 		or actual timestamp
	 */
	private void handleTransaction(AbstractPlatform platform, boolean ignoreSystem,
			boolean isSystem, StateInfo state, boolean isConsensus, EventImpl event,
			Transaction trans, Address address, Instant baseTime,
			long timeInc) {
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
			long creator = event == null ? platform.getSelfId().getId() // selfId assumed to be main
					: event.getCreatorId();

			/** The claimed creation time of the event holding this transaction (or now, if no event) */
			Instant timeCreated = (event == null ? Instant.now()
					: event.getTimeCreated());

			/**
			 * Estimate of what will be this transaction's consensus timestamp. The first transaction in an
			 * event gets the event's timestamp, and then each successive transaction in that event has a
			 * timestamp 1 nanosecond later. If the event already has consensus, the estimate is exact.
			 */
			Instant estConsTime = baseTime.plusNanos(timeInc);

			if (isSystem) {// send system transactions to Platform (they come from doCons)
				platform.handleSystemTransaction(creator, isConsensus,
						timeCreated, estConsTime, trans, address);
				if (event != null && event.getReachedConsTimestamp() != null) {
					// we only add this stat for transactions that have reached consensus
					platform.getStats().avgConsHandleTime.recordValue(
							event.getReachedConsTimestamp().until(Instant.now(),
									ChronoUnit.NANOS) / 1_000_000_000.0);
				}
			} else { // send user transactions to SwirldState

				// If we have consensus, validate any signatures present and wait if necessary
				if (isConsensus) {
					for (TransactionSignature sig : trans.getSignatures()) {
						Future<Void> future = sig.waitForFuture();

						// Block & Ignore the Void return
						future.get();
					}
				}
				long startTime = System.nanoTime();

				state.getState().handleTransaction(creator, isConsensus, timeCreated,
						estConsTime, trans, address);

				// we only add these stats for transactions that have reached consensus
				if (event != null && event.getReachedConsTimestamp() != null) {
					platform.getStats().avgSecTransHandled.recordValue(
							(System.nanoTime() - startTime) / 1_000_000_000.0);
					platform.getStats().transHandledPerSecond.cycle();
					platform.getStats().avgConsHandleTime.recordValue(
							event.getReachedConsTimestamp().until(Instant.now(),
									ChronoUnit.NANOS) / 1_000_000_000.0);
				}
			}
		} catch (InterruptedException ex) {
			log.debug(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
					"EventFlow::handleTransaction Interrupted [ nodeId = {} ]", platform.getSelfId().getId(), ex);
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
					() -> event, trans::getContentsDirect);
		}
	}

	@Override
	void addMinGenInfo(long round, long minGeneration) {
		doConsMinGenFamous.add(Pair.of(round, minGeneration));
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
		event = takeHandlePut(true, forCons, stateCons, forSigs);
		if (event == null) {
			return;
		}
		if (!event.isConsensus()) {
			log.error(EXCEPTION.getMarker(), "EventFlow forCons had nonconensus event {}", event);
		}

		// keep track of the last event created by each member
		lastConsEventByMember[(int) event.getCreatorId()] = event;
		lastConsEventInForSigs[(int) event.getCreatorId()] = true;

		// count events that have had all their transactions handled by stateCons
		numEventsCons.incrementAndGet();

		// set the running hash of all hashes of consensus events so far
		hashEventsCons = event.getRunningHash();

		if (event.isLastOneBeforeShutdown() || // if eventstream is shuting down
				event.isLastInRoundReceived()  // if we have a new shared state to sign
				&& Settings.state.getSignedStateKeep() > 0 // we are keeping states
				&& Settings.signedStateFreq > 0 // and we are signing states
				&& (event.getRoundReceived() == 1 // the first round should be signed
				// and every Nth should be signed, where N is signedStateFreq
				|| event.getRoundReceived() % Settings.signedStateFreq == 0)) {

			// the consensus timestamp for the signed state should be the timestamp of the last transaction
			// in the last event. if the last event has no transactions, then it will be the timestamp of
			// the event
			Instant ssConsTime = event.getLastTransTime();

			boolean signThisState = true;

			if (FreezeManager.isInFreezePeriod(ssConsTime)) {
				if (!savedStateInFreeze) {
					// we are saving the first state in the freeze period
					signThisState = true;
					savedStateInFreeze = true;
				} else {
					signThisState = false;
				}
			} else {
				// once the freeze period has ended, this variable is reset
				savedStateInFreeze = false;
			}

			if (signThisState) {
				log.info(SIGNED_STATE.getMarker(), "about to sign a state");
				// create a new signed state, sign it, and send out a new transaction with the signature
				// the signed state keeps a copy that never changes.
				final long startCopy = System.nanoTime();
				final SwirldState immutableStateCons;
				synchronized (stateCons) {
					immutableStateCons = stateCons.getState();
					// we increment the refCount so the state doesn't get deleted until we put it into a signed state
					immutableStateCons.incrementReferenceCount();
					stateCons.setState(stateCons.getState().copy());
				}

				platform.getStats().avgSecStateCopy.recordValue((System.nanoTime() - startCopy) / 1_000_000_000.0);
				// this state will be saved, so there will be no more transactions sent to it
				try {
					immutableStateCons.noMoreTransactions();
				} catch (Exception e) {
					// defensive: catch exceptions from a bad app
					log.error(EXCEPTION.getMarker(), "exception in app during noMoreTransactions:", e);
				}

				// the saved state should contain at least one event by each member. if the member doesnt have an
				// event in the forSigs, we need to add an older event in the signed state
				final List<EventImpl> ssEventList = new ArrayList<>(forSigs.size() + lastConsEventByMember.length);
				for (int i = 0; i < lastConsEventInForSigs.length; i++) {
					if (!lastConsEventInForSigs[i] && lastConsEventByMember[i] != null) {
						ssEventList.add(lastConsEventByMember[i]);
					}
				}

				ssEventList.addAll(forSigs);
				EventImpl[] events = ssEventList.toArray(new EventImpl[] { });

				log.info(SIGNED_STATE.getMarker(), "finished adding events, about to create a minGen list");
				// create a minGen list with only the rounds up until this round received
				final List<Pair<Long, Long>> minGen = new ArrayList<>();
				for (Pair<Long, Long> next : doConsMinGenFamous) {
					if (next.getKey() <= event.getRoundReceived()) {
						minGen.add(next);
					} else {
						break;
					}
				}

				// The doCons thread will not wait for a state to be signed, it will put it into this queue to be done
				// in the background. If the hashing cannot be done before the next state needs to be signed, doCons
				// will block and wait.
				log.info(SIGNED_STATE.getMarker(),
						"about to put a NewSignedStateInfo to stateToHashSign for round:{} , event: {}",
						event.getRoundReceived(),
						toShortString(event));

				stateToHashSign.put(
						new SignedState(
								immutableStateCons,
								event.getRoundReceived(),
								numEventsCons.get(),
								hashEventsCons,
								platform.getHashgraph().getAddressBook().copy(),
								events,
								ssConsTime,
								savedStateInFreeze,
								minGen
						)
				);
				immutableStateCons.decrementReferenceCount();
			}
		}

		// the next part removes events from forSigs that are not needed

		// the latest round received
		long roundReceived = event.getRoundReceived();
		// the round whose min generation we need
		long staleRound = roundReceived - Settings.state.roundsStale;
		// if we dont have stale rounds yet, no need to do anything
		/* starting round is now 1, so anything less than one will be discounted */
		if (staleRound < 1) {
			return;
		}
		Pair<Long, Long> minGenInfo = doConsMinGenFamous.peekFirst();
		// it should not be null
		if (minGenInfo == null) {
			log.error(EXCEPTION.getMarker(), "Missing min gen info for round {}. Queue is empty", staleRound);
			return;
		}
		// remove old min gen info we no longer need
		while (minGenInfo.getKey() < staleRound) {
			doConsMinGenFamous.pollFirst();
			minGenInfo = doConsMinGenFamous.peekFirst();
		}
		// a round should not be missing, if it is, log an error
		if (minGenInfo.getKey() != staleRound) {
			log.error(EXCEPTION.getMarker(), "Missing min gen info for round {}. Oldest round stored {}",
					staleRound, minGenInfo.getKey());
			return;
		}

		long minGen = minGenInfo.getValue();

		EventImpl e = forSigs.peek();
		while (e != null && e.getGeneration() < minGen) {
			if (e == lastConsEventByMember[(int) e.getCreatorId()]) {
				// if we are deleting the last event created by this member, we must keep track of it so we add it to
				// the saved state
				lastConsEventInForSigs[(int) e.getCreatorId()] = false;
			}
			forSigs.poll(); // e is not needed, so remove and discard it
			e = forSigs.peek(); // look at the next e, but don't remove or discard yet
		}
	}

	private void stateHashSign() throws InterruptedException {
		log.info(SIGNED_STATE.getMarker(), "stateHashSign:: about to hash and sign the state");
		// get the state to be hashed and signed, wait if necessary
		SignedState signedState = stateToHashSign.take();

		// hash a new signed state, signed only by self so far,
		// create a transaction with self signature (and gossip to other members),
		// and start collecting signatures on it from other members.
		long startTime = System.nanoTime();
		log.info(LogMarker.MERKLE_HASHING.getMarker(), "Starting hashing of SignedState");

		// Use digestTreeAsync because it is significantly (10x+) faster for large trees
		final FutureMerkleHash hashFuture = cryptography.digestTreeAsync(signedState);
		// wait for the hash to be computed
		hashFuture.get();

		if (Settings.checkSignedStateHashes) {
			MerkleHashChecker.checkSync(cryptography, signedState, node ->
					log.info(LogMarker.ERROR.getMarker(),
							"Invalid hash detected for node type: {}",
							node.getClass().getName()));
		}


		log.info(LogMarker.MERKLE_HASHING.getMarker(), "Done hashing SignedState, starting newSelfSigned");

		platform.getSignedStateManager().newSelfSigned(
				platform,
				signedState
		);
		log.info(LogMarker.MERKLE_HASHING.getMarker(), "Done newSelfSigned");
		platform.getStats().avgSecNewSignedState.recordValue((System.nanoTime() - startTime) / 1_000_000_000.0);
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
			stateCurr.getState().noMoreTransactions();
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
				stateWork.getState());
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
	EventImpl takeHandlePut(boolean allowShuffle, BlockingQueue<EventImpl> fromQueue, StateInfo stateInfo,
			BlockingQueue<EventImpl> toQueue) {
		Transaction[] transactions;
		Instant baseTime;
		EventImpl event = null;

		// time to wait until the next shuffle
		long wait = Settings.delayShuffle
				- lastShuffle.until(Instant.now(), ChronoUnit.MILLIS);

		if (stateInfo.getState() instanceof SwirldState2) {
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
			}
			return null;
		}

		// get the next event which might be an Event, or noEvent, or null
		if (fromQueue != null) {
			try {
				event = fromQueue.poll(wait, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				event = null;
				log.error(RECONNECT.getMarker(),
						"takeHandlePut() fromQueue.poll() interrupted");
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
		baseTime = platform.estimateTime();

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
				handleTransaction(platform, // platform: the platform that made this EventFlow
						true, // ignoreSystem: yes, though there won't be any
						trans.isSystem(), // isSystem: always false (system transactions aren't in the queue)
						stateInfo, // state: contains the state that will handle the transaction
						false, // isConsensus: this isn't in any event, so isn't yet consensus
						null, // event: this transaction isn't in an event (so timeCreated will be now)
						trans, // trans: the transaction to handle
						null, // address: the address to possibly add to addressBook
						baseTime, // baseTime: the timestamp (consensus or estimated) of the event
						i); // timeInc: number of nanoseconds to add to baseTime to get consensus or est time
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
			toQueue.offer(event);
			return event;
		}

		// estimate the timestamp that this event will have
		event.estimateTime(platform.getSelfId(), platform.getStats().avgSelfCreatedTimestamp.getWeightedMean(),
				platform.getStats().avgOtherReceivedTimestamp.getWeightedMean());
		baseTime = event.getConsensusTimestamp();

		transactions = event.getTransactions();
		// sys = event.getSysTransaction();

		// Discard events with no transactions.
		if (transactions == null
				|| transactions.length == 0
				|| (!isSwirldState2() && event.isConsensus()
				&& stateInfo.getLastCons() != null
				&& event.getConsensusOrder() <= stateInfo.getLastCons()
				.getConsensusOrder())) {
			if (toQueue == forSigs) {
				// we need to save all the events to forSigs
				event.estimateTime(platform.getSelfId(), platform.getStats().avgSelfCreatedTimestamp.getWeightedMean(),
						platform.getStats().avgOtherReceivedTimestamp.getWeightedMean());
				// update the estimate now, so the queue can sort on it
				if (!toQueue.offer(event)) {
					// this should never happen, because the queue passed
					// in should always be unlimited capacity.
					log.error(EXCEPTION.getMarker(),
							"queue.offer returned false for queue forSigs");
				}
			}
			return event;
		}
		if (event.isConsensus()) {
			stateInfo.setLastCons(event);// not used for SwirldsState2
		}

		// for SwirldState2, we say events from states other than stateCons are
		// not consensus, even if they are, to keep the contract that every
		// transaction is handled twice, once marked as consensus and once not.
		boolean allowIsConsensusTrue = !(stateInfo.getState() instanceof SwirldState2)
				|| (fromQueue == forCons);
		// if doCons just handled a self-transaction, then remove it from the
		// list of transactions that will be sent to the new stateWork to handle
		// after the next shuffle.
		boolean selfConsTrans = stateInfo == stateCons
				&& platform.getSelfId().equalsMain(event.getCreatorId());
		synchronized (stateInfo) {
			for (int i = 0; i < transactions.length; i++) {
				boolean isConsensus = event.isConsensus() && allowIsConsensusTrue;

				// this is the only place where handleTransaction is called on system
				// transactions with ignoreSystem == false
				handleTransaction(//
						platform, // platform: the platform that made this EventFlow
						fromQueue != forCons,// All system transactions are handled twice, once with consensus
						// false, and once with true. This is the second time a system
						// transaction is handled when its consensus is known. The
						// first time it was handled is in Hashgraph.addEvent3()
						transactions[i].isSystem(), // isSystem: is this a system transaction?
						stateInfo, //// state: contains the state that will handle the transaction
						isConsensus, // isConsensus: does this event have a consensus order?
						event, // event: the event containing the transaction (whose time gives timeCreated)
						transactions[i], // trans: the transaction to handle
						null, // address: the address to possibly add to addressBook
						baseTime, // baseTime: the timestamp (consensus or estimated) of the event
						i); // timeInc: number of nanoseconds to add to baseTime to get consensus or est time

				if (selfConsTrans || isSwirldState2()) {
					transLists.pollCons();
				}
			}
		}
		if (toQueue != null) {
			event.estimateTime(platform.getSelfId(), platform.getStats().avgSelfCreatedTimestamp.getWeightedMean(),
					platform.getStats().avgOtherReceivedTimestamp.getWeightedMean());
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
}
