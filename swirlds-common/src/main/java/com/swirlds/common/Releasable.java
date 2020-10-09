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

package com.swirlds.common;

/**
 * A Releasable object is an object that expects to be deleted when it is done being used.
 * This paradigm is needed when objects manage resources that are not automatically cleaned
 * by the java garbage collector.
 */
public interface Releasable {

	/**
	 * Called when this object is no longer needed. This method is expected to free
	 * any external resources formally used by this object that the java garbage
	 * collector will not free.
	 *
	 * This method is expected to be idempotent - that is, calling this method multiple times
	 * should have the same effect as calling it exactly once. This method should also be thread safe.
	 */
	void release();

	/**
	 * Determines if an object has been released or not.
	 *
	 * @return Whether is has been released or not
	 */
	default boolean isReleased() {
		return false;
	}

	/**
	 * Throws an exception if {@link #isReleased()}} returns {@code true}
	 */
	default void throwIfReleased() {
		if (this.isReleased()) {
			throw new IllegalStateException("This operation is not permitted on a released object.");
		}
	}

}
