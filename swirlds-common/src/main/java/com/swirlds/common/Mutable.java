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

package com.swirlds.common;

import com.swirlds.common.exceptions.MutabilityException;

/**
 * Describes an object that may be mutable or immutable.
 */
public interface Mutable {

	/**
	 * Determines if an object is immutable or not.
	 *
	 * @return Whether is immutable or not
	 */
	boolean isImmutable();

	/**
	 * Determines if an object is mutable or not.
	 *
	 * @return Whether the object is immutable or not
	 */
	default boolean isMutable() {
		return !isImmutable();
	}

	/**
	 * @throws MutabilityException
	 * 		if {@link #isImmutable()}} returns {@code true}
	 */
	default void throwIfImmutable() {
		throwIfImmutable("This operation is not permitted on an immutable object.");
	}

	/**
	 * @param errorMessage
	 * 		an error message for the exception
	 * @throws MutabilityException
	 * 		if {@link #isImmutable()}} returns {@code true}
	 */
	default void throwIfImmutable(final String errorMessage) {
		if (this.isImmutable()) {
			throw new MutabilityException(errorMessage);
		}
	}
}
