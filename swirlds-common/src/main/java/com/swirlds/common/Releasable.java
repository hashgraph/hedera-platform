/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common;

import com.swirlds.common.exceptions.ReferenceCountException;

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
	 *
	 * @throws ReferenceCountException
	 * 		if this object is released
	 */
	default void throwIfReleased() {
		throwIfReleased("This operation is not permitted on a released object.");
	}

	/**
	 * Throws an exception if {@link #isReleased()}} returns {@code true}
	 *
	 * @param errorMessage
	 * 		an error message for the exception
	 * @throws ReferenceCountException
	 * 		if this object is released
	 */
	default void throwIfReleased(final String errorMessage) {
		if (this.isReleased()) {
			throw new ReferenceCountException(errorMessage);
		}
	}

}
