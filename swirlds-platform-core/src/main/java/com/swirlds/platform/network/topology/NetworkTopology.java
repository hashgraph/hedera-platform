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

package com.swirlds.platform.network.topology;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.network.RandomGraph;

import java.util.List;
import java.util.function.Predicate;

/**
 * Holds information about the topology of the network
 */
public interface NetworkTopology {
	/**
	 * Should this node be connecting to this peer?
	 *
	 * @param nodeId
	 * 		the peer ID
	 * @return true if this connection is in line with the network topology
	 */
	boolean shouldConnectTo(NodeId nodeId);

	/**
	 * Should this peer be connecting to this node?
	 *
	 * @param nodeId
	 * 		the peer ID
	 * @return true if this connection is in line with the network topology
	 */
	boolean shouldConnectToMe(NodeId nodeId);

	/**
	 * @return a list of all peers this node should be connected to
	 */
	List<NodeId> getNeighbors();

	/**
	 * @return a list of peers this node should be connected to with the applied filter
	 */
	List<NodeId> getNeighbors(final Predicate<NodeId> filter);

	/**
	 * @return the underlying graph on which this topology is based on
	 */
	RandomGraph getConnectionGraph();
}
