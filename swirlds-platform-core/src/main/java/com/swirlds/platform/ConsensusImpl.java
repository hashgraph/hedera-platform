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

import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.internal.CreatorSeqPair;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.stats.ConsensusStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import static com.swirlds.logging.LogMarker.ADD_EVENT;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;
import static com.swirlds.logging.LogMarker.STARTUP;

/**
 * All the code for calculating the consensus for events in a hashgraph. This calculates
 * the consensus timestamp and consensus order, according to the hashgraph consensus algorithm.
 *
 * Every method in this file is private, except for some getters and the addEvent method. Therefore,
 * if care is taken so that only one thread at a time can be in the call to addEvent, then only one
 * thread at a time will be anywhere in this is class (except for the getters). None of the variables
 * are volatile, so calls to the getters by other threads may not see effects of addEvent immediately.
 *
 * The consensus order is calculated incrementally: each time a new event is added to the hashgraph, it
 * immediately finds the consensus order for all the older events for which that is possible.
 **/
public class ConsensusImpl implements Consensus {
	private static final Logger log = LogManager.getLogger();
	// ------------------------ Variable passed to the constructor ------------------------
	/** the member ID of the member running the platform using this hashgraph */
	private final NodeId selfId;

	/** the only address book currently, until address book changes are implemented */
	private final AddressBook addressBook;

	/** returns the stats object */
	private final Supplier<ConsensusStats> statsSupplier;

	/** a method that accepts minimum generation info */
	private final BiConsumer<Long, Long> minGenConsumer;

	/**
	 * the triple hash list for each round. In this class, it's only used in the getter. Deleted when round
	 * discarded
	 */
	private final Map<Long, List<List<Hash>>> hashLists = new HashMap<>(); //null means none are known


	// -----------------------------------------------------------------------------------------------------------------

	//////////////// WHERE EVENTS ARE STORED IN THE HASHGRAPH //////////////////////////////////////////////////////////
	// Each event is stored in eventsByCreator, in the list for its creator.
	// Each event is stored in one of the rounds, in its allEvents list, and initially in its
	// notConsensusStaleEvents list (though it is removed from that one when it reaches
	// consensus or becomes stale).
	//
	// If it is a witness, then it is also stored in exactly
	// one of the RoundInfo.witnesses lists, for the appropriate round.
	// If it is, then it might also be in the RoundInfo.famousWitnesses
	// for that same round.
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/** A map of all the events stored in this object */
	private final ConcurrentHashMap<CreatorSeqPair, EventImpl> eventsByCreatorSeq;

	/** an array that keeps the last consensus event by each member. */
	private final AtomicReferenceArray<EventImpl> lastConsEventByMember;

	/**
	 * maximum round number of all events stored in "rounds", or -1 if none. This is the max round created
	 * of all events ever added to the hashgraph.
	 */
	private final AtomicLong maxRound = new AtomicLong(-1);

	/**
	 * minimum round number of all events stored in "rounds", or -1 if none. This may not be the min round
	 * created of all events ever added to the hashgraph, since some of the older rounds may have been
	 * decided and discarded.
	 */
	private final AtomicLong minRound = new AtomicLong(-1);

	/**
	 * "rounds" is a HashMap mapping round number (starts at 1) to a RoundInfo that knows the witnesses etc.
	 * for that round. It includes the max created round of all events ever added to the hashgraph (by
	 * consRecordEvent()), but it may not include very old rounds that have already been decided and
	 * discarded.
	 */
	private final ConcurrentHashMap<Long, RoundInfo> rounds;

	/** fame has been decided for all rounds less than this, but not for this round. */
	private final AtomicLong fameDecidedBelow = new AtomicLong(1);

	/** events to be cleared in the future */
	private final List<EventImpl> toBeCleared = new LinkedList<>();

	/**
	 * Number of events that have reached consensus order. This is used for setting consensus order numbers
	 * in events, so it must be part of the signed state.
	 */
	private final AtomicLong numConsensus = new AtomicLong(0);

	/**
	 * The minimum consensus timestamp for the next event that reaches consensus. This is null if no event
	 * has reached consensus yet. As each event reaches its consensus, its timestamp is moved forward (if
	 * necessary) to be at least this time. And then minTimestamp is moved forward by n nanoseconds, if the
	 * event had n transactions (or n=1 if no transactions).
	 */
	private Instant minTimestamp = null;

	/** all the events in memory that have been added to the hashgraph, and are stale, but do not have consensus */
	private final Queue<EventImpl> staleNotConsensusEvents = new LinkedList<>();

	/** an event with this number is "marked", all others are "unmarked". Used by the ValidAncestorsIterator */
	private int currMark = 1;

	/** the round number passed to setAddressBook the last time it was called. The next call must be 1 greater */
	private long prevRoundSetAddressBook;

	/** the number of coin rounds that have happened so far (used to update the statistics) */
	private long numCoinRounds = 0;

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Public constructors
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructs an empty object (no events) to keep track of elections and calculate consensus.
	 *
	 * @param statsSupplier
	 * 		should return the statistics object
	 * @param minGenConsumer
	 * 		a method that accepts minimum generation info
	 * @param selfId
	 * 		the memberID of the member running this consensus
	 * @param addressBook
	 * 		the global address book, which never changes
	 */
	public ConsensusImpl(Supplier<ConsensusStats> statsSupplier, BiConsumer<Long, Long> minGenConsumer,
			NodeId selfId, AddressBook addressBook) {
		this.statsSupplier = statsSupplier;
		this.minGenConsumer = minGenConsumer;

		this.selfId = selfId;
		// until we implement address book changes, we will just use the use this address book
		this.addressBook = addressBook;

		this.eventsByCreatorSeq = new ConcurrentHashMap<>();

		this.rounds = new ConcurrentHashMap<>();

		this.lastConsEventByMember = new AtomicReferenceArray<>(addressBook.getSize());
		//prevRoundSetAddressBook = ;
	}

	/**
	 * Constructs an object to keep track of elections and calculate consensus. It will read from the given state
	 * to process all its events, and to read and store its lastRoundReceived.
	 *
	 * @param statsSupplier
	 * 		should return the statistics object
	 * @param minGenConsumer
	 * 		a method that accepts minimum generation info
	 * @param selfId
	 * 		the memberID of the member running this consensus
	 * @param addressBook
	 * 		the global address book, which never changes
	 * @param signedState
	 * 		a state to read from
	 */
	public ConsensusImpl(Supplier<ConsensusStats> statsSupplier, BiConsumer<Long, Long> minGenConsumer,
			NodeId selfId, AddressBook addressBook, SignedState signedState) {
		this(statsSupplier, minGenConsumer, selfId, addressBook);

		EventImpl[] events = signedState.getEvents();
		// the first part of the array of events will be old events, they are kept so that we can know what the last
		// event of each member is. we don't need to create rounds for these events or put them in the map, they will
		// only be stored in lastEventByMember
		long roundNew = getLowestFromHighestSequence(events, EventImpl::getRoundCreated);

		for (EventImpl event : events) {
			// this event is loaded from saved state, it should have reached consensus;
			// when an EventImpl is read from saved state, its InternalEventData is empty,
			// we set its `isConsensus` to be true here,
			// so that this event would not be put into forCons queue again
			event.setConsensus(true);

			lastConsEventByMember.set((int) event.getCreatorId(), event);

			if (event.getRoundCreated() < roundNew) {
				// this event wont be stored in rounds, so it should be cleared as soon as we get a new consensus
				// event from this member
				toBeCleared.add(event);
			} else {
				eventsByCreatorSeq.put(new CreatorSeqPair(event.getCreatorId(),
						event.getCreatorSeq()), event);

				// events are store in consensus order, so the last event in consensus order should be
				// incremented by 1 to get the numConsensus
				numConsensus.set(event.getConsensusOrder() + 1);

				RoundInfo roundInfo = rounds.get(event.getRoundCreated());
				if (roundInfo == null) {
					roundInfo = new RoundInfo(event.getRoundCreated(), addressBook.getSize());
					rounds.put(event.getRoundCreated(), roundInfo);

					maxRound.set(Math.max(maxRound.get(), roundInfo.round));
					minRound.set((minRound.get() == -1) ? roundInfo.round
							: Math.min(minRound.get(), roundInfo.round));
				}

				roundInfo.allEvents.add(event);

				if (event.isWitness()) {
					roundInfo.witnesses.add(event);
					roundInfo.numWitnesses++;
					if (event.isFamous()) {
						roundInfo.addFamousWitness(event);
						roundInfo.fameDecided = true;
					}
				}
			}
		}

		// The minTimestamp is just above the last transaction that has been handled
		minTimestamp = signedState.getLastTransactionTimestamp().plusNanos(1);

		fameDecidedBelow.set(signedState.getLastRoundReceived() + 1);
	}


	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Public getters
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	@Override
	public long getLastRoundDecided() {
		return fameDecidedBelow.get();  //not synchronized because it's AtomicLong
	}

