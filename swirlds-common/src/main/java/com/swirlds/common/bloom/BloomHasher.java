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

package com.swirlds.common.bloom;

import com.swirlds.common.io.SelfSerializable;

/**
 * An object that hashes elements for the bloom filter.
 *
 * @param <T>
 * 		the type of the object in the bloom filter
 */
public interface BloomHasher<T> extends SelfSerializable {

	/**
	 * Hash an element for the bloom filter
	 *
	 * @param element
	 * 		the element to hash. Null values may be optionally supported.
	 * @param maxHash
	 * 		the maximum permitted value of a hash
	 * @param hashes
	 * 		an array into which the hashes must be written. The number of hashes
	 * 		computed must equal the length of the array.
	 * @throws NullPointerException
	 * 		if this implementation does not support null elements but a null
	 * 		element is provided
	 */
	void hash(T element, long maxHash, long[] hashes);

}
