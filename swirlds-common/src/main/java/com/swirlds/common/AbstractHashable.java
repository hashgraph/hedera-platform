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

package com.swirlds.common;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Hashable;

/**
 * Boilerplate implementation for the Hashable interface.
 */
public abstract class AbstractHashable implements Hashable {

	private Hash hash = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Hash getHash() {
		return hash;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setHash(Hash hash) {
		this.hash = hash;
	}
}
