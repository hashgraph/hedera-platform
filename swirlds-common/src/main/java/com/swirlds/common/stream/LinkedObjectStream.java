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

package com.swirlds.common.stream;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHashable;

/**
 * A LinkedObjectStream maintains a stream of objects that are linked by a RunningHash.
 * Each implementation of this interface can be linked to another LinkedObjectStream.
 * An implementation is required to call the appropriate methods on the next LinkedObjectStream object in the pipeline
 *
 * @param <T>
 * 		The type of RunningHashable objects processed by the LinkedObjectStream.
 */
public interface LinkedObjectStream<T extends RunningHashable> {

	/**
	 * Sets the current running hash.
	 * This can be called at initialization or if the running hash needs to be changed during operation.
	 * It is recommended to call this method after clear().
	 *
	 * @param hash
	 * 		a running Hash of objects with this type in history
	 */
	void setRunningHash(Hash hash);

	/**
	 * Adds a new object into this stream
	 *
	 * @param t
	 */
	void addObject(T t);

	/**
	 * Clear all objects currently in the stream and call clearStream() on the next stream if applicable.
	 * This method should not destroy the stream, which should be capable of accepting new objects after the clear
	 * has completed.
	 */
	void clear();

	/**
	 * Close the stream. After this method is called, adding additional objects to the stream may result in
	 * undefined behavior.
	 */
	void close();
}
