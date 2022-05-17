/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.merkle.synchronization.internal;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.views.StandardTeacherTreeView;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;

/**
 * A subtree that needs to be sent by the teacher.
 */
public final class TeacherSubtree implements AutoCloseable {

	private final MerkleNode root;
	private final TeacherTreeView<?> view;

	/**
	 * Create a subtree with {@link StandardTeacherTreeView}.
	 *
	 * @param root
	 * 		the root of the subtree
	 */
	public TeacherSubtree(final MerkleNode root) {
		this(root, new StandardTeacherTreeView(root));
	}

	/**
	 * Create an object that tracks a subtree that needs to be sent by the teacher.
	 *
	 * @param root
	 * 		the root of the subtree
	 * @param view
	 * 		the view to be used by the subtree
	 */
	public TeacherSubtree(final MerkleNode root, final TeacherTreeView<?> view) {
		this.root = root;
		this.view = view;

		if (root != null) {
			root.incrementReferenceCount();
		}
	}

	/**
	 * Get the root of the subtree.
	 *
	 * @return the root
	 */
	public MerkleNode getRoot() {
		return root;
	}

	/**
	 * Get the view to be used for the subtree.
	 *
	 * @return a teacher view
	 */
	public TeacherTreeView<?> getView() {
		return view;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		view.close();
		if (root != null) {
			root.decrementReferenceCount();
		}
	}
}
