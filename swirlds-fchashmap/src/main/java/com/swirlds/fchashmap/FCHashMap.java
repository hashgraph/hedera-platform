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

package com.swirlds.fchashmap;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.exceptions.ReferenceCountException;
import com.swirlds.common.utility.ValueReference;
import com.swirlds.fchashmap.internal.FCHashMapEntrySet;
import com.swirlds.fchashmap.internal.GarbageCollectionEvent;
import com.swirlds.fchashmap.internal.Mutation;

import java.util.AbstractMap;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>
 * A map that with {@link java.util.HashMap HashMap} like O(1) performance that provides {@link FastCopyable} semantics.
 * </p>
 *
 * <p>
 * All operations are thread safe if performed simultaneously on different copies of the map.
 * </p>
 *
 * <p>
 * It is safe to read and write simultaneously to the mutable copy of the map with multiple threads as long as
 * the read operations are not performed concurrently with write operations on the same key. {@link #size} may return
 * incorrect results if executed concurrently with an operation that modifies the size.
 * </p>
 *
 * <p>
 * It is not thread safe to perform read/write operations on a copy of this this map while that copy is being released.
 * </p>
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public class FCHashMap<K, V> extends AbstractMap<K, V> implements FastCopyable {

	/**
	 * Monotonically increasing version number that is incremented every time copy() is called on the mutable copy.
	 */
	private final long version;

	/**
	 * Is this object a mutable object?
	 */
	private boolean immutable;

	/**
	 * Contains the data of this map and all copies that have not been garbage collected.
	 */
	private final Map<K, Mutation<V>> data;

	/**
	 * All copies of the map that have not yet been garbage collected. New copies are added to the end,
	 * old copies are removed from the beginning.
	 */
	private final Deque<FCHashMap<K, V>> copies;

	/**
	 * Contains a record of things that need to be garbage collected.
	 */
	private final Deque<GarbageCollectionEvent<K>> garbageCollectionEvents;

	/**
	 * Prevents multiple threads from attempting to do simultaneous garbage collection.
	 */
	private final Lock garbageCollectionLock;

	/**
	 * The current size of the map.
	 */
	private final AtomicInteger size;

	/**
	 * Tracks if this particular object has been deleted.
	 */
	private final AtomicBoolean released = new AtomicBoolean(false);

	/**
	 * Create a new FCHashMap.
	 */
	public FCHashMap() {
		this(0);
	}

	/**
	 * Create a new FCHashMap.
	 *
	 * @param capacity
	 * 		the initial capacity of the map
	 */
	public FCHashMap(final int capacity) {

		data = new ConcurrentHashMap<>(capacity);

		copies = new ConcurrentLinkedDeque<>();

		garbageCollectionEvents = new ConcurrentLinkedDeque<>();
		garbageCollectionLock = new ReentrantLock();

		immutable = false;
		version = 0;
		size = new AtomicInteger(0);

		copies.add(this);
	}

	/**
	 * Copy constructor.
	 *
	 * @param that
	 * 		the map to copy
	 */
	protected FCHashMap(final FCHashMap<K, V> that) {
		data = that.data;
		copies = that.copies;
		garbageCollectionLock = that.garbageCollectionLock;
		garbageCollectionEvents = that.garbageCollectionEvents;
		size = new AtomicInteger(that.size.get());

		immutable = false;
		that.immutable = true;
		version = that.version + 1;

		copies.add(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public FCHashMap<K, V> copy() {
		throwIfImmutable();
		throwIfReleased();
		return new FCHashMap<>(this);
	}

	/**
	 * Exposed for testing. Get the total number of copies that have not been fully garbage collected (including
	 * copies not eligible for garbage collection).
	 *
	 * @return the number of un-garbage-collected copies
	 */
	protected int copyCount() {
		return copies.size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isImmutable() {
		return this.immutable;
	}

	/**
	 * Use this to clean up resources held by this copy.
	 * Failure to call delete on a copy before it is garbage collected will result in a memory leak.
	 *
	 * Not thread safe.
	 * Must not be called at the same time another thread is attempting to read from this copy.
	 */
	@Override
	public synchronized void release() {
		final boolean previouslyReleased = released.getAndSet(true);
		if (previouslyReleased) {
			throw new ReferenceCountException("this object has already been released");
		}
		doGarbageCollection();
	}

	/**
	 * Check to see if this copy has been deleted.
	 */
	@Override
	public boolean isReleased() {
		return released.get();
	}

	/**
	 * Perform garbage collection on this copy of the map.
	 */
	private void doGarbageCollection() {
		if (!garbageCollectionLock.tryLock()) {
			// Another thread is currently doing garbage collection. That thread will do GC
			// for this copy, or else the next release of a copy will do GC for this copy.
			return;
		}

		try {
			final Iterator<FCHashMap<K, V>> iterator = copies.iterator();

			while (iterator.hasNext()) {
				final FCHashMap<K, V> copy = iterator.next();
				if (!copy.isReleased()) {
					// Stop when the first unreleased copy is discovered.
					return;
				}

				GarbageCollectionEvent<K> event;
				while ((event = garbageCollectionEvents.peekFirst()) != null) {
					if (event.getVersion() > copy.version) {
						// Stop when the first event from the next version is discovered.
						break;
					}

					garbageCollectionEvents.pop();
					cleanOldMutations(event.getKey(), copy.version);
				}
				iterator.remove();
			}
		} finally {
			garbageCollectionLock.unlock();
		}
	}

	/**
	 * Given a queue and a version, remove mutations that are no longer needed by any copies.
	 *
	 * @param key
	 * 		the key that requires garbage collection
	 * @param version
	 * 		the version of the map that has been released
	 */
	private void cleanOldMutations(final K key, final long version) {
		data.compute(key, (final K k, final Mutation<V> mutationHead) -> {
			if (mutationHead == null) {
				return null;
			}

			Mutation<V> parent = mutationHead;
			Mutation<V> target = parent.getPrevious();

			while (target != null) {

				// truncate all older mutations
				if (target.getVersion() <= version) {
					parent.setPrevious(null);
					break;
				}

				parent = target;
				target = parent.getPrevious();
			}

			if (mutationHead.getPrevious() == null && mutationHead.getValue() == null) {
				// entry can be deleted if just a single deletion record remains
				return null;
			}

			return mutationHead;
		});
	}

	/**
	 * Register an operation that causes the need for future garbage collection
	 *
	 * @param key
	 * 		the key that points to a list of mutations that require garbage collection
	 * @param version
	 * 		that version that, when deleted, will require garbage collection to be done
	 */
	private void registerGarbageCollectionEvent(final K key, final long version) {
		garbageCollectionEvents.addLast(new GarbageCollectionEvent<>(key, version));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int size() {
		return size.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean containsKey(final Object key) {
		final Mutation<V> mutation = getMutationForCurrentVersion((K) key);
		return mutation != null && mutation.getValue() != null;
	}

	/**
	 * Look up the most recent mutation that does not exceed this copy of the map's current version.
	 *
	 * @param key
	 * 		look up the mutation for this key
	 * @return The mutation that corresponds to this version. May be null if the key is not in the map at this version.
	 */
	protected Mutation<V> getMutationForCurrentVersion(final K key) {
		Mutation<V> mutation = data.get(key);

		while (mutation != null && mutation.getVersion() > version) {
			mutation = mutation.getPrevious();
		}
		return mutation;
	}

	/**
	 * Update the value for a key at this version.
	 *
	 * @param key
	 * 		the key associated that will hold the new value
	 * @param value
	 * 		the new value, or null if this operation signifies a deletion.
	 * @return the original value, or null if originally deleted
	 */
	private V mutate(final K key, final V value) {
		throwIfImmutable();

		final ValueReference<V> originalValueReference = new ValueReference<>();
		final ValueReference<Boolean> requiresGarbageCollection = new ValueReference<>(false);

		// update the value in the list of mutations
		data.compute(key, (final K k, final Mutation<V> mutationHead) -> {
			originalValueReference.setValue(mutationHead == null ? null : mutationHead.getValue());

			final Mutation<V> mutation;
			if (mutationHead != null && mutationHead.getVersion() == version) {
				// mutation for this version already exists
				mutation = mutationHead;
				mutation.setValue(value);
			} else {
				// mutation for this version does not yet exist
				mutation = new Mutation<>(version, value, mutationHead);

				if (mutationHead != null) {
					// If mutationHead is not null, then this list now contains at least two entries. All lists
					// with more than one entry will eventually require garbage collection.
					requiresGarbageCollection.setValue(true);
				}
			}

			if (value == null && mutation.getPrevious() == null) {
				// If the only remaining mutation is a deletion then it is safe to remove the key from the map
				return null;
			}

			return mutation;
		});

		// update size of the map
		final V originalValue = originalValueReference.getValue();
		if (originalValue == null && value != null) {
			size.getAndIncrement();
		} else if (originalValue != null && value == null) {
			size.getAndDecrement();
		}

		if (requiresGarbageCollection.getValue()) {
			// Set up future garbage collection. This is called iff the mutate operation caused an additional
			// mutation to be added to the list. Once all copies of the map before the current version
			// are released, no mutations before the current mutations will be reachable. So request
			// a garbage collection operation on this list of mutations when the version right before
			// the current version is released.
			registerGarbageCollectionEvent(key, version - 1);
		}

		return originalValue;
	}

	/**
	 * Returns the version of the copy.
	 *
	 * @return the version of the copy
	 */
	public long version() {
		return version;
	}

	/**
	 * Not thread safe on an immutable copy of the map if it is possible that another thread may have deleted the
	 * map copy. Map deletion and reads against the map must be externally synchronized. The function hasBeenDeleted()
	 * can be used to check to see if the copy has been deleted.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public V get(final Object key) {
		if (key == null) {
			throw new NullPointerException("Null keys are not allowed");
		}
		final Mutation<V> mutation = getMutationForCurrentVersion((K) key);
		return mutation == null ? null : mutation.getValue();
	}

	/**
	 * A value from an {@link FCHashMap} that is safe to modify. Return type of {@link #getForModify(Object)}.
	 *
	 * @param value
	 * 		the value from the FCHashMap, or null if no value exists
	 * @param original
	 * 		the original value that was copied. Equal to value if there was no copying performed
	 * @param <V>
	 * 		the type of the value
	 */
	public record ModifiableValue<V>(V value, V original) {

	}

	/**
	 * <p>
	 * Get a value that is safe to directly modify. If value has been modified this round then return it.
	 * If value was modified in a previous round, call {@link FastCopyable#copy()} on it, insert it into
	 * the map, and return it. If the value is null, then return null.
	 * </p>
	 *
	 * <p>
	 * It is not necessary to manually re-insert the returned value back into the map.
	 * </p>
	 *
	 * <p>
	 * This method is only permitted to be used on maps that contain values that implement {@link FastCopyable}.
	 * Using this method on maps that contain values that do not implement {@link FastCopyable} will
	 * result in undefined behavior.
	 * </p>
	 *
	 * @param key
	 * 		the key
	 * @return a {@link ModifiableValue} that contains a value is safe to directly modify, or null if the key
	 * 		is not in the map
	 */
	@SuppressWarnings("unchecked")
	public ModifiableValue<V> getForModify(final K key) {
		final ValueReference<V> original = new ValueReference<>();

		final Mutation<V> mutation = data.compute(key, (final K k, final Mutation<V> mutationHead) -> {
			if (mutationHead == null) {
				return null;
			}

			original.setValue(mutationHead.getValue());

			if (mutationHead.getVersion() == version || mutationHead.getValue() == null) {
				return mutationHead;
			}

			return new Mutation<>(version, ((FastCopyable) mutationHead.getValue()).copy(), mutationHead);
		});


		if (mutation == null || mutation.getValue() == null) {
			return null;
		}

		if (mutation.getValue() != original.getValue()) {
			// Set up future garbage collection. This is called iff the mutate operation caused an additional
			// mutation to be added to the list. Once all copies of the map before the current version
			// are released, no mutations before the current mutations will be reachable. So request
			// a garbage collection operation on this list of mutations when the version right before
			// the current version is released.
			registerGarbageCollectionEvent(key, version - 1);
		}

		return new ModifiableValue<>(mutation.getValue(), original.getValue());
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws NullPointerException
	 * 		if the key or value is null
	 */
	@Override
	public V put(final K key, final V value) {
		if (key == null) {
			throw new NullPointerException("null keys are not supported");
		}
		if (value == null) {
			throw new NullPointerException("null values are not supported");
		}

		return mutate(key, value);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public V remove(final Object key) {
		return mutate((K) key, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		for (final K k : keySet()) {
			remove(k);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<Entry<K, V>> entrySet() {
		return new FCHashMapEntrySet<>(this, data);
	}

	/**
	 * Utility function for testing garbage collection.
	 *
	 * @return the internal map
	 */
	protected Map<K, Mutation<V>> getData() {
		return data;
	}
}
