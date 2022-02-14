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

package com.swirlds.common.merkle.synchronization.views;

import com.swirlds.common.merkle.MerkleNode;

/**
 * Nodes that are want to use a custom view for reconnect must extend this interface and
 * return true for {@link MerkleNode#hasCustomReconnectView() hasCustomReconnectView()}.
 *
 * @param <T>
 * 		the type used by the view for the teacher
 * @param <L>
 * 		the type used by the view for the learner
 */
public interface CustomReconnectRoot<T, L> extends MerkleNode {

	/**
	 * <p>
	 * Build a view of this subtree to be used for reconnect by the teacher.
	 * </p>
	 *
	 * <p>
	 * It is ok if this view is not immediately ready for use, as long as the view eventually
	 * becomes ready for use (presumably when some background task has completed). If this is
	 * the case, then the {@link TeacherTreeView#waitUntilReady()} on the returned view should
	 * block until the view is ready to be used to perform a reconnect.
	 * </p>
	 *
	 * @return a view representing this subtree
	 */
	TeacherTreeView<T> buildTeacherView();

	/**
	 * Build a view of this subtree to be used for reconnect by the learner.
	 *
	 * @return a view representing this subtree
	 */
	LearnerTreeView<L> buildLearnerView();

	/**
	 * If the original node in this position is of the correct type then the learner's node is initialized via
	 * this method. After this method is called, this node and its subtree should be entirely hashed, mutable,
	 * and should not cause the original to modified.
	 *
	 * @param originalNode
	 * 		the original node in the learner's tree in the root position of this subtree
	 */
	void setupWithOriginalNode(final MerkleNode originalNode);

	/**
	 * Called on a node if there is no data to be copied.
	 */
	void setupWithNoData();

	/**
	 * {@inheritDoc}
	 */
	@Override
	default boolean hasCustomReconnectView() {
		return true;
	}
}
