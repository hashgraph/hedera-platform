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

import com.swirlds.common.io.SelfSerializable;

public interface SerializableHashable extends Hashable, SelfSerializable {
	/**
	 * Should return a hash of the object if it has been calculated. If the method returns null, an external utility
	 * will serialize the object, including its class ID and version, and set its hash by calling
	 * {@link #setHash(Hash)}.
	 * <p>
	 * If the class wants to implement its own hashing, this method should never return null. The class should
	 * calculate its own hash, which should include the class ID and version, and return that hash by this method.
	 *
	 * @return the hash of the object
	 */
	@Override
	Hash getHash();

	/**
	 * {@inheritDoc}
	 */
	@Override
	void setHash(Hash hash);
}
