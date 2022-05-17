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

import com.swirlds.common.NodeId;
import com.swirlds.platform.network.RandomGraph;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A topology that never changes. Can be either unidirectional or bidirectional.
 */
public class StaticTopology implements NetworkTopology {
	private static final long SEED = 0;

	private final NodeId selfId;
	private final int networkSize;
	private final RandomGraph connectionGraph;
	private final boolean unidirectional;

	public StaticTopology(
			final NodeId selfId,
			final int networkSize,
			final int numberOfNeighbors) {
		this(selfId, networkSize, numberOfNeighbors, true);
	}

	public StaticTopology(
			final NodeId selfId,
			final int networkSize,
			final int numberOfNeighbors,
			final boolean unidirectional) {
		this.selfId = selfId;
		this.networkSize = networkSize;
		this.unidirectional = unidirectional;
		this.connectionGraph = new RandomGraph(networkSize, numberOfNeighbors, SEED);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<NodeId> getNeighbors() {
		return getNeighbors((nodeId -> true));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<NodeId> getNeighbors(final Predicate<NodeId> filter) {
		return Arrays.stream(connectionGraph.getNeighbors(selfId.getIdAsInt()))
				.mapToLong(i -> (long) i)
				.mapToObj(NodeId::createMain)
				.filter(filter)
				.collect(Collectors.toList());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean shouldConnectToMe(final NodeId nodeId) {
		return isNeighbor(nodeId) && (unidirectional || nodeId.getId() < selfId.getId());
	}

	/**
	 * Queries the topology on whether this node is my neighbor
	 *
	 * @param nodeId
	 * 		the ID of the node being queried
	 * @return true if this node is my neighbor, false if not
	 */
	private boolean isNeighbor(final NodeId nodeId) {
		return selfId.sameNetwork(nodeId)
				&& nodeId.getId() >= 0
				&& nodeId.getId() < networkSize
				&& connectionGraph.isAdjacent(selfId.getIdAsInt(), nodeId.getIdAsInt());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean shouldConnectTo(final NodeId nodeId) {
		return isNeighbor(nodeId) && (unidirectional || nodeId.getId() > selfId.getId());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RandomGraph getConnectionGraph() {
		return connectionGraph;
	}
}
