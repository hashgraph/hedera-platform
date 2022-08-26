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
	 * Called when this object is no longer needed.
	 */
	default void release() {
		// override if needed
	}

	/**
	 * Determines if an object has been fully released and garbage collected.
	 *
	 * @return Whether is has been released or not
	 */
	default boolean isDestroyed() {
		return false;
	}

	/**
	 * Throws an exception if {@link #isDestroyed()}} returns {@code true}
	 *
	 * @throws ReferenceCountException
	 * 		if this object is destroyed
	 */
	default void throwIfDestroyed() {
		throwIfDestroyed("This operation is not permitted on a destroyed object.");
	}

	/**
	 * Throws an exception if {@link #isDestroyed()}} returns {@code true}
	 *
	 * @param errorMessage
	 * 		an error message for the exception
	 * @throws ReferenceCountException
	 * 		if this object is destroyed
	 */
	default void throwIfDestroyed(final String errorMessage) {
		if (this.isDestroyed()) {
			throw new ReferenceCountException(errorMessage);
		}
	}
}
