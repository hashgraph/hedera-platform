/*
 * (c) 2016-2022 Swirlds, Inc.
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

package com.swirlds.common.merkle.synchronization.internal;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * This object is used to track a node for which the learner is expecting data.
 */
public class ExpectedLesson<T> {

	/**
	 * Do we already have the node sent by the teacher?
	 */
	private final boolean nodeAlreadyPresent;

	/**
	 * The node that will be the parent of this node.
	 */
	private final T parent;

	/**
	 * This node's eventual position within its parent.
	 */
	private final int positionInParent;

	/**
	 * The node that was originally in this position within the tree.
	 */
	private final T originalNode;

	/**
	 * Create a record for a node for which we are expecting data.
	 *
	 * @param parent
	 * 		the eventual parent of the node
	 * @param positionInParent
	 * 		the eventual position of the node within its parent
	 * @param originalNode
	 * 		the node that was originally in this location in the tree
	 * @param nodeAlreadyPresent
	 * 		does the learner already have the node being sent by the teacher?
	 */
	public ExpectedLesson(
			final T parent,
			final int positionInParent,
			final T originalNode,
			final boolean nodeAlreadyPresent) {
		this.parent = parent;
		this.positionInParent = positionInParent;
		this.originalNode = originalNode;
		this.nodeAlreadyPresent = nodeAlreadyPresent;
	}

	/**
	 * Does the learner already have the node from the teacher?
	 */
	public boolean isNodeAlreadyPresent() {
		return nodeAlreadyPresent;
	}

	/**
	 * Get the eventual parent of the node.
	 */
	public T getParent() {
		return parent;
	}

	/**
	 * Get the eventual position of the node within its parent.
	 */
	public int getPositionInParent() {
		return positionInParent;
	}

	/**
	 * Get the original node in this position.
	 */
	public T getOriginalNode() {
		return originalNode;
	}

	/**
	 * For debugging purposes
	 */
	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.append("already present", nodeAlreadyPresent)
				.append("parent", parent)
				.append("position", positionInParent)
				.append("original", originalNode)
				.build();
	}
}
