/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.common.merkle.utility;

/**
 * <p>
 * Describes an object that contains a key.
 * </p>
 *
 * <p>
 * If a Keyed object implements {@link com.swirlds.common.FastCopyable FastCopyable}, a
 * {@link com.swirlds.common.FastCopyable#copy() copy()} operation is expected to copy the key.
 * </p>
 *
 * <p>
 * If a Keyed object implements {@link com.swirlds.common.crypto.Hashable Hashable}
 * then the key is expected to be hashed.
 * </p>
 *
 * <p>
 * If a Keyed object is capable of being serialized and deserialized
 * then the key is expected to be serialized and deserialized as well.
 * </p>
 *
 * @param <K>
 * 		the type of the key, each instance of this type must be effectively immutable
 * 		(that is, no operation after initial construction should be capable of changing the
 * 		behavior of {@link Object#hashCode()} or {@link Object#equals(Object)}.
 */
public interface Keyed<K> {

	/**
	 * Get the key that corresponds to this object. Must always return the last object supplied by
	 * {@link #setKey(Object)}, or null if {@link #setKey(Object)} has never been called.
	 *
	 * @return the key that corresponds to this object
	 */
	K getKey();

	/**
	 * Set the key that corresponds to this object.
	 *
	 * @param key
	 * 		the new key for this object
	 */
	void setKey(K key);

}
