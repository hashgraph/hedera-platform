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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

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
class SyncShadowGraphDescendantDFSIterator implements Iterator<SyncShadowEvent> {
	private final Deque<SyncShadowEvent> stack = new ArrayDeque<>();
	private final HashSet<SyncShadowEvent> visited = new HashSet<>();
	private SyncShadowEvent cur;

	public SyncShadowGraphDescendantDFSIterator(final SyncShadowEvent start) {
		this.cur = start;
		stack.push(cur);
	}

	@Override
	public boolean hasNext() {
		return !stack.isEmpty();
	}

	public SyncShadowEvent next() {
		while (!stack.isEmpty()) {
			cur = stack.pop();

			if (visited.contains(cur)) {
				continue;
			}

			visited.add(cur);
			pushNext();
			return cur;
		}

		throw new NoSuchElementException();
	}

	private void pushNext() {
		cur.getOtherChildren().forEach(shadowEvent -> {
			if (!visited.contains(shadowEvent)) {
				stack.push(shadowEvent);
			}
		});

		cur.getSelfChildren().forEach(shadowEvent -> {
			if (!visited.contains(shadowEvent)) {
				stack.push(shadowEvent);
			}
		});
	}

}
