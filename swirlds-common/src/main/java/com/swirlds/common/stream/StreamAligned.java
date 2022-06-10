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

package com.swirlds.common.stream;

/**
 * Describes an object that requires specific alignment within a stream.
 * Linguistically similar to, but in no way actually related to "streamlined".
 */
public interface StreamAligned {

	long NO_ALIGNMENT = Long.MIN_VALUE;

	/**
	 * Gets the stream alignment descriptor for the object, or {@link #NO_ALIGNMENT} if
	 * this object does not care about its stream alignment. If two or more sequential
	 * objects have the same stream alignment (excluding {@link #NO_ALIGNMENT})
	 * then those objects are grouped together.
	 *
	 * @return the stream alignment of the object
	 */
	default long getStreamAlignment() {
		return NO_ALIGNMENT;
	}
}
