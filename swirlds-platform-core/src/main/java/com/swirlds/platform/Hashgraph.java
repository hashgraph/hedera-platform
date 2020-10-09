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
import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.events.BaseEventHashedData;
import com.swirlds.common.events.BaseEventUnhashedData;
import com.swirlds.platform.event.CreateEventTask;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.event.TransactionConstants;
import com.swirlds.platform.internal.CreatorSeqPair;
import com.swirlds.platform.state.SignedState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import static com.swirlds.common.Constants.SEC_TO_NS;
import static com.swirlds.logging.LogMarker.BETA_MIRROR_NODE;
import static com.swirlds.logging.LogMarker.CREATE_EVENT;
import static com.swirlds.logging.LogMarker.EVENT_SIG;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.INTAKE_EVENT;
import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.STALE_EVENTS;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.logging.LogMarker.SYNC;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT;


class Hashgraph extends AbstractHashgraph {
	// all variables below are private

	static final Marker INTAKE = MarkerManager.getMarker("INTAKE");
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	/** immutable current version of the address book. (will later store one per round) */
	private final AddressBook latestAddressBook; // if this code is changed to non-final, make it volatile
	/** the Platform that is using this hashgraph */
	private final AbstractPlatform platform;
	/** the member ID of the member running the platform using this hashgraph */
	private final NodeId selfId;

	///////////// EVENTS NOT YET IN THE HASHGRAPH ///////////////
	/** the number of transactions currently in the hashgraph */
	private final AtomicLong numTrans = new AtomicLong(0);
	/** sequence number of first known event by each member (-1 if none) */
	private final AtomicLongArray firstSeq;
	/** A pool of threads used for processing received events and creating new events */
	//private ExecutorService intakeThreadPool;
	/** sequence number of last known event by each member (-1 if none) */
	private final AtomicLongArray lastSeq;
	/** number of members not started yet */
	private final AtomicLong numNotStarted;
	/** used to keep track of which members have started and decrease the numNotStarted value */
	private final AtomicReferenceArray<Boolean> nodesStarted;
	/** A queue of potential events that are either received or about to be created */
	private BlockingQueue<EventIntakeTask> intakeQueue = new LinkedBlockingQueue<>();
	/** A map of the potential events in the queue used to find them by creator id and sequence */
	private ConcurrentMap<CreatorSeqPair, EventIntakeTask> intakeMap = new ConcurrentHashMap<>();

	/////////////////////////////////////////////////////////////
	/** An array that stores the last potential event by each member */
	private AtomicReferenceArray<EventIntakeTask> lastInfoByMember;
	/** The last sequence used for other parent by this node */
	private AtomicReferenceArray<Long> lastOtherParentSeq;
	/** A lock used to ensure that new events are added to the intake queue in the appropriate order */
	private ReentrantLock addNewEventLock = new ReentrantLock();
	/** A lock used to ensure that any event that is in the intakeQueue will also be in the intakeMap */
	private ReentrantLock addRecEventLock = new ReentrantLock();
	/** a thread that polls the intake queue and adds events into the hashgraph */
	private StoppableThread threadPollIntakeQueue;
	/** the number of non-consensus, non-stale events with user transactions currently in the hashgraph */
	private volatile long numUserTransEvents = 0;
	/** the last round received with at least 1 user transaction */
	private volatile long lastRRwithUserTransaction = -1;
	/**
	 * the last round received after which there were no non consensus user transactions in the hashgraph, but before
	 * this round there were
	 */
	private volatile long lastRoundReceivedAllTransCons = -1;
	/** the object that calculates the consensus of events */
	private volatile Consensus consensus;

	/** number of stale events ever */
	private AtomicLong staleEventsTotal = new AtomicLong(0);
	/** the maximum generation of all the events we have */
	private volatile long maxGeneration = -1;

	/** track events by peer to determine which nodes fall within a super minority based on number of events */
	private StrongMinority strongMinority;

	/**
	 * constructor that is given the platform using the hashgraph, and the initial addressBook (which can
	 * change)
	 *
	 * @param platform
	 * 		the platform using the hashgraph
	 * @param addressBook
	 * 		the initial addressBook (which can change)
	 * @param selfId
	 * 		the ID of the platform this hashgraph is running on
	 * @param startIntakeThreads
	 * 		a flag that signifies whether the intake threads should be started, they can be turned off for testing
	 * 		purposes
	 * @param executorService
	 * 		a executor service used for instantiating internal thread pool for process EventInfo instances
	 * @implNote executorService is no longer used	due to the change to move validation to single threaded model
	 * 		This will be re-evaluated in the future
	 */
	Hashgraph(AbstractPlatform platform, AddressBook addressBook, NodeId selfId, boolean startIntakeThreads,
			ExecutorService executorService) {
		this.platform = platform;
		this.selfId = selfId;
		this.latestAddressBook = addressBook.immutableCopy();

		consensus = new ConsensusImpl(
				platform::getStats,
				(l1, l2) -> platform.getEventFlow().addMinGenInfo(l1, l2),
				selfId,
				latestAddressBook);

		int n = addressBook.getSize();
		firstSeq = new AtomicLongArray(n);
		lastSeq = new AtomicLongArray(n);

		if (Settings.enableBetaMirror) {
			// only count nodes with non-zero stake excluding self from the count if the local node has stake
			int numStakedNodes = addressBook.getNumberWithStake();

			if (!AbstractPlatform.isZeroStakeNode(addressBook, selfId.getId())) {
				numStakedNodes--;
			}

			numNotStarted = new AtomicLong(numStakedNodes);
		} else {
			numNotStarted = new AtomicLong(n); // lastSeq starts with n -1 elements, so n haven't started
		}

		nodesStarted = new AtomicReferenceArray<>(n);

		lastInfoByMember = new AtomicReferenceArray<>(n);
		lastOtherParentSeq = new AtomicReferenceArray<>(n);

		// the above two need to be AtomicLongArray instead of long[], even though everything is already
		// properly synchronized, because this is the only way to make each array element volatile.
		for (int i = 0; i < n; i++) {
			firstSeq.set(i, -1);
			lastSeq.set(i, -1);
			lastOtherParentSeq.set(i, -1L);
		}

		strongMinority = new StrongMinorityStake(consensus, addressBook);

		if (startIntakeThreads) {
			// start a thread that will forever process events from the intake queue
			startThreadPollIntakeQueue();
		}
		//intakeThreadPool = executorService;
	}

	@Override
	long getMinGenerationNonAncient() {
		return consensus.getMinGenerationNonAncient();
	}

	private void startThreadPollIntakeQueue() {
		threadPollIntakeQueue = new StoppableThread("pollIntakeQueue", this::intakeQueueHandler, platform.getSelfId());
		threadPollIntakeQueue.start();
		log.info(STARTUP.getMarker(), "threadPollIntakeQueue is started");
	}

	/**
	 * Clear all the Event data that has been added to the hashgraph
	 * this method is called when a node is fallen behind
	 */
	void clear() {
		// clear intakeQueue so that old event would be not added to forCons queue at reconnect
		intakeQueue.clear();
		intakeMap.clear();
		// stop threadPollIntakeQueue
		threadPollIntakeQueue.stop();

		for (int i = 0; i < latestAddressBook.getSize(); i++) {
			firstSeq.set(i, -1);
			lastSeq.set(i, -1);
		}

		numUserTransEvents = 0;
		lastRoundReceivedAllTransCons = -1;
	}

