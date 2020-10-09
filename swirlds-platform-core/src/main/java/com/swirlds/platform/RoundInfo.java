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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hold all of the information about a round, such as lists of witnesses, statistics about them, and all the
 * elections that are active in that round.
 * <p>
 * An inner class ElectionRound is used to store information about every member's vote in one round on the
 * question of whether some previous witness is famous. The ElectionRound objects connect to each other as a
 * double-linked grid, and each row of it is linked to from a RoundInfo object.
 */
public class RoundInfo {
	/** the round this is about (0 is first) */
	long round = 0;
	// are all the famous witnesses known for this round?
	boolean fameDecided = false;
	// number of known witnesses in this round
	int numWitnesses = 0;
	// number of known witnesses in this round with unknown fame
	int numUnknownFame = 0;
	// these witnesses are the first event in this round by each member
	List<EventImpl> witnesses = Collections
			.synchronizedList(new ArrayList<EventImpl>());
	// The judges (unique famous witnesses) in this round. Element i is the one created by member i, or null if none
	EventImpl[] judges;
	// all the known events that were created in this round (i.e., have this round number)
	List<EventImpl> allEvents = Collections
			.synchronizedList(new ArrayList<EventImpl>()); // synchronized so getAllEvents works well
	// these events were all created in this round, and aren't yet consensus
	List<EventImpl> nonConsensusEvents = Collections
			.synchronizedList(new ArrayList<EventImpl>());
	// XOR of sigs of all famous events
	byte[] whitening = new byte[Crypto.SIG_SIZE_BYTES];

	// each witness has one election per future round, to decide whether it is famous. This should
	// only be accessed by Hashgraph from within a synchronized method, because it is not thread-safe.
	// this is a quadruply linked list, with 4 links in each ElectionRound object, to prev/next election in that
	// round, and to the corresponding election in prev/next round.
	ElectionRound elections = null;

	/** the minimum generation of all the famous witnesses in this round */
	long minGeneration = -1;

	/**
	 * @return the round this is about (0 is first)
	 */
	public long getRound() {
		return round;
	}

	/**
	 * One round of votes about the fame of one witness. These link to form a doubly-linked grid, with one
	 * doubly-linked list per RoundInfo.
	 */
	public static class ElectionRound {
		final ArrayList<Boolean> vote;// vote by each witness in this round for event being famous
		final EventImpl event;        // the event whose fame is being voted on
		final long age;               // = round(this ElectionRound) - round(event)
		final RoundInfo roundInfo;    // the RoundInfo for the round holding this election

		// the following 4 form a 2D linked list (or "linked grid"), with
		// each row being a doubly-linked list, and each column being a doubly-linked list.
		ElectionRound nextRound;       // Election in next-higher round for this witness (null if no more)
		ElectionRound prevRound;       // Election in the round before this one about the same witness
		ElectionRound nextElection;    // the next Election (for next witness to decide) in this round
		ElectionRound prevElection;    // the previous Election (for prev witness to decide) in this round

		ElectionRound(RoundInfo roundInfo, int numMembers, EventImpl event,
				long age) {
			this.vote = new ArrayList<>();
			this.event = event;
			this.age = age;
			this.roundInfo = roundInfo;
			this.nextRound = null;
			this.prevRound = null;
			this.nextElection = null;
			this.prevElection = null;
		}
	}

	/**
	 * constructor for the RoundInfo to describe the given round number (0 is first)
	 *
	 * @param round
	 * 		the round it will be used to describe
	 * @param numMembers
	 * 		the number of members currently in the address book
	 */
	public RoundInfo(long round, int numMembers) {
		this.round = round;
		this.judges = new EventImpl[numMembers];
	}

	/**
	 * Add a famous witness to the round (ignore if it's not a judge: a unique famous witness) and set the minGeneration
	 *
	 * @param w
	 * 		the witness to add
	 */
	void addFamousWitness(EventImpl w) {
		int creator = (int) w.getCreatorId();
		if (judges[creator] == null) {
			judges[creator] = w;
		} else {
			//if this creator forked, then the judge is the "unique" famous witness, which is the one with minimum hash
			//(where "minimum" is the lexicographically-least signed byte array)
			if (Utilities.arrayCompare(w.getBaseHash().getValue(), judges[creator].getBaseHash().getValue()) < 0) {
				judges[creator] = w;
			}
		}

		minGeneration = (minGeneration == -1) ? w.getGeneration() : Math.min(minGeneration, w.getGeneration());
	}

	/**
	 * The judges (unique famous witnesses) in this round. Element i is the one created by member i, or null if none.
	 */
	public EventImpl[] getJudges() {
		return judges;
	}
}
