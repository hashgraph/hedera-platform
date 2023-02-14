/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.common.utility;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A queue of objects. Supports O(1) operations to add or remove elements from the front/back of the
 * queue (as long as there is capacity) and O(1) operations for random access/update.
 *
 * <p>This data structure is not thread safe.
 *
 * @param <T> the type of data stored in the queue
 */
public class RandomAccessDeque<T> implements Iterable<T> {

    private static final int DEFAULT_CAPACITY = 1024;

    /**
     * Stores all objects in the deque. Is replaced with a new and larger array when deque runs out
     * of capacity.
     */
    private Object[] data;

    /** The total number of elements in the deque. */
    private int size;

    /**
     * The index into {@link #data} where the first element in the deque can be found if {@link
     * #size} is nonzero. If {@link #size} is zero then this will be equal to {@link #nextIndex}.
     */
    private int firstIndex;

    /**
     * The index into {@link #data} where the next element added to the end of the deque will be
     * written. If the {@link #size} is nonzero, then nextIndex-1 will be the index into {@link
     * #data} where the last element in the deque can be found.
     */
    private int nextIndex;

    /** Create a new queue. */
    public RandomAccessDeque() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Create a new queue.
     *
     * @param capacity the capacity of the queue
     */
    public RandomAccessDeque(final int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException(
                    "capacity must be greater than 0, requested capacity is " + capacity);
        }

        data = new Object[capacity];
    }

    /** Copy the buffer into a new larger buffer, expanding the current capacity. */
    private void expand() {
        final Object[] newData = new Object[data.length * 2];

        int firstSegmentLength = data.length - firstIndex;
        int secondSegmentLength = data.length - firstSegmentLength;

        System.arraycopy(data, firstIndex, newData, 0, firstSegmentLength);
        System.arraycopy(data, 0, newData, firstSegmentLength, secondSegmentLength);

        firstIndex = 0;
        nextIndex = data.length;
        data = newData;
    }

    /**
     * Get the number of objects in this queue.
     *
     * @return the number of objects in this queue
     */
    public int size() {
        return size;
    }

    /**
     * Check if this queue is full. If full, the next addition will cause the queue to resize
     * itself.
     *
     * @return true if this queue is totally full
     */
    public boolean isFull() {
        return data.length - size == 0;
    }

    /**
     * Add an element to the beginning of the queue. O(1) if there is capacity, O(N) if the queue
     * needs to be expanded.
     *
     * @param element the element to add
     */
    public void addFirst(final T element) {
        if (isFull()) {
            expand();
        }

        firstIndex = (firstIndex - 1 + data.length) % data.length;
        data[firstIndex] = element;
        size++;
    }

    /**
     * Add an element to the end of the queue. O(1) if there is capacity, O(N) if the queue needs to
     * be expanded.
     *
     * @param value the value to add
     */
    public void addLast(final T value) {
        if (isFull()) {
            expand();
        }

        data[nextIndex] = value;
        nextIndex = (nextIndex + 1) % data.length;
        size++;
    }

    /**
     * Remove the element from the beginning of the queue. O(1)
     *
     * @return the element that was removed
     */
    public T removeFirst() {
        final T removed = set(0, null);
        firstIndex = (firstIndex + 1) % data.length;
        size--;
        return removed;
    }

    /**
     * Remove the element from the end of the queue. O(1)
     *
     * @return the element that was removed
     */
    public T removeLast() {
        final T removed = set(size() - 1, null);
        nextIndex = (nextIndex - 1 + data.length) % data.length;
        size--;
        return removed;
    }

    /**
     * Get an element at an index relative to the first element.
     *
     * @param index the index to get
     * @return the element at the specified index
     */
    @SuppressWarnings("unchecked")
    public T get(final int index) {
        if (index < 0) {
            throw new IllegalArgumentException("negative indices not supported");
        }
        if (index >= size()) {
            throw new NoSuchElementException(
                    "can't get element at index " + index + " in queue of size " + size());
        }
        return (T) data[(firstIndex + index) % data.length];
    }

    /**
     * Get the first element in the queue.
     *
     * @return the first element in the queue
     */
    public T getFirst() {
        return get(0);
    }

    /**
     * Get the last element in the queue.
     *
     * @return the last element in the queue
     */
    public T getLast() {
        return get(size() - 1);
    }

    /**
     * Set an element at an index relative to the first element.
     *
     * @param index the index to set
     * @param value the value to put at the specified index
     * @return the previous value
     */
    @SuppressWarnings("unchecked")
    public T set(final int index, final T value) {
        if (index < 0) {
            throw new IllegalArgumentException("negative indices not supported");
        }
        if (index >= size()) {
            throw new IllegalStateException(
                    "can't put element at index " + index + " in queue of size " + size());
        }
        final int adjustedIndex = (firstIndex + index) % data.length;
        final T prev = (T) data[adjustedIndex];
        data[adjustedIndex] = value;
        return prev;
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<T> iterator() {
        return iterator(0);
    }

    /**
     * Returns an iterator that walks over all elements starting at a given index.
     *
     * @param startingIndex the index of the first value to return with the iterator
     * @return an iterator that walks over objects starting at an index
     */
    public Iterator<T> iterator(final int startingIndex) {
        return new Iterator<>() {
            private int offset = startingIndex;

            @Override
            public boolean hasNext() {
                return offset < size();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                final T next = get(offset);
                offset++;
                return next;
            }
        };
    }
}