	@Override
	public long getMaxRound() {
		return maxRound.get();  //not synchronized because it's AtomicLong
	}

	@Override
	public long getMinRound() {
		return minRound.get();  //not synchronized because it's AtomicLong
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized long getMinGenerationNonAncient() {
		if (getLastRoundDecided() - Settings.state.roundsStale < 0) {
			// if we dont have any stale rounds yet
			return -1;
		}

		if (getLastRoundDecided() - Settings.state.roundsStale < minRound.get()) {
			// this can happen after a restart when loading a state saved with the old code
			for (int i = 0; i < 10; i++) {
				RoundInfo ri = rounds.get(minRound.get());
				if (ri != null) {
					return ri.minGeneration;
				}
			}
		}

		// because LastRoundDecided can change, we try to get the min generation multiple times
		// in practice, we should always get it on the first try
		for (int i = 0; i < 10; i++) {
			RoundInfo ri = rounds.get(getLastRoundDecided() - Settings.state.roundsStale);
			if (ri != null) {
				return ri.minGeneration;
			}
		}
		// in case we don't find it, we throw an exception that will likely never happen
		throw new RuntimeException("Cannot find stale round!");
	}

	@Override
	public synchronized EventImpl[] getAllEvents() {
		ArrayList<EventImpl> all = new ArrayList<>();
		for (long r = minRound.get(); r <= maxRound.get(); r++) {
			RoundInfo info = rounds.get(r); // each element of rounds has its own lock
			if (info != null) {
				all.addAll(info.allEvents); // allEvents has its own lock
			}
		}
		return all.toArray(new EventImpl[0]);
	}

	/**
	 * {@inheritDoc}
	 */
	public synchronized int getNumEvents() {
		int count = 0;
		for (long r = minRound.get(); r <= maxRound.get(); r++) {
			RoundInfo info = rounds.get(r); // each element of rounds has its own lock
			if (info != null)
				count += info.allEvents.size(); // allEvents has its own lock
		}
		return count;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized EventImpl[] getRecentEvents(long minGenerationNumber) {
		ArrayList<EventImpl> recentEvents = new ArrayList<>();

		for(long r = maxRound.get(); r >= minRound.get(); --r) {
			RoundInfo info = rounds.get(r); // each element of rounds has its own lock
			if(info == null)
				// Round has been deleted because expired, so all older rounds are
				// also deleted-as-expired.
				break;

			for(EventImpl e : info.allEvents)
				if(e.getGeneration() >= minGenerationNumber)
					recentEvents.add(e);
		}

		return recentEvents.toArray(new EventImpl[0]);
	}

	@Override
	public synchronized EventImpl getEvent(CreatorSeqPair pair) {
		EventImpl event = eventsByCreatorSeq.get(pair);
		if (event == null) {
			EventImpl lastEvent = lastConsEventByMember.get((int) pair.getCreatorId());
			if (lastEvent != null && lastEvent.getSeq() == pair.getSeq()) {
				return lastConsEventByMember.get((int) pair.getCreatorId());
			}
		}
		return event;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized Queue<EventImpl> getStaleEventQueue() {
		return staleNotConsensusEvents;
	}

	@Override
	public synchronized List<List<Hash>> getWitnessHashes(final long round) {
		return hashLists.get(round);
	}

	//find the minimum number n such that the given array has at at least one element returning each
	//of the numbers n, n+1, n+2, ..., up to the maximum value that is returned.
	static synchronized <T> long getLowestFromHighestSequence(T[] array, ToLongFunction<T> getLong) {
		T[] sorted = array.clone();
		Arrays.sort(sorted, Comparator.comparingLong(getLong));
		long lowest = getLong.applyAsLong(sorted[sorted.length - 1]);
		for (int i = sorted.length - 2; i >= 0; i--) {
			long curr = getLong.applyAsLong(sorted[i]);
			if (lowest - curr > 1) {
				break;
			}
			lowest = curr;
		}
		return lowest;
	}

	/**
	 * Get the RoundInfo for the most recent round that has reached consensus. The caller must not modify anything in
	 * it.
	 *
	 * @return the RoundInfo of the most recent round that has reached consensus (or null, if none)
	 */
	public synchronized RoundInfo getLatestRoundInfo() {
		return rounds.get(fameDecidedBelow.get() - 1);
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Public methods
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	//all non-private methods are synchronized (other than constructors).

	/**
	 * Register the immutable address book resulting from handling all transactions received in the given round. This
	 * address book, which is the result of handling the events with roundReceived==round, will be used for
	 * calculating the roundCreated for all the events that are voting on the fame of witnesses created in round + 2. An
	 * event can vote for multiple previous rounds, so an event might have its roundCreated recalculated multiple times.
	 *
	 * <p>This method must always be called with a round number that is exactly 1 greater than the last time it was
	 * called.
	 * If not, then an exception is thrown.
	 *
	 * <p>It is possible that when this is called, some existing unfrozen events will have their roundCreated
	 * recalculated,
	 * which causes the voting to change, which causes new witnesses to be decided, which causes a round to be decided
	 * that had not been decided before. In that case, this method call will trigger events to reach consensus. The
	 * list of those new consensus events (if any) is returned.
	 *
	 * @param addressBook
	 * 		the address book to be used
	 * @param round
	 * 		the receivedRound of the last consensus events handled to generate addressBook
	 * @return A list of consensus events, or null if no consensus was reached
	 * @throws IllegalArgumentException
	 * 		if round is not exactly 1 more than it was the last time this was called
	 */
	public synchronized List<EventImpl> setAddressBook(AddressBook addressBook,
			long round) throws IllegalArgumentException {
		if (round != prevRoundSetAddressBook + 1) {
			throw new IllegalArgumentException("called with round == " + round
					+ " but it should have been " + (prevRoundSetAddressBook + 1));
		}
		prevRoundSetAddressBook = round;
		return null;
	}

	/**
	 * Add an event to the hashgraph. It must already have been instantiated, checked for being a duplicate
	 * of an existing event, had its signature created or checked.
	 *
	 * <p>This method will add it to the hashgraph and propagate all its effects. So if the consensus order can
	 * now be calculated for an event (which wasn't possible before), then it will do so and return a list of
	 * consensus events.
	 *
	 * <p>It is possible that adding this event will decide the fame of the last witness in a round, and so the
	 * round will become decided, and so a batch of events will reach consensus.  The list of events that reached
	 * consensus (if any) will be returned.
	 *
	 * @param event
	 * 		the event to be added
	 * @param addressBook
	 * 		the address book to be used
	 * @return A list of consensus events, or null if no consensus was reached
	 */
	@Override
	public synchronized List<EventImpl> addEvent(EventImpl event, AddressBook addressBook) {
		// all events that reached consensus because of a single addEvent call, in consensus order
		//List<EventImpl> newConsensusEvents = new LinkedList<>();
		addToEventsByCreator(event);

		// fill in any event.* that can be calculated by looking only at self and parents
		setFastVars(event);

		// find event.roundCreated which is max of parents', plus either 0 or 1
		ArrayList<EventImpl> stronglySeen = new ArrayList<>(); // witnesses this event strongly sees in previous round
		RoundInfo roundInfo = setRoundCreated(event, stronglySeen);
		statsSupplier.get().addedEvent(event);

		// set event.isWitness appropriately, and propagate consensus info
		findIsWitness(event, roundInfo);

		//evaluate this once, to trigger the precalculation and memoization.
		//This makes it act like dynamic programming, and avoids problems with the recursion going too deep for Java
		stronglySeeP(event,event.getCreatorId());

		// check if it's a witness. If so, vote in all elections in the current round, put new consensus events in nce
		final List<EventImpl> newConsensusEvents = vote(event, roundInfo, stronglySeen);

		// finish recording the event in 2 ways
		roundInfo.allEvents.add(event); // this was an event created in this round
		roundInfo.nonConsensusEvents.add(event); // there isn't yet a consensus on this event
		return newConsensusEvents;
	}

	/**
	 * Check if event is a witness, and if it is, make it vote in all elections in its created round. This should be
	 * called on a round R only if it has already been called on round R-1.
	 *
	 * @param event
	 * 		the event that will vote (if it's a witness)
	 * @param roundInfo
	 * 		the RoundInfo for the created round of event.
	 * @param stronglySeen
	 * 		a list of all the witnesses in the previous round that it strongly sees
	 * @return list of all events that reach consensus during this method call, in consensus order (or null if none)
	 */
	private List<EventImpl> vote(EventImpl event, RoundInfo roundInfo,
			ArrayList<EventImpl> stronglySeen) {
		if (!event.isWitness()) { // only witnesses are allowed to vote
			return null;
		}
		List<EventImpl> cons = new LinkedList<>();//all events reaching consensus now, in consensus order
		for (RoundInfo.ElectionRound election = roundInfo.elections; election != null; election =
				election.nextElection) { // for all elections
			if (election.age == 1) {
				// first round of an election. Vote TRUE for self-ancestors of those you firstSee. Don't decide.
				EventImpl w = firstSee(event, election.event.getCreatorId());
				while (w != null && w.getRoundCreated() > event.getRoundCreated() - 1 && w.getSelfParent() != null) {
					w = firstSelfWitnessS(w.getSelfParent());
				}
				election.vote.set(event.getWitnessSeq(), election.event == w);
			} else {
				// either a coin round or normal round, so count votes from witnesses you strongly see
				long yesStake = 0; //total stake of all members voting yes
				long noStake = 0; //total stake of all members voting yes
				for (EventImpl w : stronglySeen) {
					long stake = addressBook.getStake(w.getCreatorId());
					//Collect the vote from w.
					//If w was loaded from a saved state, then it will have a witnessSeq == -1,
					//and it should be treated as if it votes NO on any new elections (because
					//any new witnesses in previous rounds weren't loaded, and so weren't ancestors of it,
					//and so would have a NO vote.
					if (w.getWitnessSeq() < 0 || election.prevRound.vote.get(w.getWitnessSeq())) {
						yesStake += stake;
					} else {
						noStake += stake;
					}
				}
				long totalStake = addressBook.getTotalStake();
				boolean superMajority = Utilities.isSupermajority(yesStake, totalStake)
						|| Utilities.isSupermajority(noStake, totalStake);

				election.vote.set(event.getWitnessSeq(), yesStake >= noStake);
				if ((election.age % Settings.coinFreq) == 0) {
					// a coin round. Vote randomly unless you strongly see a supermajority. Don't decide.
					numCoinRounds++;
					if (!superMajority) {
						if ((election.age % (2
								* Settings.coinFreq)) == Settings.coinFreq) {
							election.vote.set(event.getWitnessSeq(), true); // every other "coin round" is just
							// coin=true
						} else {
							// coin is one bit from signature (LSB of second of two middle bytes)
							election.vote.set(event.getWitnessSeq(), coin(event));
						}
					}
				} else {
					// a normal round. Vote with the majority of those you strongly see.
					// If you strongly see a supermajority one way, then decide that way.
					if (superMajority) {
						//we've decided one famous event. Set it as famous. If that round is now decided, remember the
						// new consensus events
						List<EventImpl> c = setFamous(election.event, rounds.get(election.event.getRoundCreated()),
								election.vote.get(event.getWitnessSeq()));
						if (c != null) {
							cons.addAll(c);
						}
					}
				}
			}
		}
		statsSupplier.get().coinRounds(numCoinRounds);
		return cons.size() == 0 ? null : cons;
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// All methods and inner classes below this line are private
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Set all the variables in the given Event that can be filled in quickly, without looking at the rest
	 * of the hashgraph.
	 *
	 * @param event
	 * 		the event to modify
	 */
	private void setFastVars(EventImpl event) {
		if (event.isCleared()) {
			return; // no need to update discarded events
		}
		event.setWitness(false);
		event.setFameDecided(false);
		event.setFamous(false);
		event.setConsensus(false);
	}

	/**
	 * Set event.roundCreated to the round created (the parent round, plus either 0 or 1). Add to the stronglySeen
	 * list all the witnesses that event can strongly see, in the round before event's round created.
	 *
	 * @param event
	 * 		the event to modify
	 * @param stronglySeen
	 * 		an empty list should be passed in. All the witnesses in the round before this round that event strongly
	 * 		sees will be added to this list
	 * @return the roundInfo for this event's round
	 */
	private RoundInfo setRoundCreated(EventImpl event, ArrayList<EventImpl> stronglySeen) {
		int numMembers = addressBook.getSize();
		long round;

		round(event); //find the round, and store it using event.setRoundCreated()
		for (long m = 0; m < numMembers; m++) {
			EventImpl s = stronglySeeS1(event, m);
			if (s != null) {
				stronglySeen.add(s);
			}
		}
		round = event.getRoundCreated();
		getOrCreateRoundInfo(round - 1); //ensure the roundInfo exists for this round, and the one before it
		return getOrCreateRoundInfo(round);
	}

	/**
	 * Get the RoundInfo for the given round. If it doesn't exist, then create it and register it in the
	 * various data structures that track it. The (possibly new) RoundInfo is returned.
	 *
	 * @param round
	 * 		the round whose RoundInfo should be retrieved (or created if it doesn't exist)
	 * @return the (possibly new) RoundInfo
	 */
	private RoundInfo getOrCreateRoundInfo(long round) {
		RoundInfo roundInfo = rounds.get(round);
		if (roundInfo != null) {
			return roundInfo;
		}
		roundInfo = new RoundInfo(round, addressBook.getSize());
		rounds.put(round, roundInfo);

		maxRound.set(Math.max(maxRound.get(), round));
		minRound.set((minRound.get() == -1) ? round : Math.min(minRound.get(), round));

		// create elections in this round based on the previous one
		RoundInfo oldRoundInfo = rounds.get(round - 1);
		if (oldRoundInfo != null) { // if there is a previous round, use it 2 ways:
			// each election in the previous round continues as an election here
			for (RoundInfo.ElectionRound e = oldRoundInfo.elections; e != null; e = e.nextElection) {
				newElection(e.event, roundInfo, e);
			}
			// each witness in the previous round starts a new election here
			for (EventImpl witness : oldRoundInfo.witnesses) {
				newElection(witness, roundInfo, null);
			}
		}

		return roundInfo;
	}

	/**
	 * Set event.isWitness appropriately. And if it is a witness, propagate the effects. This assumes that
	 * event.roundCreated has already been filled in. And so has event.selfParent.round, if there is a self
	 * parent.
	 *
	 * @param event
	 * 		the event to modify
	 * @param roundInfo
	 * 		the roundInfo for the round this event is created in
	 * @return list of all events that reach consensus during this method call, in consensus order (or null if none)
	 */
	private List<EventImpl> findIsWitness(EventImpl event, RoundInfo roundInfo) {
		if (event.getSelfParent() != null && event.getRoundCreated() == event.getSelfParent().getRoundCreated()) {
			event.setWitness(false);
			event.setWitnessSeq(-1);
			return null;
		}
		// the event is a witness, so mark it as such, and record it.
		event.setWitness(true);
		event.setWitnessSeq(roundInfo.numWitnesses);
		roundInfo.witnesses.add(event); // this is the only place new witnesses are ever added
		roundInfo.numWitnesses++;
		for (RoundInfo.ElectionRound e = roundInfo.elections; e != null; e = e.nextElection) {
			e.vote.add(false); //this new witness will vote in every election in this round
		}

		if (rounds.get(event.getRoundCreated() + 2) != null) {
			// theorem says it can't be famous, so decide it now, with no elections.
			roundInfo.numUnknownFame++;
			// numUnknownFame will be decremented in setFamous(), we need to increment it here so we don't loose
			// track of how many witnesses we need to determine fame for. This was the cause of bug #197
			return setFamous(event, roundInfo, false);
		} else {
			// the theorem doesn't apply, so we can't decide yet
			roundInfo.numUnknownFame++;
			RoundInfo nextRound = rounds.get(roundInfo.round + 1);
			if (nextRound != null) {
				// event is in round R, there is a round R+1, but not an R+2, so create an election in R+1
				newElection(event, nextRound, null);
			}
		}
		return null;
	}

	/**
	 * create a new election for the given witness in the given round, and add it to the various data
	 * structures.
	 *
	 * @param witness
	 * 		the witness that needs another election
	 * @param roundInfo
	 * 		the round that the new election should be added to
	 * @param prevRound
	 * 		the election for the same witness in the previous round (or null if none)
	 * @return the created election
	 */
	private RoundInfo.ElectionRound newElection(EventImpl witness, RoundInfo roundInfo,
			RoundInfo.ElectionRound prevRound) {
		RoundInfo.ElectionRound election = new RoundInfo.ElectionRound(roundInfo,
				getAddressBook().getSize(), witness,
				roundInfo.round - witness.getRoundCreated()); // this is the only place this is called
		election.nextRound = null;
		election.prevRound = prevRound;
		election.prevElection = null;
		election.nextElection = roundInfo.elections;
		for (int i = 0; i < roundInfo.numWitnesses; i++) {
			election.vote.add(false); //all this round's witnesses will vote on fame of witness. Votes init to false.
		}
		if (witness.getFirstElection() == null) {
			witness.setFirstElection(election);
		}
		if (prevRound != null) {
			prevRound.nextRound = election;
		}
		if (roundInfo.elections != null) {
			roundInfo.elections.prevElection = election;
		}
		roundInfo.elections = election;
		return election;
	}

	/**
	 * Set a witness as being famous or not. Then propagate consensus info.
	 *
	 * @param event
	 * 		the witness in the given round
	 * @param roundInfo
	 * 		the roundInfo for the given round (must be same round as event)
	 * @param isFamous
	 * 		is this witness famous?
	 * @return list of all events that reach consensus during this method call, in consensus order (or null if none)
	 */
	private List<EventImpl> setFamous(EventImpl event, RoundInfo roundInfo,
			boolean isFamous) {
		event.setFamous(isFamous);
		event.setFameDecided(true);
		if (isFamous) {
			//remember it as a judge (or don't, if it's a fork that won't be the judge)
			roundInfo.addFamousWitness(event);
		}
		roundInfo.numUnknownFame--;

		// fame is now decided for event, so remove all its elections
		for (RoundInfo.ElectionRound e = event.getFirstElection(); e != null; e = e.nextRound) {
			if (e.prevElection == null) {
				e.roundInfo.elections = e.nextElection;
			} else {
				e.prevElection.nextElection = e.nextElection;
			}
			if (e.nextElection != null) {
				e.nextElection.prevElection = e.prevElection;
			}
		}
		event.setFirstElection(null);

		if (roundInfo.numUnknownFame == 0) {
			// all famous witnesses for this round are now known. None will ever be added again. We know this
			// round has at least one witness, because this method was called. We know they all have fame
			// decided, because this IF statement checked for that. We know the next 2 rounds have events in
			// them, because otherwise we couldn't have decided the fame here.
			// Therefore any new witness added to this round
			// in the future will be instantly decided as not famous. Therefore, the set of famous witnesses
			// in this round is now completely known and immutable. So we can call the following, to record
			// that fact, and propagate appropriately.
			statsSupplier.get().lastFamousInRound(event);
			return setRoundFameDecidedTrue(roundInfo);
		}
		return null;
	}

	/**
	 * Set roundInfo.fameDecided to be true, then propagate consensus info. Setting fameDecided to true
	 * means that the fame of all known witnesses in that round has been decided, and so any new witnesses
	 * discovered in the future will be guaranteed to not be famous.
	 *
	 * Since fame for this round is now decided, it is now possible to decide consensus and time stamps for
	 * events in earlier rounds. If an event is an ancestor of a famous witness, its isFrozen becomes true.
	 * If it's an ancestor of all the famous witnesses, then it reaches consensus. If its isFrozen is still
	 * false, then it may have its roundCreated changed as a result of address book changes due to the effects
	 * of the new consensus events being handled.
	 *
	 * @param roundInfo
	 * 		the round information to modify
	 * @return list of all events that reach consensus during this method call, in consensus order (or null if none)
	 */
	private List<EventImpl> setRoundFameDecidedTrue(RoundInfo roundInfo) {
		//all events that reach consensus during this method call, in consensus order
		List<EventImpl> newConsensusEvents = new LinkedList<>();

		// the current round just had its fame decided. There may have
		// been some later rounds that already had fame decided, but
		// couldn't be used to determine consensus, because this round
		// was still undecided. So calculate consensus now using this
		// round, and using all later rounds that already have fame decided.
		// Note: more witnesses may be added to this round in the future, but
		// they'll all be instantly marked as not famous.
		roundInfo.fameDecided = true;
		long round = roundInfo.round;
		while (fameDecidedBelow.get() == round && roundInfo.fameDecided) {
			minGenConsumer.accept(round, roundInfo.minGeneration);
			findReceivedInRound(roundInfo, newConsensusEvents);
			round++;
			fameDecidedBelow.set(
					round); // all rounds before this round are now decided, and appropriate events marked consensus
			statsSupplier.get().consensusReachedOnRound();
			roundInfo = rounds.get(round);
		}
		delRounds(); // we could delete old rounds more often, but once per new decided round is enough

		//now that a new round reached consensus, some events became ancient, so set the non-consensus ones to stale
		for (long r = minRound.get(); r <= roundInfo.round; r++) {
			RoundInfo info = rounds.get(r); // each element of rounds has its own lock
			if (info != null) {
				for (EventImpl e : info.allEvents) { // allEvents has its own lock
					if (!e.isConsensus() && !e.isStale() && e.getGeneration() < getMinGenerationNonAncient()) {
						staleEvent(e);
					}
				}
			}
		}

		return newConsensusEvents.size() == 0 ? null : newConsensusEvents;
	}

	/**
	 * Find all events that are ancestors of the judges in round roundInfo.round and update them. A
	 * non-consensus event that is an ancestor of all of them should be marked as consensus, and have its consensus
	 * roundReceived  and timestamp set. An event that is an ancestor of at least one should be marked as frozen (so
	 * its roundCreated won't change when the address book changes). Events with very old generations should be marked
	 * as stale or expired, as appropriate. Expired events can be deleted. This should not be called on any round
	 * greater than R until after it has been called on round R.
	 *
	 * @param roundInfo
	 * 		the info for the round with the judges, which is also the round received for these events
	 * 		reaching consensus now
	 * @param newConsensusEvents
	 * 		a (possibly-nonempty) list that will have added to it all events that reach consensus during this
	 * 		method call, adding them in consensus order
	 */
	private void findReceivedInRound(RoundInfo roundInfo, List<EventImpl> newConsensusEvents) {
		byte[] whitening; //an XOR of the signatures of judges in a round, used during sorting
		ArrayList<EventImpl> consensus = new ArrayList<>(); //the newly-consensus events where round received is "round"
		EventImpl[] judges = roundInfo.judges; //all judges for this round
		int numJudges = 0; //number of judges in this round
		long round = roundInfo.round; //the round where we just got consensus on the set of judges
		List<Hash> hashesR0 = new ArrayList<>(); //hashes of judges in round
		List<Hash> hashesR1 = new ArrayList<>(); //hashes of round-1 witnesses that are ancestors of hashesR0
		List<Hash> hashesR2 = new ArrayList<>(); //hashes of round-2 witnesses that are ancestors of hashesR0
		List<List<Hash>> hashes = new ArrayList<>();
		hashes.add(hashesR0);
		hashes.add(hashesR1);
		hashes.add(hashesR2);

		// now is the first time that roundInfo.round and all earlier
		// rounds have fame decided. So set roundReceived for earlier events.
		// Do a recursive search of the hashgraph, without using the Java stack, and being efficient when it's a
		// DAG that isn't a tree.

		// find whitening for round
		Arrays.fill(roundInfo.whitening, (byte) 0);
		for (EventImpl w : judges) {
			if (w != null) {
				numJudges++;
				int mn = Math.min(roundInfo.whitening.length, w.getSignature().length);
				for (int i = 0; i < mn; i++) {
					roundInfo.whitening[i] ^= w.getSignature()[i];
				}
			}
		}
		whitening = roundInfo.whitening;

		// get the minimum generation of famous witnesses for [roundsStale] rounds ago
		// any event with generation less than minGenConsensus is ancient, and will be stale if not already consensus
		long minGenConsensus = 0;
		long staleRound = roundInfo.round - Settings.state.roundsStale;
		if (staleRound < minRound.get()) {
			// this can happen after a restart when loading a state saved with the old code
			staleRound = minRound.get();
		}
		if (staleRound >= 0) {
			minGenConsensus = rounds.get(staleRound).minGeneration;
		}

		ArrayList<EventImpl> staleEvents = new ArrayList<>(); // new stale events in round r
		ArrayList<EventImpl> visited = new ArrayList<>(); //each event visited by iterator from at least one judge

		//for each judge in this round that just decided fame
		for (EventImpl w : roundInfo.judges) {
			//search from every judge that exists
			if (w != null) {
				ValidAncestorsView nonConsensusAncestors = new ValidAncestorsView(w,
						e -> (!e.isConsensus() && !e.isStale()));
				ValidAncestorsView threeRoundAncestors = new ValidAncestorsView(w,
						e -> (e.getRoundCreated() == round - 1 || e.getRoundCreated() == round - 2));
				hashesR0.add(new Hash(w.getBaseHash())); //remember hash of each judge in round
				//find hashes of all ancestors of w that are witnesses in rounds round-1 or round-2
				for (EventImpl event : threeRoundAncestors) {
					if (event.isWitness() && event.getRoundCreated() == round - 1) {
						hashesR1.add(new Hash(event.getBaseHash()));
					} else if (event.isWitness() && event.getRoundCreated() == round - 2) {
						hashesR2.add(new Hash(event.getBaseHash()));
					}
				}
				//walk through all non-consensus, non-stale ancestors of w, using a predicate lambda to check for that
				//for every ancestor of the judge that isn't consensus/stale/expired yet
				for (EventImpl event : nonConsensusAncestors) {
					if (event.getGeneration() < minGenConsensus) {
						// this non-stale, non-consensus event is too old, so it should now be declared stale
						RoundInfo eventRoundInfo = rounds.get(event.getRoundCreated());
						staleEvent(event);
						staleEvents.add(event);
						if (eventRoundInfo == null) {
							// added to figure out issue #2344
							RoundInfo minRound = rounds.get(getMinRound());
							log.error(INVALID_EVENT_ERROR.getMarker(),
									"Judge {} rc:{} gen:{}\n" +
											"has ancestor:\n" +
											"{} rc:{} gen:{} cons:{} stale:{}\n" +
											"which is not stale or consensus and its round is missing!\n" +
											"minGenConsensus:{} minRound:{} minRound.minGeneration:{}\n",
									w.getCreatorSeqPair(), w.getRoundCreated(), w.getGeneration(),
									event.getCreatorSeqPair(), event.getRoundCreated(), event.getGeneration(),
									event.isConsensus(), event.isStale(),
									minGenConsensus, getMinRound(),
									minRound != null ? minRound.minGeneration : null
							);
						} else {
							eventRoundInfo.nonConsensusEvents.remove(event);
						}
						continue;
					}
					if (event.getRecTimes() == null) {
						event.setRecTimes(new ArrayList<>());
						visited.add(event);
					}
					//this is one of the times that will affect the median
					event.getRecTimes().add(nonConsensusAncestors.getTime()); //this is reset to null after this loop

					//if it reached all the judges, then it now has consensus
					if (event.getRecTimes().size() == numJudges) {
						// event has reached consensus, so store it, set consensus timestamp, and set isConsensus to
						// true
						RoundInfo eventRoundInfo = rounds.get(event.getRoundCreated());
						setIsConsensusTrue(event, roundInfo);
						consensus.add(event);
						eventRoundInfo.nonConsensusEvents.remove(event);
					}
				}
			}
		}
		hashLists.put(round, hashes);

		// "consensus" now has all events in history with receivedRound==round
		// there will never be any more events with receivedRound<=round (not even if the address book changes)

		// consensus order is to sort by roundReceived, then consensusTimestamp,
		// then generation, then whitened signature.
		Collections.sort(consensus, (EventImpl e1, EventImpl e2) -> {
			int c;

			//sort by consensus timestamp
			c = (e1.getConsensusTimestamp()
					.compareTo(e2.getConsensusTimestamp()));
			if (c != 0)
				return c;

			//subsort ties by extended median timestamp
			ArrayList<Instant> recTimes1 = e1.getRecTimes();
			ArrayList<Instant> recTimes2 = e2.getRecTimes();
			int size1 = recTimes1.size();
			int size2 = recTimes2.size();

			int m1 = size1 / 2; //middle position of e1 (the later of the two middles, if even length)
			int m2 = size2 / 2; //middle position of e2 (the later of the two middles, if even length)
			int d = -1; //offset from median position to look at
			while (m1 + d >= 0
					&& m2 + d >= 0
					&& m1 + d < size1
					&& m2 + d < size2) {
				c = recTimes1.get(m1 + d).compareTo(recTimes2.get(m2 + d));
				if (c != 0)
					return c;
				d = d < 0 ? -d : -d - 1; //use the median position plus -1, 1, -2, 2, -3, 3, ...
			}
			if (size1 > size2) {
				return 1; //this should never happen. They should be the same length.
			}
			if (size1 < size2) {
				return -1; //this should never happen. They should be the same length.
			}

			//subsort ties by generation
			c = Long.compare(e1.getGeneration(), e2.getGeneration());
			if (c != 0)
				return c;

			//subsort ties by whitened signature
			return Utilities.arrayCompare(e1.getSignature(), e2.getSignature(),
					whitening);
		});


		// Set the consensus number for every event that just became a consensus
		// event. Add more info about it to the hashgraph. Set event.lastInRoundReceived
		// to true for the last event in "consensus".
		setConsensusOrder(consensus);

		for (EventImpl e : consensus) { // add them in consensus order
			lastConsEventByMember.set((int) e.getCreatorId(), e);
			newConsensusEvents.add(e);
		}
		for (EventImpl e : visited) {
			e.setFrozen(true); //never recalculate roundCreated again for an event that was an ancestor of a judge
			e.setRecTimes(null); //reclaim the memory for the list of received times
		}
	}

	/**
	 * Delete the oldest rounds with round number roundToDelete or earlier (and clear the references in
	 * their events). But don't delete any round that still has non-consensus events. Nor any round after
	 * that one. If a round R is deleted, then every event with a received round of R is deleted, by having
	 * all the references in it to other events set to null, so the Java garbage collector can delete what
	 * they reference.
	 */
	private void delRounds() {
		//delete rounds before minRoundNotExpired
		long minRoundNotExpired = getLastRoundDecided() - Settings.state.roundsExpired;
		//events are expired if their generation is less than minGenNotExpired
		long minGenNotExpired = -1;
		RoundInfo expRound = rounds.get(minRoundNotExpired);
		if (expRound != null) {
			minGenNotExpired = expRound.minGeneration;
		}
		roundLoop:
		for (long r = minRound.get(); r < minRoundNotExpired; r++) {
			RoundInfo info = rounds.get(r);
			if (info != null) {
				// we should not delete any round that has an event with a generation >= minGenNotExpired
				// and we should not delete any rounds after that (so there aren't any gaps)
				for (EventImpl e : info.allEvents) {
					if (e.getGeneration() >= minGenNotExpired) {
						break roundLoop;
					}
				}

				// at this point, every event in the round is expired, every witness has fame decided, no
				// elections exist, so this round can be removed
				rounds.remove(r);
				hashLists.remove(r);
				for (EventImpl e : info.allEvents) {
					if (!e.isConsensus() && !e.isStale()) {
						staleEvent(e); //this should be incredibly rare: expiring before being marked stale
					}
					// if the event is the lastConsEventByMember, we might still need it in the future if the node
					// starts generating events again. For this reason, we will not clear it until we get another
					// event from that node
					if (isLastConsEventByMember(e)) {
						toBeCleared.add(e);
					} else {
						// null out the references to other events, so the garbage collector can delete
						// those older events
						e.clear();
					}

					// remove it from the record of all the events in the universe.
					eventsByCreatorSeq.remove(new CreatorSeqPair(
							e.getCreatorId(), e.getCreatorSeq()));
				}
			}
			minRound.set(r + 1);
		}
		// events were added to the toBeCleared array because they were the last event by a member. If this is no
		// longer the case, we can clear this event
		Iterator<EventImpl> it = toBeCleared.iterator();
		while (it.hasNext()) {
			EventImpl next = it.next();
			if (!isLastConsEventByMember(next)) {
				next.clear();
				it.remove();
			}
		}
	}

	/**
	 * Set event.isConsensus to true, set its consensusTimestamp, and record speed statistics.
	 *
	 * @param event
	 * 		the event to modify, with event.getRecTimes() containing all the times judges first saw it
	 * @param receivedRoundInfo
	 * 		information about the round in which event was received
	 */
	private void setIsConsensusTrue(EventImpl event, RoundInfo receivedRoundInfo) {
		if (event.isCleared()) {
			return; // no need to update discarded events
		}
		event.setRoundReceived(receivedRoundInfo.round);
		event.setConsensus(true);

		ArrayList<Instant> times = event.getRecTimes(); //list of when e1 first became ancestor of each judge
		//sort ascending the received times. Used to find the median now, and the extended median later.
		Collections.sort(times);
		// take middle. If there are 2 middle (even length) then use the 2nd (max) of them
		event.setConsensusTimestamp(times.get(times.size() / 2));

		event.setReachedConsTimestamp(Instant.now()); //used for statistics

		statsSupplier.get().consensusReached(event);
	}

	/**
	 * Set event.consensusOrder for every event that just reached consensus, and update the count
	 * numConsensus accordingly. The last event in events is marked as being the last received in its round.
	 * Consensus timestamps are adjusted, if necessary, to ensure that each event in consensus order is
	 * later than the previous one, by enough nanoseconds so that each transaction can be given a later
	 * timestamp than the last.
	 *
	 * @param events
	 * 		the events to set (such that a for(EventImpl e:events) loop visits them in consensus order)
	 */
	private void setConsensusOrder(Collection<EventImpl> events) {
		EventImpl last = null;
		for (EventImpl e : events) {
			last = e;
			e.setConsensusOrder(numConsensus.longValue());
			numConsensus.incrementAndGet();
			// advance this event's consensus timestamp to be at least minTimestamp. Update minTimestamp
			if (minTimestamp != null
					&& e.getConsensusTimestamp().isBefore(minTimestamp)) {
				e.setConsensusTimestamp(minTimestamp);
			}
			minTimestamp = e.getLastTransTime().plusNanos(1);
		}
		if (last != null) {
			last.setLastInRoundReceived(true);
		}
	}

	/**
	 * Record that the given event was created by the member who created it.
	 *
	 * @param evt
	 * 		the event to record
	 */
	private void addToEventsByCreator(EventImpl evt) {
		eventsByCreatorSeq.put(
				new CreatorSeqPair(evt.getCreatorId(), evt.getCreatorSeq()),
				evt);
	}

	/**
	 * Called when an Event is stale
	 *
	 * @param e
	 * 		the stale event
	 */
	private void staleEvent(EventImpl e) {
		e.setStale(true);
		staleNotConsensusEvents.add(e);
	}

	/**
	 * Is the event supplied the last consensus event by its creator
	 *
	 * @param e
	 * 		the event to check
	 * @return true if the event is last consensus event by its creator, false otherwise
	 */
	private boolean isLastConsEventByMember(EventImpl e) {
		EventImpl lastCons = lastConsEventByMember.get((int) e.getCreatorId());
		if (lastCons == null) {
			return false;
		}
		return lastCons.getCreatorSeq() == e.getCreatorSeq();
	}

	private AddressBook getAddressBook() {
		return addressBook;
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Functions from SWIRLDS-TR-2020-01, verified by Coq proof
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * The parent round (1 plus max of parents' rounds) of event x (function from SWIRLDS-TR-2020-01).
	 * This result is not memoized.
	 *
	 * @param x
	 * 		the event being queried
	 * @return the parent round of x
	 */
	private long parentRound(EventImpl x) {
		long round = 0;
		EventImpl sp, op;
		if (x == null) {
			return 0;
		}
		sp = x.getSelfParent();
		if (sp != null) {
			round = round(sp);
		}
		op = x.getOtherParent();
		if (x.getOtherParent() != null) {
			round = Math.max(round, round(op));
		}
		return round;
	}

	/**
	 * The last event created by m that is an ancestor of x (function from SWIRLDS-TR-2020-01).
	 * This has aggressive memoization: the first time it is called with a given x, it immediately calculates and stores
	 * results for all m.
	 * This result is memoized.
	 *
	 * @param x
	 * 		the event being queried
	 * @param m
	 * 		the member ID of the creator
	 * @return the last event created by m that is an ancestor of x, or null if none
	 */
	private EventImpl lastSee(EventImpl x, long m) {
		int numMembers;
		EventImpl sp, op;

		if (x == null) {
			return null;
		}
		if (x.sizeLastSee() != 0) { //return memoized answer, if available
			return x.getLastSee((int) m);
		}
		//memoize answers for all choices of m, then return answer for just this m
		numMembers = getAddressBook().getSize();
		x.initLastSee(numMembers);

		op = x.getOtherParent();
		sp = x.getSelfParent();

		for (int mm = 0; mm < numMembers; mm++) {
			if (x.getCreatorId() == mm) {
				x.setLastSee(mm, x);
			} else if (sp == null && op == null) {
				x.setLastSee(mm, null);
			} else {
				EventImpl lsop = lastSee(op, mm);
				EventImpl lssp = lastSee(sp, mm);
				long lsopGen = lsop == null ? 0 : lsop.getGeneration();
				long lsspGen = lssp == null ? 0 : lssp.getGeneration();
				if ((round(lsop) > round(lssp))
						|| ((lsopGen > lsspGen) && (firstSee(op, mm) == firstSee(sp, mm)))) {
					x.setLastSee(mm, lsop);
				} else {
					x.setLastSee(mm, lssp);
				}
			}
		}
		return x.getLastSee((int) m);
	}

	/**
	 * The witness y created by m that is seen by event x through an event z created by m2 (function from
	 * SWIRLDS-TR-2020-01).
	 * This result is not memoized.
	 *
	 * @param x
	 * 		the event being queried
	 * @param m
	 * 		the creator of y, the event seen
	 * @param m2
	 * 		the creator of z, the intermediate event through which x sees y
	 * @return the event y that is created by m and seen by x through an event by m2
	 */
	private EventImpl seeThru(EventImpl x, long m, long m2) {
		if (x == null) {
			return null;
		}
		if (m == m2 && m2 == x.getCreatorId()) {
			return firstSelfWitnessS(x.getSelfParent());
		}
		return firstSee(lastSee(x, m2), m);
	}

	/**
	 * The witness created by m in the parent round of x that x strongly sees (function from SWIRLDS-TR-2020-01).
	 * This result is memoized.
	 *
	 * This method is called multiple times by both round() and stronglySeeP1(). A measure of the total time spent in
	 * this method gives an indication of how much time is being devoted to what can be thought of as a kind of
	 * generalized dot product (not a literal dot product). So it is timed and it updates the statistic for that.
	 *
	 * @param x
	 * 		the event being queried
	 * @param m
	 * 		the member ID of the creator
	 * @return witness created by m in the parent round of x that x strongly sees, or null if none
	 */
	private EventImpl stronglySeeP(EventImpl x, long m) {
		long t = System.nanoTime(); //Used to update statistic for dot product time
		EventImpl result; //the witness to return (possibly null)

		if (x == null) { //if there is no event, then it can't see anything
			result = null;
		} else if (x.sizeStronglySeeP() != 0) { //return memoized answer, if available
			result = x.getStronglySeeP((int) m);
		} else { //calculate the answer, and remember it for next time
			//find and memoize answers for all choices of m, then return answer for just this m
			int numMembers = getAddressBook().getSize(); //number of members
			long totalStake = addressBook.getTotalStake(); //total stake in existence
			EventImpl sp = x.getSelfParent(); //self parent
			EventImpl op = x.getOtherParent(); //other parent
			long prx = parentRound(x); //parent round of x
			long prsp = parentRound(sp); //parent round of self parent of x
			long prop = parentRound(op); //parent round of other parent of x

			x.initStronglySeeP(numMembers);

			for (int mm = 0; mm < numMembers; mm++) {
				if (stronglySeeP(sp, mm) != null && prx == prsp) {
					x.setStronglySeeP(mm, stronglySeeP(sp, mm));
				} else if (stronglySeeP(op, mm) != null && prx == prop) {
					x.setStronglySeeP(mm, stronglySeeP(op, mm));
				} else {
					EventImpl st = seeThru(x, mm, mm); //the canonical witness by mm that is seen by x thru someone else
					if (round(st) != prx) { //ignore if the canonical is in the wrong round, or doesn't exist
						x.setStronglySeeP(mm, null);
					} else {
						long stake = 0;
						for (long m3 = 0; m3 < numMembers; m3++) {
							if (seeThru(x, mm, m3) == st) {  //only count intermediates that see the canonical witness
								stake += addressBook.getStake(m3);
							}
						}
						if (Utilities.isSupermajority(stake, totalStake)) { //strongly see supermajority of
							// intermediates
							x.setStronglySeeP(mm, st);
						} else {
							x.setStronglySeeP(mm, null);
						}
					}
				}
			}
			result = x.getStronglySeeP((int) m);
		}
		t = System.nanoTime() - t; // nanoseconds spent doing the dot product
		statsSupplier.get().dotProductTime(t);
		return result;
	}

	/**
	 * The round-created for event x (first round is 1), or 0 if x is null (function from SWIRLDS-TR-2020-01).
	 * It also stores the round number with x.setRoundCreated().
	 * This result is memoized.
	 *
	 * If the event has a hash in the hash lists given to the ConsensusImpl constructor, then the roundCreated is set
	 * to that round number, rather than calculating it from the parents.
	 *
	 * If a parent has a round of -1, that is treated as negative infinity. So if all parents are -1, then this one
	 * will also be -1.
	 *
	 * @param x
	 * 		the event being queried
	 * @return the round-created for event x, or 0 if x is null
	 */
	private long round(EventImpl x) {
		int numMembers = getAddressBook().getSize(); //number of members that are voting, with ID 0 to numMembers-1
		EventImpl op, sp; //other parent, self parent
		long rop, rsp, stake; //roundCreated of other parent, roundCreated of self parent, sum of stake involved

		if (x == null) {
			return 0;
		}
		if (x.getRoundCreated() > 0) {
			return x.getRoundCreated();
		}
		//calculate the round, memoize it, and return it

		op = x.getOtherParent();
		sp = x.getSelfParent();
		//if no parents, then it's round 1
		if (op == null && sp == null) {
			x.setRoundCreated(1);
			return x.getRoundCreated();
		}
		rsp = round(sp);
		rop = round(op);

		//if parents have unequal rounds, then copy the round of the later parent
		if (rsp > rop) {
			x.setRoundCreated(rsp);
			return x.getRoundCreated();
		}
		if (rop > rsp) {
			x.setRoundCreated(rop);
			return x.getRoundCreated();
		}

		// parents have equal rounds. But if both are -1, then this is -1 (because -1 represents negative infinity)
		if (rsp == -1) {
			x.setRoundCreated(-1);
			return x.getRoundCreated();
		}

		// parents have equal rounds (not -1), so check if x can strongly see witnesses with a supermajority of stake
		stake = 0;
		for (long m = 0; m < numMembers; m++) {
			if (stronglySeeP(x, m) != null) {
				stake += addressBook.getStake(m);
			}
		}
		if (Utilities.isSupermajority(stake, addressBook.getTotalStake())) {
			//it's a supermajority, so advance to the next round
			x.setRoundCreated(1 + parentRound(x));
			return x.getRoundCreated();
		}
		//it's not a supermajority, so don't advance to the next round
		x.setRoundCreated(parentRound(x));
		return x.getRoundCreated();
	}

	/**
	 * The self-ancestor of x in the same round that is a witness (function from SWIRLDS-TR-2020-01).
	 * This result is memoized.
	 *
	 * @param x
	 * 		the event being queried
	 * @return The ancestor of x in the same round that is a witness, or null if x is null
	 */
	private EventImpl firstSelfWitnessS(EventImpl x) {
		if (x == null) {
			return null;
		}
		if (x.getFirstSelfWitnessS() != null) { //if already found and memoized, return it
			return x.getFirstSelfWitnessS();
		}
		//calculate, memoize, and return the result
		if (round(
				x) > round(x.getSelfParent())) {
			x.setFirstSelfWitnessS(x);
		} else {
			x.setFirstSelfWitnessS(firstSelfWitnessS(x.getSelfParent()));
		}
		return x.getFirstSelfWitnessS();
	}

	/**
	 * The earliest witness that is an ancestor of x in the same round as x (function from SWIRLDS-TR-2020-01).
	 * This result is memoized.
	 *
	 * @param x
	 * 		the event being queried
	 * @return the earliest witness that is an ancestor of x in the same round as x, or null if x is null
	 */
	private EventImpl firstWitnessS(EventImpl x) {
		if (x == null) {
			return null;
		}
		if (x.getFirstWitnessS() != null) { //if already found and memoized, return it
			return x.getFirstWitnessS();
		}
		//calculate, memoize, and return the result
		if (round(x) > parentRound(x)) {
			x.setFirstWitnessS(x);
		} else if (round(x) == round(x.getSelfParent())) {
			x.setFirstWitnessS(firstWitnessS(x.getSelfParent()));
		} else {
			x.setFirstWitnessS(firstWitnessS(x.getOtherParent()));
		}
		return x.getFirstWitnessS();
	}

	/**
	 * The event by m that x strongly sees in the round before the created round of x (function from
	 * SWIRLDS-TR-2020-01).
	 * This result is not memoized.
	 *
	 * @param x
	 * 		the event being queried
	 * @param m
	 * 		the member ID of the creator
	 * @return event by m that x strongly sees in the round before the created round of x, or null if none
	 */
	private EventImpl stronglySeeS1(EventImpl x, long m) {
		return stronglySeeP(firstWitnessS(x), m);
	}

	/**
	 * The first witness in round r that is a self-ancestor of x, where r is the round of the last event by m
	 * that is
	 * seen by x (function from SWIRLDS-TR-2020-01).
	 * This result is not memoized.
	 *
	 * @param x
	 * 		the event being queried
	 * @param m
	 * 		the member ID of the creator
	 * @return firstSelfWitnessS(lastSee ( x, m)), which is the first witness in round r that is a
	 * 		self-ancestor
	 * 		of x, where r is the round of the last event by m that is seen by x, or null if none
	 */
	private EventImpl firstSee(EventImpl x, long m) {
		return firstSelfWitnessS(lastSee(x, m));
	}

	/**
	 * Return the result of a "coin flip". It doesn't need to be cryptographically strong. It just needs to be the case
	 * that an attacker cannot predict the coin flip results before seeing the event, even if they can manipulate the
	 * internet traffic to the creator of this event earlier. It's even OK if the attacker can predict the coin flip
	 * 90% of the time. There simply needs to be some epsilon such that the probability of a wrong prediction is always
	 * greater than epsilon (and less than 1-epsilon).
	 * This result is not memoized.
	 *
	 * @param event
	 * 		the event that will vote with a coin flip
	 * @return true if voting for famous, false if voting for not famous
	 */
	private boolean coin(EventImpl event) {
		return ((event.getSignature()[(event.getSignature().length / 2)] & 1) == 1);
	}

	/////////////////////////////////////////////////////////////
	// Graph search Iterator and view
	/////////////////////////////////////////////////////////////

	/**
	 * This is an unmodifiable view collection for the collection of all valid ancestors of a given
	 * root event, that are reachable through valid ancestors. The "valid" ancestors are defined by a lambda predicate
	 * passed in to the constructor.
	 *
	 * It can be used to get an iterator for those events, or to read or modify the events. This does not implement
	 * Collection, because its only purpose to allow iteration over those events. The iteration is depth first, and
	 * backtracks each time it reaches an invalid event (one for which the predicate returns false).
	 *
	 * It is not threadsafe, and will silently fail without throwing any exceptions if you attempt to create and use
	 * two at the same time, even if they are in the same thread, and even if they are only used to read and not write.
	 * So always create only one at a time, and ensure that you are done with it before creating another one.
	 *
	 * This returns all ancestors of the root event that are valid. It iterates in an order that always returns
	 * a parent before its child. The root itself is considered to be one of the ancestors, and is last in the
	 * iteration.
	 *
	 * Recursion happens on self parents before other parents. So if there are multiple paths from the root to an event,
	 * it will use the path that stays on line of self parents for as far down as possible before leaving that line.
	 */
	private class ValidAncestorsView implements Iterable<EventImpl> {
		/** the event whose ancestors we are getting */
		private final EventImpl root;

		/** the time when the event last returned by the iterator's next() first reached the creator of root */
		private Instant timeReachedRoot;

		/** a lambda which filters which ancestors are of interest: only each event e for which valid(e)==true */
		private final Predicate<EventImpl> valid;

		/**
		 * The set of ancestors of the given event (the root of the search) for which valid is true.
		 * This will not include a valid ancestor that is only reachable through invalid ancestors.
		 *
		 * @param root
		 * 		the root event whose ancestors should be searched
		 * @param valid
		 * 		do a depth-first search, but backtrack from any event e where valid(e)==false
		 */
		public ValidAncestorsView(EventImpl root, Predicate<EventImpl> valid) {
			this.root = root;
			this.valid = valid;
		}

		/** @return the time when the event last returned by the iterator first reached a self-ancestor of the root */
		public Instant getTime() {
			return timeReachedRoot;
		}

		/** @return an iterator for this ValidAncestorsView collection of Events. */
		@Override
		public Iterator<EventImpl> iterator() {
			return new ValidAncestorsIterator(root, valid);
		}

		/** An iterator over the events in a ValidAncestorsView view. */
		private class ValidAncestorsIterator implements Iterator<EventImpl> {
			boolean hasNext = true; //becomes false when done and hashNext should return false
			EventImpl curr; //the current event reached in the search
			byte state = 0; //the state of the state machine searching from curr
			boolean selfAncestor = true; //is curr a self ancestor of the judge?
			Deque<EventImpl> stackRef = new ArrayDeque<>(300); //stack of EventImpl on the path to curr
			Deque<Byte> stackState = new ArrayDeque<>(300); //stack of state
			Deque<Boolean> stackSelfAncestor = new ArrayDeque<>(300); //stack of selfAncestor
			Deque<Instant> stackTime = new ArrayDeque<>(300); //stack of timeReachedRoot
			Predicate<EventImpl> valid;

			/** construct iterator that will iterate over all ancestors of root (including itself) */
			private ValidAncestorsIterator(EventImpl root, Predicate<EventImpl> valid) {
				this.valid = valid;
				curr = root;
				currMark++; //unmark all the events, so the search can find them all again
				timeReachedRoot = root.getTimeCreated(); //ancestors of curr reached creator then
			}

			/**
			 * Returns {@code true} if the iteration has more elements.
			 * (In other words, returns {@code true} if {@link #hasNext} would
			 * return an element rather than throwing an exception.)
			 *
			 * @return {@code true} if the iteration has more elements
			 */
			@Override
			public boolean hasNext() {
				return hasNext;
			}

			/**
			 * Returns the next element in the iteration.
			 *
			 * @return the next element in the iteration
			 * @throws NoSuchElementException
			 * 		if the iteration has no more elements
			 */
			@Override
			public EventImpl next() {
				if (!hasNext) {
					throw new NoSuchElementException("no more events left to iterator over");
				}
				while (true) { //keep recursing until we reach the return statement in the case state == 2
					curr.setMark(currMark); //mark this event so we don't explore it again later for this judge
					if (state == 0) { //try to recurse into selfParent
						EventImpl p = curr.getSelfParent();
						state = 1;
						if (p != null && p.getMark() != currMark && valid.test(p)) {
							stackRef.push(curr);
							stackState.push(state);
							stackSelfAncestor.push(selfAncestor);
							stackTime.push(timeReachedRoot);
							curr = p;
							state = 0;
							if (selfAncestor) {
								timeReachedRoot = curr.getTimeCreated(); //ancestors of curr reached creator then
							}
						} else { //there is no selfParent, or it was already visited, or it was consensus
							state = 1;
						}
					} else if (state == 1) { //try to recurse into otherParent
						EventImpl p = curr.getOtherParent();
						state = 2;
						if (p != null && p.getMark() != currMark && valid.test(p)) {
							stackRef.push(curr);
							stackState.push(state);
							stackSelfAncestor.push(selfAncestor);
							stackTime.push(timeReachedRoot);
							curr = p;
							state = 0;
							selfAncestor = false; //first step off the selfAncestor path makes all the events below
							// false
						} //else there is no otherParent, or it was already visited, or it was consensus
					} else if (state == 2) { //done with ancestors of curr, so return curr then backtrack
						if (stackRef.size() == 0) { //if we're back to the root
							hasNext = false; //then there are no more
							return curr; //return this root
						}
						EventImpl toReturn = curr; //else we are done with all the descendents, so backtrack
						curr = stackRef.pop();
						state = stackState.pop();
						selfAncestor = stackSelfAncestor.pop();
						timeReachedRoot = stackTime.pop();
						return toReturn; //return the child of the vertex we just backtracked to
					} else { //this should never happen (illegal state number)
						log.error(EXCEPTION.getMarker(),
								"illegal state number: iterator state {} is not in range [0,2]",
								state);
					}
				}
			}
		}
	}
}
