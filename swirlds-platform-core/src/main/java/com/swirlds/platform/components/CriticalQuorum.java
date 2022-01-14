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

package com.swirlds.platform.components;

import com.swirlds.platform.EventImpl;
import com.swirlds.platform.observers.EventAddedObserver;

/**
 * <p>
 * A critical quorum a heuristic used to select the group of nodes that, when gossiped with, have a high probability
 * of moving the network towards achieving quorum, thus allowing for consensus to proceed.
 * </p>
 *
 * <p>
 * If a node has gossiped with many other nodes recently then it is less likely to be in the critical quorum. Since
 * it has participated in the ongoing consensus, its input in the short term is unlikely to move consensus forward.
 * </p>
 *
 * <p>
 * If a node has not gossiped with many other nodes recently then it is more likely to be in the critical quorum.
 * This node has not participated in recent consensus operations, and so its input (or worded differently, its vote)
 * is likely to change (and advance) the consensus algorithm.
 * </p>
 *
 * <p>
 * The end goal of identifying the critical quorum is to prioritise gossip with nodes that are likely to move consensus
 * forward, and to deprioritise gossip with nodes that will not. There is no need for a node to gossip with another
 * node many times in a short span if that gossip is not aiding in the advancement of consensus.
 * </p>
 *
 * <p>
 * The algorithm for deriving a critical quorum is defined below. A critical quorum will always contain 1/3 or
 * more of the stake of the network (up to and including all stake in the network if the number of events
 * from each node is exactly the same).
 * </p>
 *
 * <ol>
 * <li> Start with a threshold of 0. </li>
 * <li> Count the stake of all nodes that have a number of events (in the current round) equal or less
 * than the threshold. </li>
 * <li> If the stake counted meets or exceeds 1/3 of the whole then stop. All nodes with a number of events that do not
 * exceed the threshold are considered to be part of the critical quorum. </li>
 * <li> If the stake counted is below 1/3 then increase the threshold by 1 and go to step 2. </li>
 * </ol>
 */
public interface CriticalQuorum extends EventAddedObserver {

	/**
	 * Checks whether the node with the supplied id is in critical quorum based on the number of events
	 * created in the most recent round.
	 *
	 * @param nodeId
	 * 		the id of the node to check
	 * @return true if it is in the critical quorum, false otherwise
	 */
	boolean isInCriticalQuorum(long nodeId);

	/**
	 * {@inheritDoc}
	 */
	@Override
	void eventAdded(EventImpl event);
}
