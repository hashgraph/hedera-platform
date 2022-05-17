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

package com.swirlds.virtualmap.datasource;

import com.swirlds.common.crypto.Hash;

/**
 * Represents a virtual internal node. An internal node is essentially just a path, and a hash.
 */
public final class VirtualInternalRecord extends VirtualRecord {
	/**
	 * Create a new {@link VirtualInternalRecord} with a null hash.
	 *
	 * @param path
	 *		must be non-negative
	 */
	public VirtualInternalRecord(long path) {
		super(path, null);
	}

	/**
	 * Create a new {@link VirtualInternalRecord} with both a path and a hash.
	 *
	 * @param path
	 *		Must be non-negative
	 * @param hash
	 *      The hash, which may be null.
	 */
	public VirtualInternalRecord(long path, Hash hash) {
		super(path, hash);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "VirtualInternalRecord{path=" + getPath() + ", hash=" + getHash() + "}";
	}
}
