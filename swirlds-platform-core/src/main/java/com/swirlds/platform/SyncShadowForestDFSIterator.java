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

import java.util.HashSet;
import java.util.Iterator;

/**
 * A "bottom-up" DFS iterator on self-parent and self-children. "Bottom-up" here
 * means we move through the unvisited self-tree of the start vertex.
 * When there are no more unvisited self-descendants of the start vertex, move to
 * its self-parent and repeat.
 *
 * This ceases when the self-parent DNE in the graph. (That is, when the current
 * location is "primitive"). Then, move to an unvisited primitive and repeat.
 *
 * This search terminates when there are no unvisited vertices.
 *
 * The last vertex visited is a tip Event descended from the last unvisited primitive.
 *
 * Assumes: The shadowGraph's tip set does not change during iteration.
 */
public class SyncShadowForestDFSIterator implements Iterator<SyncShadowEvent> {
	private final SyncShadowGraph syncShadowGraph;
	private SyncShadowEvent cur;
	private final HashSet<SyncShadowEvent> visited;

	SyncShadowForestDFSIterator(SyncShadowGraph syncShadowGraph, SyncShadowEvent start) {
		this.visited = new HashSet<>();
		this.syncShadowGraph = syncShadowGraph;
		cur = start;
		if(cur == null)
			for (SyncShadowEvent s : syncShadowGraph.shadowEvents)
				if (s.selfParent == null) {
					cur = s;
					break;
				}
	}

	@Override
	public boolean hasNext() {
		return visited.size() < syncShadowGraph.shadowEvents.size();
	}

	@Override
	public SyncShadowEvent next() {
		// (first entry only)
		if (!visited.contains(cur)) {
			visited.add(cur);
			return cur;
		}

		// Find an unvisited self-child
		for (SyncShadowEvent sc : cur.selfChildren)
			if (!visited.contains(sc)) {
				cur = sc;
				visited.add(cur);
				return cur;
			}

		// If there is no unvisited self-child, backtrack to self-parent.
		// Repeat until an unvisited sibling is found, or else there
		// is no self-parent.
		while (cur.selfParent != null) {
			cur = cur.selfParent;
			for (SyncShadowEvent sc : cur.selfChildren)
				if (!visited.contains(sc)) {
					cur = sc;
					visited.add(cur);
					return cur;
				}
		}

		// If the entire self-tree rooted at cur@entry has been visited,
		// scan for the next primitive Event (an Event that has no self-parent).
		for (SyncShadowEvent s : syncShadowGraph.shadowEvents)
			if (s.selfParent == null && !visited.contains(s)) {
				cur = s;
				visited.add(s);
				return cur;
			}

		// There are no remaining trees to visit in the forest.
		// We are here iff `hasNext()` evaluates to `true`: should never get here.
		return null;
	}

}

