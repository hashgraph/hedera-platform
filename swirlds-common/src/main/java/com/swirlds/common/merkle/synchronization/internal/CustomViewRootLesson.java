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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;

/**
 * This lesson describes a root of a subtree that requires a custom view to be used for reconnect.
 */
public class CustomViewRootLesson implements SelfSerializable {

	private static final long CLASS_ID = 0x1defd7fb7e8c303fL;

	private long rootClassId;

	private static final class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	/**
	 * Zero arg constructor for constructable registry.
	 */
	public CustomViewRootLesson() {

	}

	/**
	 * Create a new custom view root lesson.
	 *
	 * @param rootClassId
	 * 		the class ID of the node at teh root of the subtree
	 */
	public CustomViewRootLesson(final long rootClassId) {
		this.rootClassId = rootClassId;
	}

	/**
	 * Get the class ID of the node at the root of the subtree.
	 */
	public long getRootClassId() {
		return rootClassId;
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
		out.writeLong(rootClassId);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		rootClassId = in.readLong();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}
}
