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

import static com.swirlds.common.CommonUtils.throwArgNull;

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
public class ShadowGraphDescendantDFSIterator implements Iterator<ShadowEvent> {

	/**
	 * The set used to track nodes that have been previously visited.
	 */
	private final Set<ShadowEvent> visited;

	/**
	 * The stack used to maintain the traversal order and next nodes to be traversed.
	 */
	private final Deque<ShadowEvent> stack = new ArrayDeque<>();

	/**
	 * A set of the sending tips determined at the beginning of the synchronization. This is used to bound the
	 * traversal, please refer to the {@link #pushNext()} method for more information. If this is an empty list, then no
	 * bounding will be performed based on sending tips and the traversal will be bounded solely by the {@code
	 * maxTipGenerations} list.
	 */
	private final Set<Hash> sendingTips;

	/**
	 * A list of maximum generations per creator where the list index refers to the zero-based creator and the value is
	 * the maximum tip generation for that creator. This is used to bound the traversal, please refer to the {@link
	 * #pushNext()} method for more information.
	 */
	private final List<Long> maxTipGenerations;

	/**
	 * A flag indicating whether to traverse the other children of each node visited.
	 */
	private final boolean includeOtherChildren;

	/**
	 * The current position of this DFS traversal.
	 */
	private ShadowEvent cur;

	/**
	 * Constructs a new iterator instance.
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
	public ShadowGraphDescendantDFSIterator(final ShadowEvent start, final Set<Hash> sendingTips,
			final Set<ShadowEvent> visited, final List<Long> maxTipGenerations, final boolean includeOtherChildren) {
		throwArgNull(start, "start");
		throwArgNull(sendingTips, "sendingTips");
		throwArgNull(visited, "visited");
		throwArgNull(maxTipGenerations, "maxTipGenerations");

		this.cur = start;
		this.sendingTips = sendingTips;
		this.visited = visited;
		this.maxTipGenerations = maxTipGenerations;
		this.includeOtherChildren = includeOtherChildren;

		stack.push(cur);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasNext() {
		return !stack.isEmpty();
	}

	/**
	 * {@inheritDoc}
	 */
	public ShadowEvent next() {
		if (!stack.isEmpty()) {
			cur = stack.pop();
			visited.add(cur);
			pushNext();
			return cur;
		}

		throw new NoSuchElementException();
	}

	/**
	 * Traverses the graph from the {@code cur} node and adds the results to the {@code stack}. This method optionally
	 * traverses the other children of the current node. If the {@link #maxTipGenerations} and {@link #sendingTips} are
	 * not empty, then this traversal is bounded by both max generation and sending tips. If the {@link #sendingTips}
	 * list is empty, then this traversal is bounded solely by the {@link #maxTipGenerations} for each creator.
	 */
	private void pushNext() {
		// If we have reached a sending tip, do not process descendants.
		if (sendingTips.contains(cur.getEvent().getBaseHash())) {
			return;
		}

		if (includeOtherChildren) {
			for (final ShadowEvent otherChild : cur.getOtherChildren()) {
				enumerateChildren(otherChild);
			}
		}

		for (final ShadowEvent selfChild : cur.getSelfChildren()) {
			enumerateChildren(selfChild);
		}
	}

	private void enumerateChildren(final ShadowEvent child) {
		if (!visited.contains(child)
				&& child.getEvent().getGeneration() <= maxTipGenerations.get((int) child.getEvent().getCreatorId())) {
			stack.push(child);
		}
	}

}
