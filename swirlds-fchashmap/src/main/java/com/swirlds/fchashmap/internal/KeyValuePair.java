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

import java.util.Objects;

/**
 * A container for holding a key and a value. Used by {@link com.swirlds.fchashmap.FCOneToManyRelation}.
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public class KeyValuePair<K, V> {

	private final K key;
	private final V value;

	/**
	 * Create a new key value pair.
	 *
	 * @param key
	 * 		the key, null is not supported
	 * @param value
	 * 		the value
	 * @throws NullPointerException
	 * 		if the key is null
	 */
	public KeyValuePair(final K key, final V value) {
		if (key == null) {
			throw new NullPointerException("null keys are not supported");
		}
		this.key = key;
		this.value = value;
	}

	/**
	 * Get the key.
	 */
	public K getKey() {
		return key;
	}

	/**
	 * Get the value.
	 */
	public V getValue() {
		return value;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final KeyValuePair<?, ?> that = (KeyValuePair<?, ?>) o;
		return key.equals(that.key) && value.equals(that.value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		// Chosen to resemble Object.hashCode(Object...) but with a different prime number
		return key.hashCode() * 37 + Objects.hashCode(value.hashCode());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "(" + key + ", " + value + ")";
	}
}
