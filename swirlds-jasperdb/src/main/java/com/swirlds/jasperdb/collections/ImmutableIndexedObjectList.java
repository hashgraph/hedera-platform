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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

/**
 * An immutable list of indexed objects, containing at most one object at any given index.
 *
 * The {@link ImmutableIndexedObjectList#withAddedObject(IndexedObject)} and
 * {@link ImmutableIndexedObjectList#withDeletedObjects(Set)}} methods return shallow copies
 * of the list that result from applying the requested addition or deletion(s), leaving the
 * receiving list unchanged.
 *
 * @param <T>
 * 		the type of IndexedObject in the list
 */
@SuppressWarnings("unused")
public interface ImmutableIndexedObjectList<T extends IndexedObject> {
	/**
	 * Creates a new ImmutableIndexedObjectList with the union of the existing objects
	 * and the given new object, minus any existing object at the new object's index.
	 *
	 * Returns this list if given a null object.
	 *
	 * @param newT
	 * 		a new indexed object
	 * @return an immutable copy of this list plus newT, minus any existing element at newT's index
	 */
	ImmutableIndexedObjectList<T> withAddedObject(T newT);

	/**
	 * Creates a new ImmutableIndexedObjectList with the existing objects, minus any
	 * at indexes in a list of objects (indexes) to delete.
	 *
	 * @param objectsToDelete
	 * 		a non-null list of objects to delete
	 * @return an immutable copy of this list minus any existing elements at indices from the deletion list
	 */
	ImmutableIndexedObjectList<T> withDeletedObjects(Set<T> objectsToDelete);

	/**
	 * Gets the last object in this list.
	 *
	 * @return the object in this list with the greatest index, or null if the list is empty
	 */
	T getLast();

	/**
	 * Gets the object at given index.
	 *
	 * @return the unique object at the given index, null if this list has no such object
	 * @throws IndexOutOfBoundsException
	 * 		if the index is negative
	 */
	T get(final int objectIndex);

	/**
	 * Gets a stream containing all non-null objects in this list, sorted in ascending order
	 * of their self-reported index. (Note that an object's index need not be its position in
	 * the returned stream; for example, the index of the first object in the stream
	 * could be 7 and not 0.)
	 *
	 * @return a stream of this list's objects, sorted by self-reported index
	 */
	Stream<T> stream();

	/**
	 * Useful debugging helper that prints just the indices of the objects in this list.
	 *
	 * @return a pretty-printed list of the self-reported indices in the list
	 */
	default String prettyPrintedIndices() {
		return Arrays.toString(stream().mapToInt(IndexedObject::getIndex).toArray());
	}

	/**
	 * Get the number of items in this list
	 */
	int size();
}
