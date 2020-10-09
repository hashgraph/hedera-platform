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

package com.swirlds.common.crypto;

public interface Hashable {

	/**
	 * This method returns an up to date hash of the contents of the object if it has been computed, or
	 * null if the hash has not yet been computed. This method must never return a non-null hash that
	 * does not match the contents of this object.
	 *
	 * Some object types may decide to self compute their own hash. In that case, this method should cause an
	 * up to date hash to be generated and returned. Objects that self compute their hash should never return
	 * null.
	 *
	 * For objects that do not self compute their own hash, this method will simply return the last hash passed
	 * to {@link Hashable#setHash(Hash)}, or null if {@link Hashable#setHash(Hash)} has never been called.
	 *
	 * @return a valid hash or null
	 */
	Hash getHash();

	/**
	 * This method is used to set the hash of an object.
	 *
	 * Some object types may decide to self compute their own hash. Those objects throw an unsupported exception
	 * if this method is called. It is guaranteed that as long as the following conditions hold then this method
	 * will never be called.
	 *
	 * 1: {@link Hashable#getHash()} never returns null.
	 * 2: {@link Hashable#invalidateHash()} is overridden so that it does not call setHash(null)
	 *
	 * @param hash
	 * 		the hash of the object
	 * @throws UnsupportedOperationException
	 * 		if this object is self hashing
	 */
	void setHash(Hash hash);

	/**
	 * This method must be called when an object is mutated in a way that makes the current hash no longer valid.
	 *
	 * Objects that are self hashing should override this method and provide a no-op implementation that does not call
	 * setHash(null).
	 */
	default void invalidateHash() {
		setHash(null);
	}
}
