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

package com.swirlds.jasperdb.collections;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * An implementation of ImmutableIndexedObjectList that stores its objects in a {@link TreeMap}.
 *
 * Serves as a reference implementation to check correctness of {@link ImmutableIndexedObjectListUsingArray},
 * whose performance profile is better suited for production.
 *
 * @param <T>
 * 		the type of the IndexedObject in the list
 */
@SuppressWarnings("unused")
public class ImmutableIndexedObjectListUsingMap<T extends IndexedObject> implements ImmutableIndexedObjectList<T> {
	/**
	 * Maps from self-reported index to list object.
	 */
	private final SortedMap<Integer, T> dataMap;

	/**
	 * Creates a new ImmutableIndexedObjectList from an existing array of objects.
	 */
	public ImmutableIndexedObjectListUsingMap(final T[] objects) {
		Objects.requireNonNull(objects);
		final SortedMap<Integer, T> map = new TreeMap<>();
		for (final T object : objects) {
			if (object != null) {
				map.put(object.getIndex(), object);
			}
		}
		dataMap = Collections.unmodifiableSortedMap(map);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ImmutableIndexedObjectListUsingMap<T> withAddedObject(final T newT) {
		if (newT == null) {
			return this;
		}

		final SortedMap<Integer, T> map = new TreeMap<>(dataMap);
		map.put(newT.getIndex(), newT);
		return new ImmutableIndexedObjectListUsingMap<>(Collections.unmodifiableSortedMap(map));
	}


	/**
	 * {@inheritDoc}
	 */
	public ImmutableIndexedObjectListUsingMap<T> withDeletedObjects(final Set<T> objectsToDelete) {
		if (objectsToDelete == null || objectsToDelete.isEmpty() || dataMap.isEmpty()) {
			return this;
		}

		final SortedMap<Integer, T> map = new TreeMap<>(dataMap);
		for (final T object : objectsToDelete) {
			if (object != null) {
				map.remove(object.getIndex());
			}
		}
		return new ImmutableIndexedObjectListUsingMap<>(Collections.unmodifiableSortedMap(map));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T getLast() {
		return dataMap.isEmpty() ? null : dataMap.get(dataMap.lastKey());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T get(final int objectIndex) {
		if (objectIndex < 0) {
			throw new IndexOutOfBoundsException("Cannot use negative index " + objectIndex);
		}
		return dataMap.get(objectIndex);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Stream<T> stream() {
		return dataMap.values().stream();
	}

	@Override
	public String toString() {
		return prettyPrintedIndices();
	}

	private ImmutableIndexedObjectListUsingMap(final SortedMap<Integer, T> dataMap) {
		this.dataMap = dataMap;
	}

	/**
	 * Get the number of items in this list
	 */
	@Override
	public int size() {
		return dataMap.size();
	}
}
