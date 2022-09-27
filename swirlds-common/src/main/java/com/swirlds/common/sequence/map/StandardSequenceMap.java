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

package com.swirlds.common.sequence.map;

import com.swirlds.common.sequence.map.internal.AbstractSequenceMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * A lock free implementation of {@link SequenceMap}.
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public class StandardSequenceMap<K, V> extends AbstractSequenceMap<K, V> {

	private long lowestAllowedSequenceNumber;
	private long highestAllowedSequenceNumber;

	/**
	 * Construct a {@link SequenceMap}.
	 *
	 * @param getSequenceNumberFromKey
	 * 		a method that extracts the sequence number from a key
	 */
	public StandardSequenceMap(final ToLongFunction<K> getSequenceNumberFromKey) {
		this(DEFAULT_LOWEST_ALLOWED_SEQUENCE_NUMBER, DEFAULT_HIGHEST_ALLOWED_SEQUENCE_NUMBER, getSequenceNumberFromKey);
	}

	/**
	 * Construct a {@link SequenceMap}.
	 *
	 * @param lowestAllowedSequenceNumber
	 * 		the lowest allowed sequence number
	 * @param highestAllowedSequenceNumber
	 * 		the highest allowed sequence number
	 * @param getSequenceNumberFromKey
	 * 		a method that extracts the sequence number from a key
	 */
	public StandardSequenceMap(
			final long lowestAllowedSequenceNumber,
			final long highestAllowedSequenceNumber,
			final ToLongFunction<K> getSequenceNumberFromKey) {

		super(lowestAllowedSequenceNumber, highestAllowedSequenceNumber, getSequenceNumberFromKey);
		this.lowestAllowedSequenceNumber = lowestAllowedSequenceNumber;
		this.highestAllowedSequenceNumber = highestAllowedSequenceNumber;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getLowestAllowedSequenceNumber() {
		return lowestAllowedSequenceNumber;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getHighestAllowedSequenceNumber() {
		return highestAllowedSequenceNumber;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void setLowestAllowedSequenceNumber(final long sequenceNumber) {
		lowestAllowedSequenceNumber = sequenceNumber;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void setHighestAllowedSequenceNumber(final long sequenceNumber) {
		this.highestAllowedSequenceNumber = sequenceNumber;
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
	protected Map<Long, Set<K>> buildSequenceMap() {
		return new HashMap<>();
	}
}
