/*
 * (c) 2016-2022 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.fchashmap;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.utility.ValueReference;
import com.swirlds.fchashmap.internal.FCHashMapEntrySet;
import com.swirlds.fchashmap.internal.FCHashMapGarbageCollector;
import com.swirlds.fchashmap.internal.Mutation;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

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

	private final Map<K, Mutation<V>> data;

	private final AtomicInteger size;

	private final CountDownLatch releasedLatch;

	private final FCHashMapGarbageCollector<K, V> garbageCollector;

	/**
	 * Tracks if this particular object has been deleted.
	 */
	private boolean deleted;

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
	 * 		the initial capacity
	 */
	public FCHashMap(final int capacity) {
		this(capacity, FCHashMapGarbageCollector::new);
	}

	/**
	 * Create a new FCHashMap.
	 *
	 * @param capacity
	 * 		the initial capacity of the map
	 * @param gcBuilder
	 * 		garbage collector constructor
	 */
	protected FCHashMap(final int capacity,
			Function<Map<K, Mutation<V>>, FCHashMapGarbageCollector<K, V>> gcBuilder) {

		data = new ConcurrentHashMap<>(capacity);
		immutable = false;
		version = 0;
		garbageCollector = gcBuilder.apply(data);
		garbageCollector.start();
		deleted = false;
		releasedLatch = new CountDownLatch(1);
		size = new AtomicInteger(0);
	}

	/**
	 * Get the garbage collector. Useful for testing.
	 */
	protected FCHashMapGarbageCollector<K, V> getGarbageCollector() {
		return garbageCollector;
	}

	/**
	 * Copy constructor.
	 *
	 * @param other
	 * 		the map to copy
	 */
	protected FCHashMap(final FCHashMap<K, V> other) {
		data = other.data;
		size = new AtomicInteger(other.size.get());
		garbageCollector = other.garbageCollector;
		deleted = false;

		immutable = false;
		other.immutable = true;
		version = other.version + 1;

		releasedLatch = new CountDownLatch(1);

		garbageCollector.registerCopy(other);
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
		throwIfReleased();
		releasedLatch.countDown();
		deleted = true;
		garbageCollector.decrementReferenceCount();
	}

	/**
	 * Check to see if this copy has been deleted.
	 */
	@Override
	public boolean isReleased() {
		return deleted;
	}

	/**
	 * Block until this FCHashMap has been released.
	 */
	public void waitUntilReleased() throws InterruptedException {
		releasedLatch.await();
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
			garbageCollector.registerGarbageCollectionEvent(key, version - 1);
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
			garbageCollector.registerGarbageCollectionEvent(key, version - 1);
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

	/**
	 * Utility function for testing garbage collector cleanup.
	 *
	 * @return if the garbage collection thread is still running.
	 */
	protected boolean isGarbageCollectorStillRunning() {
		return garbageCollector.isRunning();
	}
}
