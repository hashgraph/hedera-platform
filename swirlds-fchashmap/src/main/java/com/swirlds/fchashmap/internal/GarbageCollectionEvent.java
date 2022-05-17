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

package com.swirlds.fchashmap.internal;

/**
 * Represents an action that the garbage collector will take after a certain copy version is deleted.
 *
 * @param <K>
 * 		the type of the key
 */
public class GarbageCollectionEvent<K> {

	private final K key;
	private final long version;

	/**
	 * Create a new garbage collection event.
	 *
	 * @param key
	 * 		the key that requires garbage colleciton
	 * @param version
	 * 		the version which triggers garbage collection when it is deleted
	 */
	public GarbageCollectionEvent(final K key, final long version) {
		this.key = key;
		this.version = version;
	}

	/**
	 * Get the key that requires cleanup.
	 */
	public K getKey() {
		return key;
	}

	/**
	 * Get the version which, after deletion, requires cleanup.
	 */
	public long getVersion() {
		return version;
	}

	public String toString() {
		return "[" + key + "@" + version + "]";
	}
}
