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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A bottom-up DFS iterator for all descendants of a given shadow event in a shadow graph. Here, "bottom-up" means that
 * if an event in the DAG is encountered, then at least one its parents has already been encountered. That is, any two
 * traversal paths share only the starting event. That is, no two traversal paths intersect after leaving the starting
 * event. In other words, iteration is unique: In a single loop with a single iterator, a shadow event is visited
 * at most once.
 *
 * Further, iteration is exhaustive: all descendants of the starting iterator are encountered. I.e., execution is
 * theta(linear) in the number of events, which, for a finite graph, is equivalent to saying it is theta(linear)
 * in the number of descendants of the starting event.
 */
public class SyncShadowGraphDescendantDFSIterator implements Iterator<SyncShadowEvent> {
	private final Set<Hash> sendingTips;
	private final Set<SyncShadowEvent> visited;
	private final Deque<SyncShadowEvent> stack = new ArrayDeque<>();
	private final List<Long> maxTipGenerations;
	private final boolean includeOtherChildren;
	private SyncShadowEvent cur;

	public SyncShadowGraphDescendantDFSIterator(
			final SyncShadowEvent start,
			Set<Hash> sendingTips,
			Set<SyncShadowEvent> visited,
			List<Long> maxTipGenerations,
			boolean includeOtherChildren) {
		this.cur = start;
		this.sendingTips = sendingTips;
		this.visited = visited;
		this.maxTipGenerations = maxTipGenerations;
		this.includeOtherChildren = includeOtherChildren;

		stack.push(cur);
	}

	@Override
	public boolean hasNext() {
		return !stack.isEmpty();
	}

	public SyncShadowEvent next() {
		if (!stack.isEmpty()) {
			cur = stack.pop();
			visited.add(cur);
			pushNext();
			return cur;
		}

		throw new NoSuchElementException();
	}

	private void pushNext() {
		// If we have reached a sending tip, do not process descendants.
		if (sendingTips.contains(cur.getEvent().getBaseHash())) {
			return;
		}

		if (includeOtherChildren) {
			cur.getOtherChildren().forEach(otherChild -> {
				if (!visited.contains(otherChild)) {
					if (otherChild.getEvent().getGeneration() <= maxTipGenerations.get(
							(int) otherChild.getEvent().getCreatorId())) {
						stack.push(otherChild);
					}
				}
			});
		}

		cur.getSelfChildren().forEach(selfChild -> {
			if (!visited.contains(selfChild)) {
				if (selfChild.getEvent().getGeneration() <= maxTipGenerations.get(
						(int) selfChild.getEvent().getCreatorId())) {
					stack.push(selfChild);
				}
			}
		});
	}

}