	/**
	 * @return the number of stale events ever
	 */
	public long getStaleEventsTotal() {
		return staleEventsTotal.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	long getNumNotStarted() {
		return numNotStarted.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	boolean hasNodeStarted(int nodeId) {
		Boolean started = nodesStarted.get(nodeId);
		return started == null ? false : started;
	}

	/**
	 * return the round number of the latest round for which the fame of all witnesses has been decided for
	 * that round and all earlier rounds.
	 *
	 * @return the round number, or -1 if none
	 */
	long getLastRoundDecided() {
		return consensus.getLastRoundDecided();

	}

	/**
	 * Return the max round number for which we have an event. If there are none yet, return -1.
	 *
	 * @return the max round number, or -1 if none.
	 */
	long getMaxRound() {
		return consensus.getMaxRound();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AddressBook getAddressBook() {
		// this is final and immutable, so just return it.
		// if the code is ever changed so that it is non-final but immutable, then it will be volatile, so
		// it will still be ok to just return it.
		return latestAddressBook;
	}

	/**
	 * return the number of transactions currently in the hashgraph
	 *
	 * The returned value may be slightly out of date.
	 *
	 * @return the number of transactions
	 */
	long getNumTrans() {
		return numTrans.get();
	}

	/** get the highest sequence number of all events with the given creator */
	long getLastSeq(long creatorId) {
		return lastSeq.get((int) creatorId);
	}

	/**
	 * Return the sequence numbers of the last event created by each member. This is a copy, so it is OK for
	 * the caller to modify it.
	 *
	 * The returned array values may be slightly out of date, and different elements of the array may be out
	 * of date by different amounts.
	 *
	 * @return an array of sequence numbers indexed by member id number
	 */
	long[] getLastSeqByCreator() {
		int len = lastSeq.length();
		long[] result = new long[len];
		for (int i = 0; i < len; i++) {
			result[i] = lastSeq.get(i);
		}
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	int getEventIntakeQueueSize() {
		return intakeQueue.size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	int getNumEvents() {
		return consensus.getNumEvents();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	EventImpl[] getAllEvents() {
		return consensus.getAllEvents();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	EventImpl[] getRecentEvents(long minGenerationNumber) {
		return consensus.getRecentEvents(minGenerationNumber);
	}

	/**
	 * get an event, given the ID of its creator, and the sequence number, where that creator's first event
	 * is seq==0, the next is seq==1 and so on. Return null if it doesn't exist.
	 *
	 * The returned value may be slightly out of date, so it may return a null if the event was added to the
	 * hashgraph VERY recently.
	 *
	 * @param creatorId
	 * 		the ID of the member who created the event
	 * @param seq
	 * 		the sequence number (0 is the first event they created, 1 is second, etc)
	 * @return the Event, or null if it doesn't exist.
	 */
	EventImpl getEventByCreatorSeq(long creatorId, long seq) {
		CreatorSeqPair pair = new CreatorSeqPair(creatorId, seq);
		return getEventByCreatorSeq(pair);
	}

	/**
	 * get an event, given the ID of its creator, and the sequence number, where that creator's first event
	 * is seq==0, the next is seq==1 and so on. Return null if it doesn't exist.
	 *
	 * The returned value may be slightly out of date, so it may return a null if the event was added to the
	 * hashgraph VERY recently.
	 *
	 * @param pair
	 * 		the creator sequence pair that identifies this event
	 * @return the Event, or null if it doesn't exist.
	 */
	EventImpl getEventByCreatorSeq(CreatorSeqPair pair) {
		return consensus.getEvent(pair);
	}

	/**
	 * Checks if the event is old. An event is old if it is not stored in the eventsByCreatorSeq map,
	 * but only in lastEventByMember
	 *
	 * @param e
	 * 		the event to be checked
	 * @return true if old, false otherwise
	 */
	boolean isOldEvent(EventImpl e) {
		return e.getRoundCreated() > 0 && e.getRoundCreated() <= consensus.getMinRound();
	}

	/**
	 * Return the highest round number that has been deleted (or at least will be deleted soon).
	 *
	 * @return the round number that will be deleted (along with all earlier rounds)
	 **/
	long getDeleteRound() {
		return consensus.getMinRound() - 1;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	long getNumUserTransEvents() {
		return numUserTransEvents;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	long getLastRoundReceivedAllTransCons() {
		return lastRoundReceivedAllTransCons;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	boolean isStrongMinorityInMaxRound(final long nodeId) {
		return strongMinority.isStrongMinorityInMaxRound(nodeId);
	}

	/**
	 * @return The last round received with user transactions
	 */
	long getLastRRwithUserTransaction() {
		return lastRRwithUserTransaction;
	}

	void setLastRRwithUserTransaction(long lastRRwithUserTransaction) {
		this.lastRRwithUserTransaction = lastRRwithUserTransaction;
	}

	/**
	 * Update the values of parameters that keep track of which node has been started
	 *
	 * @param nodeId
	 * 		the id of the node that started
	 */
	protected void nodeStarted(long nodeId) {
		// if all nodes have started, we do not need to do anything
		if (numNotStarted.get() > 0) {
			Boolean wasStarted = nodesStarted.getAndSet((int) nodeId,
					true);
			if (wasStarted == null) {
				// if this is the first event received for that member in all of history, or since the restart
				// then that member just stopped being "not started", so decrement the count
				numNotStarted.decrementAndGet();
			}
			platform.checkPlatformStatus();
		}
	}

	/**
	 * Set the given node to not started state that was previously started
	 *
	 * @param nodeId
	 * 		the node ID
	 */
	private void nodeNotStarted(long nodeId) {
		Boolean wasStarted = nodesStarted.getAndSet((int) nodeId, null);
		if (wasStarted != null && wasStarted) {
			numNotStarted.incrementAndGet();
		}
	}

	/**
	 * Record an event in the hashgraph, if its roundCreated will not be smaller than min round;
	 * Mark the event's creator node as started;
	 * Update statistics and variables;
	 * Get a list of events which reached consensus because of adding this event;
	 * Add those consensus events to the forCons queue
	 *
	 * @param event
	 * @return a list of consensus events, or null if no consensus was reached
	 */
	List<EventImpl> consRecordEvent(EventImpl event) {

		// if max roundCreated of the event's parents is smaller than min round, we discard this event.
		//
		// this exception is acceptable when reconnect happens;
		// for example, suppose node3 has been disconnected for a while,
		// after node3 reconnect, when node0 syncs with node3,
		// node0 creates an event whose otherParent is the lastInfo received from node3, i.e., lastInfoByMember.get(3)
		if (smallerThanMinRound(event)) {
			log.error(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
					"Round created {} will be smaller than min round {}, discarding event: {}",
					event.getMaxRoundCreated(),
					consensus.getMinRound(),
					event.toString());
			log.error(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(), "Event's self parent {}",
					event.getSelfParent());
			log.error(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(), "Event's other parent {}",
					event.getOtherParent());
			log.error(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
					"Consensus min round {}, max round {}, last round decided {}",
					consensus.getMinRound(),
					consensus.getMaxRound(),
					consensus.getLastRoundDecided());
			log.error(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(), "Last Sequence by creator {}",
					Arrays.toString(this.getLastSeqByCreator()));
			event.clear();
			return null;
		}

		// record the event in the hashgraph, which results in the events in consEvent reaching consensus
		List<EventImpl> consEvents = consensus.addEvent(event, latestAddressBook);

		// set the max generation
		maxGeneration = Math.max(maxGeneration, event.getGeneration());

		// check if this event has user transactions, if it does, increment the counter
		if (event.hasUserTransactions()) {
			numUserTransEvents++;
		}

		// count all transactions ever in the hashgraph
		numTrans.set(numTrans.get() + event.getTransactions().length);

		int id = (int) event.getCreatorId();
		long newSeq = lastSeq.get(id) + 1;

		// set the last and first sequence
		lastSeq.set(id, newSeq);
		if (firstSeq.get(id) == -1) {
			firstSeq.set(id, newSeq);
		}
		// mark the node as started
		nodeStarted(event.getCreatorId());

		strongMinority.update(latestAddressBook, event);

		// debug for ecMax
		/*if (selfId == 0) {
			boolean supMin[] = new boolean[numMembers];
			for (int i = 0; i < numMembers; i++) {
				supMin[i] = isStrongMinorityInMaxRound(i);
			}
			log.error(Settings.EXCEPTION, "{} RC:{} mem:{} supMin:{}", toString(event), event.roundCreated,
					ecMaxByMemberIndex.toString(), toString(supMin));
		}*/

		if (consEvents != null) {
			for (EventImpl e : consEvents) {
				// check if this event has user transactions, if it does, decrement the counter
				if (e.hasUserTransactions()) {
					numUserTransEvents--;
					lastRRwithUserTransaction = e.getRoundReceived();
					// we decrement the numUserTransEvents for every event that has user transactions. If the counter
					// reaches 0, we keep this value of round received
					if (numUserTransEvents == 0) {
						lastRoundReceivedAllTransCons = lastRRwithUserTransaction;
					}
				}

				platform.getRunningHashCalculator().forRunningHashPut(e);
				if (e.isLastInRoundReceived()) {
					platform.getSignedStateManager().consensusReachedOnRound(
							e.getRoundReceived(),
							e.getLastTransTime(),
							this::getAllEvents);
				}
			}
		}
		while (consensus.getStaleEventQueue().size() > 0) {
			staleEventsTotal.incrementAndGet();
			platform.getStats().staleEventsPerSecond.cycle();

			EventImpl e = consensus.getStaleEventQueue().poll();
			if (e.hasUserTransactions()) {
				numUserTransEvents--;
				if (numUserTransEvents == 0) {
					lastRoundReceivedAllTransCons = lastRRwithUserTransaction;
				}
			}
			log.warn(STALE_EVENTS.getMarker(), "Stale event ({},{})",
					e.getCreatorId(), e.getCreatorSeq());
		}
		return consEvents;
	}

	/**
	 * Checks if the event will have a round created smaller than minRound. If it does, we should discard it. This
	 * should not cause any issues since that event will be stale anyway.
	 *
	 * @param event
	 * 		the Event to check
	 * @return true if its round will be smaller, false otherwise
	 */
	private boolean smallerThanMinRound(EventImpl event) {
		return event.getMaxRoundCreated() < consensus.getMinRound();
	}

	/**
	 * Reloads the Hashgraph from the {@link SignedState} supplied
	 *
	 * @param signedState
	 * 		the signed state to load from
	 */
	void loadFromSignedState(SignedState signedState) {
		consensus = new ConsensusImpl(
				platform::getStats,
				(l1, l2) -> platform.getEventFlow().addMinGenInfo(l1, l2),
				selfId,
				latestAddressBook,
				signedState);

		// Data that is needed for the intake system to work
		for (int i = 0; i < signedState.getEvents().length; i++) {
			EventImpl e = signedState.getEvents()[i];
			lastSeq.set((int) e.getCreatorId(), e.getCreatorSeq());
			lastInfoByMember.set((int) e.getCreatorId(), new ValidateEventTask(e));

			// set the max generation
			maxGeneration = Math.max(maxGeneration, e.getGeneration());
		}
		log.info(STARTUP.getMarker(), "Last known sequence numbers after restart are {}", lastSeq.toString());

		// if threadPollIntakeQueue is stopped before reconnect,
		// we should start it after reconnect
		if (!threadPollIntakeQueue.isAlive()) {
			startThreadPollIntakeQueue();
		}
	}

	void rescueChildlessEvents() {
		if (Settings.rescueChildlessProbability <= 0) {
			return;
		}
		// ThreadLocalRandom used to avoid locking issues
		Random random = ThreadLocalRandom.current();

		for (int i = 0; i < lastInfoByMember.length(); i++) {
			if (selfId.equalsMain(i)) {
				// we don't rescue our own event, this might have been the cause of a reconnect issue
				continue;
			}
			EventIntakeTask ei = lastInfoByMember.get(i);
			if (ei == null) {
				// we have no last event for this member
				continue;
			}
			if (!ei.isChildless()) {
				// not childless
				continue;
			}
			EventImpl e = ei.getEvent();
			if (e == null) {
				// this event still isn't ready
				continue;
			}
			if (e.getGeneration() + Settings.rescueChildlessGeneration < maxGeneration &&
					random.nextInt(Settings.rescueChildlessProbability) == 0) {
				// if the generation is old enough, and the random generation produced a 0, create an event
				log.info(STALE_EVENTS.getMarker(), "Creating child for childless event ({},{})",
						e.getCreatorId(), e.getCreatorSeq());
				createEvent(e.getCreatorId());
				platform.getStats().rescuedEventsPerSecond.cycle();
			}
		}
	}

	void createEvent(final long otherId) {
		createEvent(otherId, false);
	}

	void createEvent(final long otherId, final boolean compensatingEvent) {
		// If beta mirror node logic is enabled and this node is a zero stake node then we should not
		// create this event
		if (Settings.enableBetaMirror && (platform.isZeroStakeNode()
				|| platform.isZeroStakeNode(otherId))) {
			return;
		}

		// We must set the other parent for an event before adding to the queue. This is because the other
		// parent must be processed before its descendant and any event in this array is already in the queue.
		EventIntakeTask otherParent = lastInfoByMember.get((int) otherId);
		CreateEventTask createEventTask = new CreateEventTask(otherId, compensatingEvent);
		createEventTask.setOtherParent(otherParent);


		// If we are adding a new event to be created to the queue, we must ensure that they are added to the
		// queue in the right order. Because it is a 2 step process, get the last self event & add to the
		// queue, we must put a lock to ensure they will be done atomically.
		addNewEventLock.lock();
		try {
			if (!selfId.equalsMain(otherId) && otherParent != null) {
				if (!(otherParent instanceof ValidateEventTask)) {
					// this should never happen
					log.error(EXCEPTION.getMarker(), "otherParent is not an instanceof ValidateEventTask!");
					return;
				}
				ValidateEventTask validateEventTask = (ValidateEventTask) otherParent;
				// we don't want to create multiple events with the same other parent, so we have to check if we
				// already created an event with this particular other parent
				if (validateEventTask.getCreatorSeq() <= lastOtherParentSeq.get(
						(int) validateEventTask.getCreatorId())) {
					// if our other parent is not newer than the one we already used, we will not create this
					// event

					// if numFreezeTransEvent is not 0, we need to create an Event for the Freeze system transaction
					if (!(platform.getEventFlow().numUserTransForEvent() > 0
							&& getNumUserTransEvents() == 0)
							&& platform.getEventFlow().numFreezeTransEvent() == 0
							&& !compensatingEvent) {
						createEventTask.clearParents();
						return;
					}

				}
				// if we are creating an event, we set lastOtherParentSeq to the current sequence so we don't
				// create another event with the same other parent
				lastOtherParentSeq.set((int) validateEventTask.getCreatorId(), validateEventTask.getCreatorSeq());
			}

			// in order to prevent a node from creating many events without an other parent at startup, this
			// method needs to be called at this point
			nodeStarted(selfId.getId());

			// find the last event that was, or is about to be, created by self and set it as the self parent. At the
			// same time, atomically set this event to be the self parent for the next new event
			// this variable should never be null
			createEventTask.setSelfParent(lastInfoByMember.getAndSet(selfId.getIdAsInt(), createEventTask));
			log.debug(INTAKE_EVENT.getMarker(),
					"Adding self event to intake, other id {}, self parent ({}), other parent ({})",
					() -> otherId,
					() -> {
						EventIntakeTask spi = createEventTask.getSelfParent();
						if (spi == null) {
							return "null";
						}
						EventImpl sp = spi.getEvent();
						if (sp == null) {
							return "N/A";
						} else {
							return selfId + "," + sp.getCreatorSeq();
						}
					},
					() -> {
						EventIntakeTask spi = createEventTask.getOtherParent();
						if (spi == null) {
							return "null";
						}
						EventImpl sp = spi.getEvent();
						if (sp == null) {
							return "N/A";
						} else {
							return otherId + "," + sp.getCreatorSeq();
						}
					});
			intakeQueue.put(createEventTask);

			// the last step is to submit these potential events to the thread pool to be processed in parallel
			//intakeThreadPool.submit(() -> this.createNewEvent(createEventTask));
		} catch (InterruptedException e) {
			// should never happen, and we don't have a simple way of recovering from it
			log.error(EXCEPTION.getMarker(), "CRITICAL ERROR, adding to the event intake queue failed", e);
		} finally {
			addNewEventLock.unlock();
		}
	}

	/**
	 * Add an event to the queue to be instantiated by other threads in parallel. The instantiated event will
	 * eventually be added to the hashgraph by the pollIntakeQueue method.
	 *
	 * If eventInfo.newEvent==true, then this is a new event, created by self, whose other parent is otherId, then all
	 * other parameters are ignored.
	 *
	 * If eventInfo.newEvent==false, then this is an event received in a sync, and all parameters are used.
	 *
	 * @param validateEventTask
	 */
	void addEvent(final ValidateEventTask validateEventTask) {

		// If beta mirror node logic is enabled and the event originated from a node known to have a zero stake
		// then we should discard this event and not even worry about validating the signature in order
		// to prevent a potential DoS or DDoS attack from a zero stake node
		if (Settings.enableBetaMirror
				&& platform.isZeroStakeNode(validateEventTask.getCreatorId())) {
			log.error(TESTING_EXCEPTIONS.getMarker(),
					"Event Intake: Received an event from a zero stake node [ nodeId = {}, eventSeq = {} ]",
					validateEventTask::getCreatorId, validateEventTask::getCreatorSeqPair);
			return;
		}

		// add the potential event to the queue to be added to the hashgraph by the pollIntakeQueue method
		try {
			// we must check for duplicates on for events received
			CreatorSeqPair csPair = validateEventTask.getCreatorSeqPair();
			// this lock ensures that any event added to the intakeMap are added to the intakeQueue as well.
			// Unless this lock is implemented, a situation can occur where a child is put into the queue before
			// the parent
			addRecEventLock.lock();
			try {
				// the events will move from the intake map to the hashgraph map, so we must check them in that
				// order
				EventIntakeTask intake = intakeMap.get(csPair);
				EventImpl hashgraph = getEventByCreatorSeq(csPair);

				if (intake != null || hashgraph != null) {
					// this is a duplicate, ignore it
					platform.getStats().duplicateEventsPerSecond.cycle();
					platform.getStats().avgDuplicatePercent.recordValue(100); // move toward 100%
					//if (intake == null ) { // not in intakeMap but already in hashgraph need remove
					//	log.error(Settings.EXCEPTION, "already in hashgraph {}", eventInfo);
					//} else { // intake != null
					//	log.error(Settings.EXCEPTION, "already in intakeMap {}", eventInfo);
					//}
					return;
				}
				log.debug(INTAKE_EVENT.getMarker(),
						"Adding event ({},{}) to intake, other parent ({},{})",
						validateEventTask.getCreatorId(), validateEventTask.getCreatorSeq(),
						validateEventTask.getOtherId(), validateEventTask.getOtherSeq());

				// we need to look for the parents at this point, to make sure they are in the queue before this
				// event
				if (validateEventTask.hasSelfParent()) {
					EventIntakeTask selfParent = findParent(validateEventTask, true);

					long minGeneration = consensus.getMinGenerationNonAncient();
					if (selfParent == null && validateEventTask.getSelfParentGen() >= minGeneration) {
						// if a parent is missing, then its generation should be smaller than the minimum
						// generation. if its not smaller, then we do not accept this event
						log.error(INVALID_EVENT_ERROR.getMarker(),
								"{} Invalid event! selfParent of ({},{}) is missing." +
										" Claimed self parent gen:{} min gen:{}\n" +
										" Self-parent hash  = {}\n" +
										" Other-parent hash = {}",
								selfId, validateEventTask.getCreatorId(), validateEventTask.getCreatorSeq(),
								validateEventTask.getSelfParentGen(), minGeneration,
								validateEventTask.getSelfParentHashInstance(),
								validateEventTask.getOtherParentHashInstance());
						return;
					}
				}
				// if the event has an other parent, try to find it
				if (validateEventTask.hasOtherParent()) {
					EventIntakeTask otherParent = findParent(validateEventTask, false);

					long minGeneration = consensus.getMinGenerationNonAncient();
					if (otherParent == null && validateEventTask.getOtherParentGen() >= minGeneration) {
						// if a parent is missing, then its generation should be smaller than the minimum
						// generation. if its not smaller, then we do not accept this event
						log.error(INVALID_EVENT_ERROR.getMarker(),
								"{} Invalid event! otherParent({},{}) of ({},{}) is missing." +
										" Claimed other parent gen:{} min gen:{}\n" +
										" Self-parent hash  = {}\n" +
										" Other-parent hash = {}",
								selfId, validateEventTask.getOtherId(), validateEventTask.getOtherSeq(),
								validateEventTask.getCreatorId(), validateEventTask.getCreatorSeq(),
								validateEventTask.getOtherParentGen(), minGeneration,
								validateEventTask.getSelfParentHashInstance(),
								validateEventTask.getOtherParentHashInstance());

						validateEventTask.clearParents();
						return;
					}
				}

				//log.error(Settings.EXCEPTION, "intakeQueue put {}", eventInfo);
				intakeMap.putIfAbsent(csPair, validateEventTask);
				intakeQueue.put(validateEventTask);

				// Update the lastInfoByMember. In the step before this one we discarded duplicates, and any old
				// events
				// should be discarded by the gossip protocol, so the event we have here should be the latest by
				// that
				// member.
				// This should be updated after adding the event to the queue, because any new event that is about
				// to be
				// created should have its other parent in the queue before it.
				lastInfoByMember.set((int) validateEventTask.getCreatorId(), validateEventTask);
				// the last step is to submit these potential events to the thread pool to be processed in parallel
				//intakeThreadPool.submit(() -> this.processIntakeEvent(validateEventTask));
			} finally {
				addRecEventLock.unlock();
			}

		} catch (InterruptedException e) {
			// should never happen, and we don't have a simple way of recovering from it
			log.error(EXCEPTION.getMarker(), "CRITICAL ERROR, adding to the event intake queue failed", e);
		}
	}

	/**
	 * A method that tries to find the parent of a potential event. The parent could either be a valid event in the
	 * hashgraph, or it could be a potential event still being processed.
	 *
	 * @param validateEventTask
	 * 		the event whose parent we are looking for
	 * @param selfParent
	 * 		should be true if we are looking to the self parent and false if we are looking for the other
	 * 		parent
	 * @return the parent being looked for, or null if none was found
	 */
	private EventIntakeTask findParent(ValidateEventTask validateEventTask, boolean selfParent) {
		// are we looking for the self parent or other parent
		CreatorSeqPair pair = null;
		if (selfParent) {
			pair = new CreatorSeqPair(validateEventTask.getCreatorId(), validateEventTask.getCreatorSeq() - 1);
		} else {
			pair = new CreatorSeqPair(validateEventTask.getOtherId(), validateEventTask.getOtherSeq());
		}

		// We need to find an event that could be in 2 places, the intakeMap and the hashgraph. The event will move
		// from intakeMap into the hashgraph, with a very small overlap where it will be in both. This means that it
		// first needs to be looked for in the intakeMap to avoid missing it if the move happens between 2 checks. But
		// we have a preference of using the one from the hashgraph because we are sure it is a valid event.
		EventIntakeTask intake = intakeMap.get(pair);
		EventImpl hashgraph = getEventByCreatorSeq(pair);
		EventIntakeTask parent;

		if (hashgraph != null) {
			// if we did find it in the hashgraph, it is our first choice
			parent = new ValidateEventTask(hashgraph);
		} else {
			parent = intake;
		}
		if (parent == null) {
			// this means we didn't find it anywhere, return null
			return null;
		}

		// if we got the event from the intake queue, then we're not sure if the event will be valid once it's
		// processed, so before the event is added to the hashgraph, its parents will be checked
		if (selfParent) {
			validateEventTask.setSelfParent(parent);
		} else {
			validateEventTask.setOtherParent(parent);
		}

		return parent;
	}

	/**
	 * This method processes event information received from other nodes, and instantiates and event object
	 *
	 * @param validateEventTask
	 * 		the event information based on which an event should be instantiated
	 */
	private void processIntakeEvent(ValidateEventTask validateEventTask) {
		try {
			CryptoFactory.getInstance().digestSync(validateEventTask.getHashedData());

			EventImpl selfParent = null;
			if (validateEventTask.getSelfParent() != null) {
				selfParent = validateEventTask.getSelfParent().getEventWait(validateEventTask);

				// when processing an event received from other nodes,
				// if this ValidateEventTask's selfParent's event return null,
				// we log such exception;
				//
				// this exception is acceptable when reconnect happens;
				// suppose this ValidateEventTask v1's selfParent is v2,
				// if v2 has old otherParent received from a reconnect node,
				// v2.setEventNull() would be called during processIntakeEvent(v2),
				// making V2's event be null.
				// in processIntakeEvent(v1), its self parent's event would return null
				if (selfParent == null) {
					log.debug(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
							"Self parent returned null, event: {}", validateEventTask);
					validateEventTask.setEventNull();
					return;
				}

				// check the claimed generation of the self parent
				if (validateEventTask.getSelfParentGen() != selfParent.getGeneration()) {
					log.error(TESTING_EXCEPTIONS.getMarker(),
							"Claimed generation of self parent does not match for event:({},{})",
							validateEventTask.getCreatorId(), validateEventTask.getCreatorSeq());
					validateEventTask.setEventNull();
					return;
				}

				// check the claimed hash of the self parent
				if (!Arrays.equals(validateEventTask.getSelfParentHash(), selfParent.getBaseHash().getValue())) {
					log.error(TESTING_EXCEPTIONS.getMarker(),
							"Claimed hash of self parent does not match for event:({},{})",
							validateEventTask.getCreatorId(), validateEventTask.getCreatorSeq());
					validateEventTask.setEventNull();
					return;
				}
			}

			EventImpl otherParent = null;
			if (validateEventTask.getOtherParent() != null) {
				otherParent = validateEventTask.getOtherParent().getEventWait(validateEventTask);

				// when processing an event received from other nodes,
				// if this ValidateEventTask's otherParent's event return null,
				// we log such exception;
				//
				// this exception is acceptable when reconnect happens;
				// if this ValidateEventTask v1's otherParent v2 is sent from a reconnect node, and
				// v2.setEventNull() has called during processIntakeEvent(v2) because of stale events,
				// making v2's event be null.
				// in processIntakeEvent(v1), its other parent's event would return null
				if (otherParent == null) {
					log.debug(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
							"Other parent returned null, event: {}", validateEventTask);
					validateEventTask.setEventNull();
					return;
				}

				// check the claimed generation of the other parent
				if (validateEventTask.getOtherParentGen() != otherParent.getGeneration()) {
					log.error(TESTING_EXCEPTIONS.getMarker(),
							"Claimed generation of other parent does not match for event:({},{})",
							validateEventTask.getCreatorId(), validateEventTask.getCreatorSeq());
					validateEventTask.setEventNull();
					return;
				}

				// check the claimed hash of the other parent
				if (!Arrays.equals(validateEventTask.getOtherParentHash(), otherParent.getBaseHash().getValue())) {
					log.error(TESTING_EXCEPTIONS.getMarker(),
							"Claimed hash of other parent does not match for event:({},{})",
							validateEventTask.getCreatorId(), validateEventTask.getCreatorSeq());
					validateEventTask.setEventNull();
					return;
				}
			}

			// Ignore any event that claims to be created before (or at the same time as) its self-parent.
			// This forces a given member to have a monotonically increasing series of timestamps.
			// This will be true for an honest computer running this code, because it has a check to
			// ensure each timestamp is at least 1 nanosecond later than the previous one, even if
			// the computer's clock is changed so that it says an earlier time.
			if (selfParent != null && !validateEventTask.getTimeCreated().isAfter(selfParent.getTimeCreated())) {
				log.debug(TESTING_EXCEPTIONS.getMarker(),
						"{} Event timeCreated ERROR creatorId:{}, creatorSeq:{}, otherId:{}, otherSeq:{}, event " +
								"created:{}, parent created:{}",
						selfId, validateEventTask.getCreatorId(),
						validateEventTask.getCreatorSeq(), validateEventTask.getOtherId(),
						validateEventTask.getOtherSeq(),
						validateEventTask.getTimeCreated().toString(),
						selfParent.getTimeCreated().toString());
				validateEventTask.setEventNull();
				return;
			}

			// Sum the total size of all transactions about to be included in this event
			int eventTransSize = 0;
			for (Transaction t : validateEventTask.getTransactions()) {
				eventTransSize += t.size();
			}

			// Ignore & log if we have encountered a transaction larger than the limit
			// This might be due to a malicious node in the network
			if (eventTransSize > Settings.maxTransactionBytesPerEvent) {
				log.error(TESTING_EXCEPTIONS.getMarker(),
						"Event Limit (id,seq): maxTransactionBytesPerEvent exceeded by event ({},{}) " +
								" with a total size of {} bytes",
						validateEventTask.getCreatorId(),
						validateEventTask.getCreatorSeq(),
						eventTransSize);
				validateEventTask.setEventNull();
				return;
			}

			// when processing an event received from other nodes,
			// if this ValidateEventTask's selfParent's event is old,
			// and otherParent's event is null or old, we log such exception;
			//
			// this exception is acceptable when reconnect happens;
			// a ValidateEventTask sent from a reconnect node might have old parents.
			if (selfParent != null && isOldEvent(selfParent) && // self parent is old
					(otherParent == null || isOldEvent(otherParent))) { // other parent is null or old
				// this event will have an old round created, we will discard it
				log.error(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
						"Error: Received an event {} whose selfParent's event is old, " +
								"and otherParent's event is null or old, discarding it.",
						validateEventTask);
				validateEventTask.setEventNull();
				return;
			}

			// Event is only instantiated in two places.
			// Events received in syncs are instantiated here.
			// New events created by self are instantiated in createNewEvent().
			EventImpl event = validateEventTask.createEventFromInfo(selfParent, otherParent);
			validateEventTask.setEvent(event);

			if (validateEventTask.getSignature() != null) {// only record this time for events received, not the ones
				// created
				platform.getStats().avgCreatedReceivedTime.recordValue(
						event.getTimeCreated().until(event.getTimeReceived(),
								ChronoUnit.NANOS) / 1_000_000_000.0);
			}
			log.debug(EVENT_SIG.getMarker(),
					"event signature is about to be verified. creatorId={} creatorSeq={} otherId={} otherSeq={} " +
							"hash={}",
					event.getCreatorId(), event.getCreatorSeq(),
					event.getOtherId(), event.getOtherSeq(), event.getBaseHash());
			// check the signature, if it hasn't already been checked
			if (!Settings.verifyEventSigs) {
				// if we aren't verifying signatures, then say they're all valid
				validateEventTask.setEventValidity(true);
			} else {
				// we are verifying signatures, but this event hasn't been verified yet
				PublicKey publicKey = getAddressBook()
						.getAddress(event.getCreatorId()).getSigPublicKey();
				boolean valid = Crypto.verifySignature(event.getBaseHash().getValue(), event.getSignature(), publicKey);
				validateEventTask.setEventValidity(valid);

				// the signature failed verification, so discard the event and don't record it
				if (!valid) {
					final EventImpl finalEvent = event;
					final byte[] signatureCopy = finalEvent.getSignature();
					log.error(EXCEPTION.getMarker(),
							"failed the signature check {} with sig \n     {} and hash \n     {}",
							() -> finalEvent,
							() -> Arrays.toString(signatureCopy),
							() -> Arrays.toString(finalEvent.getBaseHash().getValue()));
					platform.getStats().badEventsPerSecond.cycle();
					event.clear();
					return;
				}
			}

			// Expand signatures for events received from sync operations
			// Additionally, we should enqueue any signatures for verification
			// Signature expansion should be the last thing that is done. It can be fairly time consuming, so we don't
			// want to delay the verification of an event for it because other events depend on this one being valid.
			// Furthermore, we don't want to validate signatures contained in an event that is invalid.
			handleSignatureExpansion(validateEventTask.getTransactions());

		} catch (Exception e) {
			log.error(EXCEPTION.getMarker(), "Error while processing intake event", e);
		}
	}

	/**
	 * Creates a new event based on the information supplied
	 *
	 * @param createEventTask
	 * 		the information used to create an event
	 */
	private void createNewEvent(CreateEventTask createEventTask) {
		try {
			EventIntakeTask selfParentInfo = createEventTask.getSelfParent();
			EventIntakeTask otherParentInfo = createEventTask.getOtherParent();

			EventImpl otherParent;
			// If we can't find the event info, or the event is not valid, we take a valid one from the hashgraph
			if (otherParentInfo == null || !otherParentInfo.isValidWait(createEventTask)) {
				long otherId = createEventTask.getOtherId();
				otherParent = (otherId < 0
						|| otherId >= lastSeq.length()) ? null
						: getEventByCreatorSeq(otherId,
						lastSeq.get((int) otherId));

				log.info(CREATE_EVENT.getMarker(),
						"event {} 's otherParentInfo is null or not valid. Choosing as other parent: {}, {}",
						createEventTask, otherParent, otherParent == null ? "" :
								otherParent.toString());

				// isValidWait() returns false when setEventNull() has been called on this task; or
				// the event's signature is invalid
				if (otherParentInfo != null) {
					// if its event is null, we log an exception;
					// this exception is acceptable when reconnect happens,
					// because setEventNull() might be called because of stale events;
					if (otherParentInfo.getEvent() == null) {
						log.error(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
								"event {} has otherParent {} whose event is null. Choosing as other parent: {}",
								createEventTask,
								otherParentInfo,
								otherParent);
					} else {
						// if its event is not null, which means the signature is invalid, we log an exception
						log.error(EXCEPTION.getMarker(),
								"event {} has invalid otherParent {}. Choosing as other parent: {}",
								createEventTask,
								otherParentInfo,
								otherParent);
					}
				}

				// the initial other parent won't be used, so we set it to null
				createEventTask.setOtherParent(null);
				otherParentInfo = null;
			} else {
				otherParent = otherParentInfo.getEventWait(createEventTask);
			}

			// give the app one last chance to create a non-system transaction and give the platform
			// one last chance to create a system transaction
			platform.preEvent();

			EventImpl selfParent;
			long creatorSeq = 0;
			if (selfParentInfo == null || createEventTask.isCompensatingEvent()) {
				selfParent = null;
			} else {
				selfParent = selfParentInfo.getEventWait(createEventTask);
				creatorSeq = selfParent.getCreatorSeq() + 1;
			}

			if (otherParent == null && selfParent != null && isOldEvent(selfParent)) {
				// This is introduced as a fix for problems seen while recovering from the mainnet hardware
				// crash on 3 June 2019. Then, 3 out of 10 nodes went offline (due to hardware problems) and had only
				// old events in the state. When the nodes started back up,
				// nodes that previously crashed synced with other nodes that crashed. This created events where both
				// parents are old, and these events could not be entered into the hashgraph on the nodes that created
				// them, and they were not gossipped out. This fix prevents these events from being created.
				// this exception is acceptable when more than one nodes reconnects, after the last events created by
				// them have been stale.
				log.error(EXCEPTION.getMarker(),
						"New event on node {} has both old parents, will not be created",
						selfId);
				createEventTask.setEvent(selfParent);
				// we set ourselves to started when we start creating an event, if we don't create one, then we need
				// to reverse those values otherwise the network might get stuck
				nodeNotStarted(selfId.getId()); // selfId assumed to be main
				return;
			}

			long otherSeq = (otherParent == null) ? -1 : otherParent.getSeq();
			// get all the new transactions waiting to be put into an event, if any,
			// and remove from transLists in EventFlow
			Transaction[] transactions = platform.getEventFlow()
					.pollTransListsForEvent();

			Instant timeCreated = Instant.now();
			// null means the constructor will make a sig, not check it
			byte[] signature = (byte[]) null;

			// check all the system transactions that are put in an Event. if there is a
			// SYS_TRANS_STATE_SIG_FREEZE transaction, notify the platform to freeze event creation
			for (Transaction trans : transactions) {
				if (trans.isSystem() && trans.getContents(
						0) == TransactionConstants.SYS_TRANS_STATE_SIG_FREEZE) {
					platform.getFreezeManager().setEventCreationFrozen(true);
					break;
				}
			}

			// Ensure that events created by self have a monotonically increasing creation time.
			// This is true when the computer's clock is running normally.
			// If the computer's clock is reset to an earlier time, then the Instant.now() call
			// above may be earlier than the self-parent's creation time. In that case, it
			// advances to several nanoseconds later than the parent. If the clock is only set back
			// a short amount, then the timestamps will only slow down their advancement for a
			// little while, then go back to advancing one second per second. If the clock is set
			// far in the future, then the parent is created, then the clock is set to the correct
			// time, then the advance will slow down for a long time. One solution for that is to
			// generate no events for enough rounds that the parent will not exist (will be null
			// at this point), and then the correct clock time can be used again. (Assuming this
			// code
			// has implemented nulling out parents from extremely old rounds).
			// If event x is followed by y, then y should be at least n nanoseconds later than x,
			// where n is the number of transactions in x (so each can have a different time),
			// or n=1 if there are no transactions (so each event is a different time).
			{
				long n = (selfParent == null
						|| selfParent.getTransactions() == null
						|| selfParent.getTransactions().length == 0) //
						? 1
						: selfParent.getTransactions().length;
				Instant nextTime = selfParent == null //
						? null
						: selfParent.getTimeCreated().plusNanos(n);
				if (selfParent != null
						&& timeCreated.isBefore(nextTime)) {
					timeCreated = nextTime;
				}
			}

			Address creator = getAddressBook().getAddress(selfId.getId());// selfId assumed to be main

			long selfParentGen = selfParent != null ? selfParent.getGeneration() : -1;
			long otherParentGen = otherParent != null ? otherParent.getGeneration() : -1;
			byte[] selfParentHash = selfParent != null ? selfParent.getBaseHash().getValue() : null;
			byte[] otherParentHash = otherParent != null ? otherParent.getBaseHash().getValue() : null;

			BaseEventHashedData hashedData = new BaseEventHashedData(
					creator.getId(),
					selfParentGen,
					otherParentGen,
					selfParentHash,
					otherParentHash,
					timeCreated,
					transactions
			);
			// creating a new event is by self has 2 CPU intensive parts to it, hashing and signing. The event
			// that will be created after another event needs its hash, but not its signature. We can reduce the
			// waiting time by splitting this into a 2 part process and making the hash available to the next event
			// while creating the signature in parallel
			CryptoFactory.getInstance().digestSync(hashedData);
			signature = platform.sign(hashedData.getHash().getValue());
			BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
					creatorSeq,
					createEventTask.getOtherId(),
					otherSeq,
					signature
			);

			// Event is only instantiated in two places.
			// New events created by self are instantiated here.
			// Events received in syncs are instantiated in processIntakeEvent.
			EventImpl event = new EventImpl(hashedData, unhashedData, selfParent, otherParent);

			createEventTask.setEvent(event);

			platform.getStats().eventsCreatedPerSecond.cycle();

			// the following two will be null if the given parent doesn't exist
			final Address creatorCopy = creator;
			final long creatorSeqCopy = creatorSeq;
			final EventImpl otherParentCopy = otherParent;
			log.debug(CREATE_EVENT.getMarker(), "{}",
					() -> (String.format(
							"EventFlow (id,seq): about to create (%d,%d) with other parent (%d,%d)",
							creatorCopy.getId(), creatorSeqCopy,
							otherParentCopy == null ? -1
									: otherParentCopy.getCreatorId(),
							otherParentCopy == null ? -1
									: otherParentCopy.getSeq())));


			// Signature expansion should be the last thing that is done. It can be fairly time consuming, so we don't
			// want to delay the creation of an event or its adding to the hashgraph.
			handleSignatureExpansion(transactions);

		} catch (Exception e) {
			log.error(EXCEPTION.getMarker(), "Error while creating new event", e);
		}
	}

	/**
	 * Poll a singe EventInfo from intake Queue and check validity and add to hashgraph if valid
	 */
	void intakeQueueHandler() throws InterruptedException {
		EventIntakeTask eventIntakeTask;
		try {
			// take the event info from the queue, waiting in necessary
			eventIntakeTask = intakeQueue.take();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			// propagate the exception to the caller so that pollIntakeQueue would stop
			throw (e);
		}
		// start measuring the time once we got the event
		long time = System.nanoTime();

		//Moving validation to inline model
		if (eventIntakeTask instanceof CreateEventTask) {
			this.createNewEvent((CreateEventTask) eventIntakeTask);
		} else {
			this.processIntakeEvent((ValidateEventTask) eventIntakeTask);
		}

		EventImpl event = eventIntakeTask.getEventWait(null);

		// check if this EventIntakeTask is valid,
		// if not, clear the task and clear the event when it is not null
		if (!isValidEventIntakeTask(eventIntakeTask)) {
			clearIntakeEventInfo(eventIntakeTask);
			if (event != null) {
				event.clear();
			}
			return;
		}

		///////// partially record the event in several ways:
		// add to receivedEvents so we can check for duplicates in the future
		// add it to the hashgraph.eventsByCreator list of lists (to find by creator and seq)
		// call hashgraph.incrementEventSeq if I'm the creator (so we know seq for self new events)

		log.debug(SYNC.getMarker(), "{} sees {}", platform.getSelfId(), event);

		// handle system transaction in this event, record stats
		handleSystemTxAndRecordStats(event);

		if (!isEmptyEvent(event) && !isCreatedBySelf(event)) {
			// stateCurr / stateWork don't need the empty events.
			// and they don't need events by self (since those transactions are in
			// transLists)
			platform.getEventFlow().forCurrPut(event);
		}

		log.debug(SYNC.getMarker(),
				"record event is about to record: {} last seq known for each creator: {} ",
				() -> EventUtils.toShortString(event), () -> Arrays.toString(getLastSeqByCreator()));

		// add to hashgraph, and if any older events become consensus, then this will
		// acquire the appropriate lock and add them to forCons
		consRecordEvent(event);

		// once the event has been added to the hashgraph map, and its parents have been checked, we can
		// remove the eventInfo object from the map and clear its parents
		clearIntakeEventInfo(eventIntakeTask);

		time = System.nanoTime() - time; // nanoseconds spent adding to hashgraph
		platform.getStats().timeFracAdd.update(((double) time) / SEC_TO_NS);
	}

	/**
	 * check whether this event doesn't contain any transactions
	 */
	private boolean isEmptyEvent(final EventImpl event) {
		return event.getTransactions() == null || event.getTransactions().length == 0;
	}

	/**
	 * check whether this event is created by current node
	 */
	private boolean isCreatedBySelf(final EventImpl event) {
		return platform.getSelfId().equalsMain(event.getCreatorId());
	}

	/**
	 * check if an EventIntakeTask is valid
	 */
	private boolean isValidEventIntakeTask(final EventIntakeTask eventIntakeTask) {
		EventImpl event = eventIntakeTask.getEventWait(null);

		// if the event is null, log an exception
		// this exception is acceptable when reconnect happens;
		// because when reconnect happens, in processIntakeEvent(validateEventTask),
		// validateEventTask.setEventNull() might be called
		if (event == null) {
			log.error(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
					"Error, null event in intake queue: {}",
					eventIntakeTask);
			return false;
		}

		// If beta mirror node logic is enabled and the event originated from a node known to have a zero stake
		// then we should discard this event and not even worry about validating the signature in order
		// to prevent a potential DoS or DDoS attack from a zero stake node
		if (Settings.enableBetaMirror
				&& platform.isZeroStakeNode(event.getCreatorId())) {
			log.debug(BETA_MIRROR_NODE.getMarker(),
					"Event Intake: Received an event from a zero stake node [ nodeId = {}, eventSeq = {} ]",
					event::getCreatorId, eventIntakeTask::getCreatorSeqPair);
			return false;
		}

		// it is invalid when this ValidateEventTask.setEventNull() has been called, or
		// when the event's signature is verified as invalid;
		// since we have checked event is null or not, here if isValidWait returns false,
		// it means the signature is invalid, so it should be log as an error
		// if the event is not valid, discard it;
		if (!eventIntakeTask.isValidWait(null)) {
			log.error(EXCEPTION.getMarker(), "Error, invalid event in intake queue: {}",
					eventIntakeTask);
			return false;
		}

		// check if parents of this EventIntakeTask are valid
		if (!areParentsOfEventIntakeTaskValid(eventIntakeTask)) {
			return false;
		}

		// if this is an event we already have
		if (isDuplicateEvent(event)) {
			platform.getStats().duplicateEventsPerSecond.cycle();
			platform.getStats().avgDuplicatePercent.recordValue(100); // move toward 100%
			return false; // then ignore the event, so it will soon be garbage collected
		} else {
			platform.getStats().avgDuplicatePercent.recordValue(0); // move toward 0%
		}
		return true;
	}

	/**
	 * check if we already know this event
	 */
	private boolean isDuplicateEvent(final EventImpl event) {
		return event.getSeq() <= lastSeq.get((int) event.getCreatorId());
	}

	/**
	 * check whether 2 parents of an EventIntakeTask are both valid
	 *
	 * @param eventIntakeTask
	 * @return
	 */
	private boolean areParentsOfEventIntakeTaskValid(final EventIntakeTask eventIntakeTask) {
		return isValidParentEventIntakeTask(eventIntakeTask, eventIntakeTask.getSelfParent(),
				true) && isValidParentEventIntakeTask(eventIntakeTask, eventIntakeTask.getOtherParent(),
				false);
	}

	/**
	 * check whether a parent of an EventIntakeTask is valid
	 *
	 * @param eventIntakeTask
	 * 		an EventIntakeTask
	 * @param parentEventIntakeTask
	 * 		a parent of the EventIntakeTask
	 * @param isSelfParent
	 * 		whether this parent is selfParent or otherParent
	 * @return if this parent is valid return true, otherwise return false
	 */
	private boolean isValidParentEventIntakeTask(final EventIntakeTask eventIntakeTask,
			final EventIntakeTask parentEventIntakeTask, final boolean isSelfParent) {
		// isValidWait() returns false when setEventNull() has been called on this task; or
		// the event's signature is invalid
		if (parentEventIntakeTask != null && !parentEventIntakeTask.isValidWait(eventIntakeTask)) {
			// if its event is null, we log an exception,
			// this exception is acceptable when reconnect happens,
			// because setEventNull() might be called because of old events;
			if (parentEventIntakeTask.getEvent() == null) {
				log.error(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
						"Error, invalid {}(has null event) for event in intake queue: {}",
						() -> isSelfParent ? "selfParent" : "otherParent",
						() -> eventIntakeTask);
			} else {
				// if its event is not null, which means the signature is invalid, we log an exception
				log.error(EXCEPTION.getMarker(),
						"Error, invalid {} for event in intake queue: {}",
						() -> isSelfParent ? "selfParent" : "otherParent",
						() -> eventIntakeTask);
			}
			return false;
		}
		return true;
	}

	/**
	 * handles system transactions in this event, and record stats for all transactions in this event
	 *
	 * @param event
	 * 		the event to be added to the hashgraph
	 */
	private void handleSystemTxAndRecordStats(final EventImpl event) {
		Transaction[] trans = event.getTransactions();
		int numTrans = (trans == null ? 0 : trans.length);

		// we have already ensured this isn't a duplicate event, so record all the stats on it:

		// count the unique events in the hashgraph
		platform.getStats().eventsPerSecond.cycle();
		// count the bytes in the transactions, and bytes per second, and transactions per event
		// for both app transactions and system transactions.
		// Handle system transactions
		int appSize = 0;
		int sysSize = 0;
		int numAppTrans = 0;
		int numSysTrans = 0;
		for (int i = 0; i < numTrans; i++) {
			if (trans[i].isSystem()) {
				numSysTrans++;
				sysSize += trans[i].getLength();
				platform.getStats().avgBytesPerTransactionSys
						.recordValue(trans[i].getLength());

				// All system transactions are handled twice, once with consensus false, and once with true.
				// This is the first time a system transaction is handled while its consensus is not yet
				// known. The second time it will be handled is in EventFlow.doCons()
				platform.handleSystemTransaction(event.getCreatorId(), false,
						event.getTimeCreated(),
						event.getTimeCreated().plusNanos(i), trans[i], null);
			} else {
				numAppTrans++;
				appSize += trans[i].getLength();
				platform.getStats().avgBytesPerTransaction
						.recordValue(trans[i].getLength());
			}
		}
		platform.getStats().avgTransactionsPerEvent.recordValue(numAppTrans);
		platform.getStats().avgTransactionsPerEventSys.recordValue(numSysTrans);
		platform.getStats().bytesPerSecondTrans.update(appSize);
		platform.getStats().bytesPerSecondSys.update(sysSize);
		// count each transaction within that event (this is like calling cycle() numTrans times)
		platform.getStats().transactionsPerSecond.update(numAppTrans);
		platform.getStats().transactionsPerSecondSys.update(numSysTrans);
	}

	/**
	 * Once the processing is done for an EventInfo object, remove it from the map and clear its parents
	 *
	 * @param validateEventTask
	 * 		the invalid event to be cleared
	 */
	private void clearIntakeEventInfo(EventIntakeTask validateEventTask) {
		if (!validateEventTask.isSelfEvent()) {
			// the event will only be in the intake map if it's a received event
			intakeMap.remove(validateEventTask.getCreatorSeqPair());
		}
		// the parents should be cleared so that these objects can be garbage collected
		validateEventTask.clearParents();
	}

	/** Expand signatures for the given array of transactions */
	private void handleSignatureExpansion(Transaction[] transactions) {
		// Expand signatures for the given transactions
		// Additionally, we should enqueue any signatures for verification
		final long startTime = System.nanoTime();
		final List<TransactionSignature> signatures = new LinkedList<>();

		double expandTime = 0;

		for (Transaction t : transactions) {
			try {
				if (!t.isSystem()) {
					final long expandStart = System.nanoTime();
					if (platform.getEventFlow().getConsensusState() != null) {
						platform.getEventFlow().getConsensusState().expandSignatures(t);
					}
					final long expandEnd = (System.nanoTime() - expandStart);
					expandTime += expandEnd / 1_000_000D;
				}

				List<TransactionSignature> sigs = t.getSignatures();

				if (sigs != null && !sigs.isEmpty()) {
					signatures.addAll(sigs);
				}
			} catch (Exception ex) {
				log.error(EXCEPTION.getMarker(),
						"expandSignatures threw an unhandled exception", ex);
			}
		}

		platform.getCryptography().verifyAsync(signatures);

		final double elapsedTime = (System.nanoTime() - startTime) / 1_000_000D;
		CryptoStatistics.getInstance().setPlatformSigIntakeValues(elapsedTime, expandTime);
//		log.debug(Settings.ADV_CRYPTO_SYSTEM,
//				String.format(
//						"Adv Crypto Subsystem: Hashgraph::handleSignatureExpansion() execution time was %f " +
//								"milliseconds out of which %f milliseconds was spent in the " +
//								"SwirldsState::expandSignatures() method. ",
//						elapsedTime, expandTime));
	}

	/**
	 * dump EventInfo in intake queue
	 */
	void dumpIntakeQueue() {

		Iterator<EventIntakeTask> it = intakeQueue.iterator();
		while (it.hasNext()) {
			log.info(INTAKE, "intakeQueue {}", it.next().toString());
		}
	}

	void compensateForStaleEvent(final NodeId otherId, final AtomicLongArray otherSeqByCreator) {
		final long[] selfSeqByCreator = getLastSeqByCreator();
		final long selfSeq = selfSeqByCreator[selfId.getIdAsInt()];
		final long otherSeq = otherSeqByCreator.get(selfId.getIdAsInt());

		final long delta = otherSeq - selfSeq;

		if (selfSeq == -1) { // Special case when node has no saved states
			return;
		}

		if (delta == 0) { // No delta, compensation not necessary
			return;
		}

		if (delta < 0) { // We are ahead of the peer, compensation not necessary
			return;
		}

		if (delta > Settings.syncStaleEventCompThreshold) {
			log.debug(RECONNECT.getMarker(),
					"Failed to compensate for stale events during gossip due to delta exceeding threshold ( selfId = " +
							"{}, otherId = {}, selfSeq = {}, otherSeq = " +
							"{}, delta = {}, threshold = {} )",
					selfId, otherId, selfSeq, otherSeq, delta, Settings.syncStaleEventCompThreshold);
			return;
		}

		lastSeq.set(selfId.getIdAsInt(), otherSeq);
		log.debug(RECONNECT.getMarker(),
				"Compensating for stale events during gossip ( selfId = {}, otherId = {}, selfSeq = {}, otherSeq = " +
						"{}, delta = {} )",
				selfId, otherId, selfSeq, otherSeq, delta);

		log.debug(SYNC.getMarker(), "{} created delta compensating event for sync otherId:{}", selfId, otherId);
		platform.getHashgraph().createEvent(otherId.getId(), true);
	}
}
