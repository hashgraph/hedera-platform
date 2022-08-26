/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

	@Override
	public void clear() {
		super.clear();
		smallestAllowedGeneration = 0;
	}
}
