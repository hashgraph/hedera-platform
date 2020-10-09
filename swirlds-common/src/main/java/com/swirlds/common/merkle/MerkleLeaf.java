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

package com.swirlds.common.merkle;

import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.io.SelfSerializable;

/**
 * A Merkle Leaf has the following properties:
 * <ul>
 *     <li>Has only data, no children</li>
 *     <li>Data can be internal or external</li>
 * </ul>
 */
public interface MerkleLeaf extends MerkleNode, SerializableHashable {

	/**
	 * Determine if the object has external memory stored
	 * like in a database, that is part of the state
	 *
	 * @return Whether the data is external or not
	 */
	default boolean isDataExternal() {
		return false;
	}

	@Override
	default boolean isLeaf() {
		return true;
	}

	/**
	 * Do not override this method.
	 *
	 * Overriding this method will lead to undefined behavior in various merkle algorithms.
	 */
	@Override
	boolean equals(Object that);
}
