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
import java.util.Stack;

public class SyncShadowGraphDFSIterator implements Iterator<SyncShadowEvent> {
	private final SyncShadowGraph shadowGraph;
	private SyncShadowEvent cur;
	final Stack<SyncShadowEvent> st = new Stack<>();
	private final HashSet<SyncShadowEvent> visited = new HashSet<>();

	SyncShadowGraphDFSIterator(SyncShadowGraph shadowGraph) {
		this.shadowGraph = shadowGraph;
		cur = null;
		for (SyncShadowEvent s : shadowGraph.shadowEvents)
			if (s.selfParent == null) {
				cur = s;
				st.push(cur);
				break;
			}

	}

	@Override
	public boolean hasNext() {
		return visited.size() < shadowGraph.shadowEvents.size();
	}

	public SyncShadowEvent next() {
		while(!st.empty()) {
			cur = st.pop();

			if(visited.contains(cur))
				continue;

			visited.add(cur);
			pushNext();
			return cur;
		}

		// A connected component has been exhausted. Search for an Event in an
		// unvisited component.
		for(SyncShadowEvent s : shadowGraph)
			if(!visited.contains(s)) {
				cur = s;
				st.push(cur);
				return cur;
			}

		// There are no unvisited components.
		// We are here iff `hasNext()` evaluates to `true`: should never get here.
		return null;
	}

	void pushNext() {
		if(!visited.contains(cur.otherParent) && !st.contains(cur.otherParent) && cur.otherParent != null)
			st.push(cur.otherParent);
		if(!visited.contains(cur.selfParent) && !st.contains(cur.selfParent) && cur.selfParent != null)
			st.push(cur.selfParent);

		cur.otherChildren.forEach((SyncShadowEvent s) -> { if(!visited.contains(s)) st.push(s); });
		cur.selfChildren.forEach((SyncShadowEvent s) -> { if(!visited.contains(s)) st.push(s); });
	}

}

