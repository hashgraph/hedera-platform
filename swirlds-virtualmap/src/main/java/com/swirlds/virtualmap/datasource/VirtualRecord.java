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

package com.swirlds.virtualmap.datasource;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.virtualmap.internal.Path;

import java.util.Objects;

/**
 * Base class for {@link VirtualInternalRecord} and {@link VirtualLeafRecord}. Records are
 * data stored in the {@link VirtualDataSource} and in the {@code VirtualNodeCache}.
 * These records are mutable. Since the cache maintains versions across rounds (copies), it is necessary to
 * create new copies of the VirtualRecord in each round in which it is mutated.
 */
public abstract class VirtualRecord implements Hashable {
	/**
	 * The path for this record. The path can change over time as nodes are added or removed.
	 */
	private volatile long path;

	/**
	 * The hash for this record. May be null if the record is dirty.
	 */
	private volatile Hash hash;

	/**
	 * Create a new VirtualRecord.
	 *
	 * @param path
	 * 		Must be non-negative, or {@link Path#INVALID_PATH}.
	 */
	protected VirtualRecord(long path, Hash hash) {
		assert path == Path.INVALID_PATH || path >= 0;
		this.path = path;
		this.hash = hash;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void setHash(Hash hash) {
		this.hash = hash;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final Hash getHash() {
		return hash;
	}

	/**
	 * Gets the path for this node.
	 *
	 * @return the path. Will not be INVALID_PATH.
	 */
	public final long getPath() {
		return path;
	}

	/**
	 * Sets the path for this node.
	 *
	 * @param path
	 * 		must be non-negative
	 */
	public final void setPath(long path) {
		assert path >= 0;
		this.path = path;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof VirtualRecord)) {
			return false;
		}

		final VirtualRecord that = (VirtualRecord) o;
		return path == that.path && Objects.equals(hash, that.hash);
	}

	@Override
	public int hashCode() {
		return Objects.hash(path, hash);
	}
}
