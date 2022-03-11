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

package com.swirlds.jasperdb.files.hashmap;

import com.swirlds.virtualmap.VirtualKey;

import java.util.Objects;

/**
 * A simple linked-list of "mutations" for a bucket.
 *
 * @param <K>
 *     The key.
 */
class BucketMutation<K extends VirtualKey<? super K>> {
	private final K key;
	private long value;
	private BucketMutation<K> next;

	BucketMutation(K key, long value) {
		this.key = Objects.requireNonNull(key);
		this.value = value;
		this.next = null;
	}

	/**
	 * To be called on the "head" of the list, updates an existing mutation
	 * with the same key or creates a new mutation at the end of the list.
	 * @param key
	 * 		The key (cannot be null)
	 * @param value
	 * 		The value (cannot be null)
	 */
	void put(K key, long value) {
		BucketMutation<K> mutation = this;
		while (true) {
			if (mutation.key.equals(key)) {
				mutation.value = value;
				return;
			} else if (mutation.next != null) {
				mutation = mutation.next;
			} else {
				mutation.next = new BucketMutation<>(key, value);
				return;
			}
		}
	}

	/**
	 * Computes the size of the list from this point to the end of the list.
	 * @return
	 * 		The number of mutations, including this one, from here to the end.
	 */
	int size() {
		int size = 1;
		BucketMutation<K> mutation = next;
		while (mutation != null) {
			size++;
			mutation = mutation.next;
		}
		return size;
	}

	/**
	 * Visit each mutation in the list, starting from this mutation.
	 * @param consumer
	 * 		The callback. Cannot be null.
	 */
	void forEachKeyValue(MutationCallback<K> consumer) {
		BucketMutation<K> mutation = this;
		while (mutation != null) {
			consumer.accept(mutation.key, mutation.value);
			mutation = mutation.next;
		}
	}

	/**
	 * A simple callback for {@link BucketMutation#forEachKeyValue(MutationCallback)}.
	 * @param <K>
	 *     The key.
	 */
	interface MutationCallback<K extends VirtualKey<? super K>> {
		void accept(K key, long value);
	}
}
