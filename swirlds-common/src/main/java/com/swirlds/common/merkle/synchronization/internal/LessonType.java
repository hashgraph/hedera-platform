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

/**
 * Describes different types of lessons.
 */
public final class LessonType {

	private LessonType() {

	}

	/**
	 * This lesson informs the learner that the node possessed by the learner has the correct data already.
	 */
	public static final byte NODE_IS_UP_TO_DATE = 0;

	/**
	 * This lesson contains all data required to reconstruct a leaf node node.
	 * Corresponds to {@link LeafDataLesson}.
	 */
	public static final byte LEAF_NODE_DATA = 1;

	/**
	 * This lesson contains all data required to reconstruct an internal node.
	 * Corresponds to {@link InternalDataLesson}.
	 */
	public static final byte INTERNAL_NODE_DATA = 2;

	/**
	 * This lesson describes the root of a subtree that needs a custom root to do reconnect.
	 * Corresponds to {@link CustomViewRootLesson}.
	 */
	public static final byte CUSTOM_VIEW_ROOT = 3;
}
