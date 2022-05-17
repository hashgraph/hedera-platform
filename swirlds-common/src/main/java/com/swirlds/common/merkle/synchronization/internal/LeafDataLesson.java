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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;

import java.io.IOException;

/**
 * This lesson describes a leaf node.
 *
 * @param <T>
 * 		the type used by the view to represent a node
 */
public class LeafDataLesson<T> implements SelfSerializable {

	private static final long CLASS_ID = 0xafbdd5560579cb02L;

	private static final class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	private TeacherTreeView<T> teacherView;
	private LearnerTreeView<T> learnerTreeView;
	private T leaf;

	/**
	 * Zero arg constructor for constructable registry.
	 */
	public LeafDataLesson() {

	}

	/**
	 * This constructor is used by the learner when deserializing.
	 *
	 * @param learnerTreeView
	 * 		the view for the learner
	 */
	public LeafDataLesson(final LearnerTreeView<T> learnerTreeView) {
		this.learnerTreeView = learnerTreeView;
	}

	/**
	 * Create a new lesson for a leaf node.
	 *
	 * @param teacherView
	 * 		the view for the teacher
	 * @param leaf
	 * 		the leaf to send in the lesson
	 */
	public LeafDataLesson(final TeacherTreeView<T> teacherView, final T leaf) {
		this.teacherView = teacherView;
		this.leaf = leaf;
	}

	/**
	 * Get the leaf contained by this lesson.
	 */
	public T getLeaf() {
		return leaf;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		teacherView.serializeLeaf(out, leaf);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		leaf = learnerTreeView.deserializeLeaf(in);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}
}
