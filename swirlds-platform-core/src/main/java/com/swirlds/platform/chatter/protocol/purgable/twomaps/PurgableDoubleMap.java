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

package com.swirlds.platform.chatter.protocol.purgable.twomaps;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A map-like object that supports efficient purging.
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public class PurgableDoubleMap<K, V> extends AbstractPurgableDoubleMap<K, V> {

	private long smallestAllowedGeneration;

	/**
	 * Construct a purgable map.
	 *
	 * @param getGenerationFromKey
	 * 		a method that extracts the generation from a key
	 */
	public PurgableDoubleMap(final Function<K, Long> getGenerationFromKey) {
		super(getGenerationFromKey);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getPurgedGeneration() {
		return smallestAllowedGeneration;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void setSmallestAllowedGeneration(final long generation) {
		smallestAllowedGeneration = generation;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Map<K, V> buildDataMap() {
		return new HashMap<>();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Map<Long, Set<K>> buildGenerationMap() {
		return new HashMap<>();
	}
}
