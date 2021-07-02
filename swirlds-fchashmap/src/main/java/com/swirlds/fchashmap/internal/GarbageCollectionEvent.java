/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.fchashmap.internal;

/**
 * Represents an action that the garbage collector will take after a certain copy version is deleted.
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the map
 */
class GarbageCollectionEvent<K, V> {
	private final K key;
	private final MutationQueue<V> mutationQueue;
	private final long version;

	public GarbageCollectionEvent(final K key, final MutationQueue<V> mutationQueue, final long version) {
		this.key = key;
		this.mutationQueue = mutationQueue;
		this.version = version;
	}

	public K getKey() {
		return key;
	}

	public MutationQueue<V> getMutationQueue() {
		return mutationQueue;
	}

	public long getVersion() {
		return version;
	}

	public String toString() {
		return "[" + key + "@" + version + ": " + mutationQueue + "]";
	}
}
