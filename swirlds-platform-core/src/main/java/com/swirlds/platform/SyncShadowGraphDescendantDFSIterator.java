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

public class SyncShadowGraphDescendantDFSIterator implements Iterator<SyncShadowEvent>  {
	private final SyncShadowGraph shadowGraph;
	private SyncShadowEvent cur;
	final Stack<SyncShadowEvent> st = new Stack<>();
	private final HashSet<SyncShadowEvent> visited = new HashSet<>();

	SyncShadowGraphDescendantDFSIterator(SyncShadowGraph shadowGraph, SyncShadowEvent start) {
		this.shadowGraph = shadowGraph;
		this.cur = start;
		st.push(cur);
	}

	@Override
	public boolean hasNext() {
		return !st.empty();
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

		return null;
	}

	private void pushNext() {
		cur.otherChildren.forEach((SyncShadowEvent s) -> { if(!visited.contains(s)) st.push(s); });
		cur.selfChildren.forEach((SyncShadowEvent s) -> { if(!visited.contains(s)) st.push(s); });
	}

}
