/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.platform.sync;

import com.swirlds.common.crypto.Hash;

import java.util.List;
import java.util.Set;

/**
 * A graph view facade for the descendants of a given event in a shadow graph.
 */
class SyncShadowGraphDescendantView implements Iterable<SyncShadowEvent> {
	/**
	 * The shadow event from which iterator begins
	 */
	private final SyncShadowEvent start;
	private final Set<Hash> sendingTips;
	private final Set<SyncShadowEvent> visited;
	private final List<Long> maxTipGenerations;
	private final boolean includeOtherChildren;


	/**
	 * Construct a DFS graph descendant view for a given starting event
	 *
	 * @param start
	 * 		the starting event
	 */
	public SyncShadowGraphDescendantView(
			final SyncShadowEvent start,
			final Set<Hash> sendingTips,
			final Set<SyncShadowEvent> visited,
			final List<Long> maxTipGenerations,
			final boolean includeOtherChildren) {
		this.start = start;
		this.sendingTips = sendingTips;
		this.visited = visited;
		this.maxTipGenerations = maxTipGenerations;
		this.includeOtherChildren = includeOtherChildren;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SyncShadowGraphDescendantDFSIterator iterator() {
		return new SyncShadowGraphDescendantDFSIterator(start, sendingTips, visited, maxTipGenerations,
				includeOtherChildren);
	}
}
