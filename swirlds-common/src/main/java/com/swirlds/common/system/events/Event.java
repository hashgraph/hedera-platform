/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.system.events;

import com.swirlds.common.crypto.Hash;

import java.time.Instant;

public interface Event {
	/**
	 * Whether this is a witness event or not. True if either this event's round &gt; selfParent's
	 * round, or there is no self parent.
	 *
	 * @return boolean value to tell whether this event is a witness or not
	 */
	boolean isWitness();

	/**
	 * Tells whether this event is a witness and the fame election is over or not.
	 *
	 * @return True means this event is a witness and the fame election is over
	 */
	boolean isFameDecided();

	/**
	 * Is this event both a witness and famous?
	 *
	 * @return True means this event is both a witness and famous
	 */
	boolean isFamous();

	/**
	 * Is this event part of the consensus order?
	 *
	 * @return True means this is part of consensus order
	 */
	boolean isConsensus();

	/**
	 * The community's consensus timestamp for this event if a consensus is reached. Otherwise.
	 * it will be an estimate.
	 *
	 * @return the consensus timestamp
	 */
	Instant getConsensusTimestamp();

	/**
	 * ID of otherParent. -1 if otherParent doesn't exist.
	 *
	 * @return Other parent event's ID
	 */
	long getOtherId();

	/**
	 * This event's parent event. null if none exists.
	 *
	 * @return The parent event of this event
	 */
	Event getSelfParent();

	/**
	 * The other parent event of this event. null if other parent doesn't exist.
	 *
	 * @return The other parent event
	 */
	Event getOtherParent();

	/**
	 * This event's generation, which is 1 plus max of parents' generations.
	 *
	 * @return This event's generation
	 */
	long getGeneration();

	/**
	 * The created round of this event, which is the max of parents' created around, plus either 0 or 1.
	 *
	 * @return The round number this event is created
	 */
	long getRoundCreated();

	/**
	 * ID of this event's creator.
	 *
	 * @return ID of this event's creator
	 */
	long getCreatorId();

	/**
	 * If isConsensus is true, the round where all unique famous witnesses see this event.
	 *
	 * @return the round number as described above
	 */
	long getRoundReceived();

	/**
	 * if isConsensus is true,  the order of this in history (0 first), else -1
	 *
	 * @return consensusOrder the consensus order sequence number
	 */
	long getConsensusOrder();

	/**
	 * @return The hash instance of the hashed base event data.
	 */
	Hash getBaseHash();
}
