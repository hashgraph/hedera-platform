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

package com.swirlds.common.sequence.set;

import com.swirlds.common.sequence.map.ConcurrentSequenceMap;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.set.internal.AbstractSequenceSet;

import java.util.function.ToLongFunction;

import static com.swirlds.common.sequence.map.SequenceMap.DEFAULT_HIGHEST_ALLOWED_SEQUENCE_NUMBER;
import static com.swirlds.common.sequence.map.SequenceMap.DEFAULT_LOWEST_ALLOWED_SEQUENCE_NUMBER;

/**
 * A thread safe {@link SequenceSet}.
 *
 * @param <T>
 * 		the type of the element contained within this set
 */
public class ConcurrentSequenceSet<T> extends AbstractSequenceSet<T> {

	/**
	 * Create a thread safe free {@link SequenceSet}.
	 *
	 * @param getSequenceNumberFromEntry
	 * 		given an entry, extract the sequence number
	 */
	public ConcurrentSequenceSet(final ToLongFunction<T> getSequenceNumberFromEntry) {
		this(DEFAULT_LOWEST_ALLOWED_SEQUENCE_NUMBER,
				DEFAULT_HIGHEST_ALLOWED_SEQUENCE_NUMBER,
				getSequenceNumberFromEntry);
	}

	/**
	 * Create a new thread safe {@link SequenceSet}.
	 *
	 * @param lowestAllowedSequenceNumber
	 * 		the initial lowest permitted sequence in the set
	 * @param highestAllowedSequenceNumber
	 * 		the initial highest permitted sequence in the set
	 * @param getSequenceNumberFromEntry
	 * 		given an entry, extract the sequence number
	 */
	public ConcurrentSequenceSet(
			final long lowestAllowedSequenceNumber,
			final long highestAllowedSequenceNumber,
			final ToLongFunction<T> getSequenceNumberFromEntry) {

		super(lowestAllowedSequenceNumber, highestAllowedSequenceNumber, getSequenceNumberFromEntry);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected SequenceMap<T, Boolean> buildMap(
			final long lowestAllowedSequenceNumber,
			final long highestAllowedSequenceNumber,
			final ToLongFunction<T> getSequenceNumberFromEntry) {
		return new ConcurrentSequenceMap<>(
				lowestAllowedSequenceNumber,
				highestAllowedSequenceNumber,
				getSequenceNumberFromEntry);
	}
}
