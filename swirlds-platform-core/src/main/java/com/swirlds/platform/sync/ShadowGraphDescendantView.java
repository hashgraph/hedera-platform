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

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.swirlds.common.CommonUtils.throwArgNull;

/**
 * A graph view facade for the descendants of a given event in a shadow graph.
 */
class ShadowGraphDescendantView implements Iterable<ShadowEvent> {
	/**
	 * The shadow event from which iterator begins
	 */
	private final ShadowEvent start;

	/**
	 * The set used to track nodes that have been previously visited.
	 */
	private final Set<ShadowEvent> visited;

	/**
	 * A set of the sending tips determined at the beginning of the synchronization. This is used to bound the
	 * traversal, please refer to the {@code ShadowGraphDescendantDFSIterator#pushNext()} method for more information.
	 * If this is an empty list, then no bounding will be performed based on sending tips and the traversal will be
	 * bounded solely by the {@code maxTipGenerations} list.
	 */
	private final Set<Hash> sendingTips;

	/**
	 * A list of maximum generations per creator where the list index refers to the zero-based creator and the value is
	 * the maximum tip generation for that creator. This is used to bound the traversal, please refer to the {@code
	 * ShadowGraphDescendantDFSIterator#pushNext()} method for more information.
	 */
	private final List<Long> maxTipGenerations;

	/**
	 * A flag indicating whether to traverse the other children of each node visited.
	 */
	private final boolean includeOtherChildren;


	/**
	 * Constructs a DFS graph descendant view for a given starting event.
	 *
	 * @param start
	 * 		the node from which to begin the depth first search.
	 * @param sendingTips
	 * 		the list of sending tips to bound the traversal operation.
	 * @param visited
	 * 		the {@link Set} used to track visited state of each node.
	 * @param maxTipGenerations
	 * 		the {@link List} of maximum tip generations per creator, computed from the {@code sendingTips} list.
	 * @param includeOtherChildren
	 * 		a flag indicating whether this iterator should traversal the other children of each node.
	 * @throws IllegalArgumentException
	 * 		if the {@code start}, {@code sendingTips}, {@code visited}, or {@code maxTipGenerations} argument is
	 * 		passed a {@code null} value.
	 */
	public ShadowGraphDescendantView(final ShadowEvent start, final Set<Hash> sendingTips,
			final Set<ShadowEvent> visited, final List<Long> maxTipGenerations, final boolean includeOtherChildren) {
		throwArgNull(start, "start");
		throwArgNull(sendingTips, "sendingTips");
		throwArgNull(visited, "visited");
		throwArgNull(maxTipGenerations, "maxTipGenerations");

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
	public Iterator<ShadowEvent> iterator() {
		return new ShadowGraphDescendantDFSIterator(start, sendingTips, visited, maxTipGenerations,
				includeOtherChildren);
	}
}
