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

import com.swirlds.virtualmap.VirtualKey;

import java.io.Closeable;

/**
 * A set-like data structure for virtual map keys. Keys can be added but never removed.
 *
 * @param <K>
 * 		the type of the key
 */
public interface VirtualKeySet<K extends VirtualKey<? super K>> extends Closeable {

	/**
	 * Add a key to the set.
	 *
	 * @param key
	 * 		the key to add
	 */
	void add(K key);

	/**
	 * Check if a key is contained within the set.
	 *
	 * @param key
	 * 		the key in question
	 * @return true if the key is contained in the set
	 */
	boolean contains(K key);

	/**
	 * {@inheritDoc}
	 */
	void close();
}
