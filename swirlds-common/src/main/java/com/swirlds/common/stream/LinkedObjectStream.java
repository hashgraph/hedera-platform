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
