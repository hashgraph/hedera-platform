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

package com.swirlds.common.sequence.set.internal;

import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.set.SequenceSet;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

/**
 * Boilerplate implementation for {@link SequenceSet SequenceSet}.
 *
 * @param <T>
 * 		the type of the element contained within this set
 */
public abstract class AbstractSequenceSet<T> implements SequenceSet<T> {

	/**
	 * Used to implement the set.
	 */
	private final SequenceMap<T, Boolean> map;

	/**
	 * Create a new abstract sequence set.
	 *
	 * @param lowestAllowedSequenceNumber
	 * 		the initial lowest permitted sequence in the set
	 * @param highestAllowedSequenceNumber
	 * 		the initial highest permitted sequence in the set
	 * @param getSequenceNumberFromEntry
	 * 		given an entry, extract the sequence number
	 */
	protected AbstractSequenceSet(
			final long lowestAllowedSequenceNumber,
			final long highestAllowedSequenceNumber,
			final ToLongFunction<T> getSequenceNumberFromEntry) {

		map = buildMap(lowestAllowedSequenceNumber, highestAllowedSequenceNumber, getSequenceNumberFromEntry);
	}

	/**
	 * Build a map that is used to implement the set.
	 *
	 * @param lowestAllowedSequenceNumber
	 * 		the initial lowest permitted sequence in the set
	 * @param highestAllowedSequenceNumber
	 * 		the initial highest permitted sequence in the set
	 * @param getSequenceNumberFromEntry
	 * 		given an entry, extract the sequence number
	 * @return a sequence map
	 */
	protected abstract SequenceMap<T, Boolean> buildMap(
			final long lowestAllowedSequenceNumber,
			final long highestAllowedSequenceNumber,
			final ToLongFunction<T> getSequenceNumberFromEntry);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean add(final T element) {
		throwArgNull(element, "element");
		return map.put(element, true) == null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean remove(final T element) {
		throwArgNull(element, "element");
		return map.remove(element) != null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean contains(final T element) {
		return map.containsKey(element);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeSequenceNumber(final long sequenceNumber, final Consumer<T> removedElementHandler) {
		map.removeSequenceNumber(sequenceNumber,
				removedElementHandler == null ? null : ((k, v) -> removedElementHandler.accept(k)));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<T> getEntriesWithSequenceNumber(final long sequenceNumber) {
		return map.getKeysWithSequenceNumber(sequenceNumber);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void purge(final long smallestAllowedSequenceNumber, final Consumer<T> removedElementHandler) {
		map.purge(smallestAllowedSequenceNumber,
				removedElementHandler == null ? null : ((k, v) -> removedElementHandler.accept(k)));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void expand(final long largestAllowedSequenceNumber) {
		map.expand(largestAllowedSequenceNumber);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSize() {
		return map.getSize();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getLowestAllowedSequenceNumber() {
		return map.getLowestAllowedSequenceNumber();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getHighestAllowedSequenceNumber() {
		return map.getHighestAllowedSequenceNumber();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		map.clear();
	}
}
