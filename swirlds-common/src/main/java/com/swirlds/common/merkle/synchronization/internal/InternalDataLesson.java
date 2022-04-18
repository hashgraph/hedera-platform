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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;

import java.io.IOException;
import java.util.List;

/**
 * This lesson contains data for an internal node.
 *
 * @param <T>
 * 		the type used by the view to represent a merkle node
 */
public class InternalDataLesson<T> implements SelfSerializable {

	private static final long CLASS_ID = 0xb76e98a8989c60a1L;

	private static final class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	private TeacherTreeView<T> teacherTreeView;
	private LearnerTreeView<T> learnerTreeView;
	private T internal;
	private List<Hash> queries;

	/**
	 * Zero arg constructor for constructable registry.
	 */
	public InternalDataLesson() {

	}

	/**
	 * Create a new lesson containing an internal node.
	 *
	 * @param teacherTreeView
	 * 		the teacher's view
	 * @param internal
	 * 		the internal node to include in the lesson
	 */
	public InternalDataLesson(final TeacherTreeView<T> teacherTreeView, final T internal) {
		this.teacherTreeView = teacherTreeView;
		this.internal = internal;
	}

	/**
	 * Create a new object that will be used to deserialize the lesson.
	 *
	 * @param learnerTreeView
	 * 		the learner's view
	 */
	public InternalDataLesson(final LearnerTreeView<T> learnerTreeView) {
		this.learnerTreeView = learnerTreeView;
	}

	/**
	 * Get the internal node described by the lesson.
	 *
	 * @return the node described by the lesson
	 */
	public T getInternal() {
		return internal;
	}

	/**
	 * Get the queries contained by this lesson (i.e. the hashes of the children of the internal node).
	 *
	 * @return a list of queries
	 */
	public List<Hash> getQueries() {
		return queries;
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
		teacherTreeView.serializeInternal(out, internal);
		out.writeSerializableList(teacherTreeView.getChildHashes(internal), false, true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		internal = learnerTreeView.deserializeInternal(in);
		queries = in.readSerializableList(MerkleInternal.MAX_CHILD_COUNT_UBOUND, false, Hash::new);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}
}