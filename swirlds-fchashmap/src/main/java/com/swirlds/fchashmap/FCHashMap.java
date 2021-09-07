/*
 * (c) 2016-2021 Swirlds, Inc.
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
import com.swirlds.fchashmap.internal.FCHashMapGarbageCollector;
import com.swirlds.fchashmap.internal.Mutation;
import com.swirlds.fchashmap.internal.MutationQueue;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * A map that with HashMap like performance that provides FastCopyable semantics.
 *
 * This object is thread safe only under the following conditions:
 * 1) Only one thread is making modifications at any one point in time
 * 2) It's ok to read from the map (or a copy) while another thread is writing to it
 * 3) A copy of this structure may not be made at the same time this structure is being modified.
 * 4) A copy must not be deleted at the same time another thread is reading it, copying it,
 * or also attempting to delete it.
 *
 * The following synchronization methods are not thread safe
 * 1) timing (duh)
 * 2) communication via non-synchronized variables (same reordering risks as other data types)
 *
 * The following synchronization methods are thread safe
 * 1) locks in any form
 * 2) communication over volatile variables
 * 3) thread operations such as join()
 *
 * There is a special mode of operation during which concurrent writes to the data structure are thread safe iff
 * those writes are to different keys and no thread attempts to concurrently read a key that is being written by
 * a different thread. This is sometimes useful when initializing a map. This behavior is disabled by default.
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

	private final ConcurrentHashMap<K, MutationQueue<V>> data;

	private int size;

	private final CountDownLatch releasedLatch;

	private final FCHashMapGarbageCollector<K, V> garbageCollector;

	/**
	 * Tracks if this particular object has been deleted.
	 */
	private boolean deleted;

	/**
	 * Initialized to contain an instance of the appropriate view the first time this view is requested.
	 * The views is stateless, so there's no reason to create more than one. This same pattern is used
	 * in AbstractMap.
	 */
	private transient Set<Entry<K, V>> entrySet;

	public FCHashMap(int capacity) {
		data = new ConcurrentHashMap<>(capacity);
		immutable = false;
		version = 0;
		garbageCollector = new FCHashMapGarbageCollector<>(data);
		startGarbageCollector();
		deleted = false;
		releasedLatch = new CountDownLatch(1);
	}

	/**
	 * Start the garbage collector. Can be overridden by tests that want to disable garbage collection.
	 */
	protected void startGarbageCollector() {
		garbageCollector.start();
	}

	public FCHashMap() {
		this(0);
	}

	protected FCHashMap(FCHashMap<K, V> other) {
		data = other.data;
		size = other.size;
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
	 *
	 * There can only be one mutable copy of an FCHashMap at any point in time.
	 */
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
	public synchronized void release() {
		releasedLatch.countDown();
		if (!deleted) {
			deleted = true;
			garbageCollector.decrementReferenceCount();
		}
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
		return size;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean containsKey(Object key) {
		Mutation<V> prev = getLatestMutation((K) key);
		return prev != null && !prev.deleted;
	}

	/**
	 * Look up the most recent mutation for a key at the map's current version
	 *
	 * @param key
	 * 		Look up the mutation for this key.
	 * @return The latest mutation for this version. May be null if the key is not in the map at this version.
	 */
	private Mutation<V> getLatestMutation(K key) {
		MutationQueue<V> mutations = data.get(key);

		if (mutations == null || mutations.isEmpty()) {
			return null;
		}
		if (!immutable) {
			// The mutable copy always depends on the latest value
			return mutations.getLast();
		} else {
			Mutation<V> prev = null;
			for (Mutation<V> mutation : mutations) {
				if (mutation.version == version) {
					// This mutation happened during this copy's version
					return mutation;
				} else if (mutation.version > version) {
					// This mutation happened after this copy was made
					return prev;
				}
				prev = mutation;
			}
			// There are no more mutations
			return prev;
		}
	}

	/**
	 * Change the value associated with a particular key.
	 *
	 * @param key
	 * 		The key that is being written to.
	 * @param value
	 * 		The new value for that key.
	 * @return The previous value for a key
	 */
	private V setValueAtKey(K key, V value) {
		return this.setValueAtKey(key, value, false);
	}

	/**
	 * Sets as deleted the value associated with a particular key.
	 *
	 * @param key
	 * 		The key that is being written to.
	 * @return The previous value for a key or null if it was
	 * 		previously deleted
	 */
	private V deleteValueAtKey(K key) {
		return this.setValueAtKey(key, null, true);
	}

	/**
	 * Change the value associated with a particular key.
	 *
	 * @param key
	 * 		The key that is being written to.
	 * @param value
	 * 		The new value for that key.
	 * @param deletion
	 * 		If true then delete this entry from the map. In this case value should also be null.
	 * @return The previous value for a key or null if {@code deletion = true} and it was
	 * 		previously deleted
	 */
	private V setValueAtKey(K key, V value, boolean deletion) {
		throwIfImmutable();
		boolean insertion = true;
		V originalValue = null;
		boolean originalDeletionStatus = false;

		MutationQueue<V> mutations = data.get(key);

		// Create a new mutation queue if needed
		if (mutations == null) {
			// Mutation queue for this key does not yet exist, create it
			if (deletion) {
				// Caller is deleting a key that is not in a map
				return null;
			}

			mutations = new MutationQueue<>();

			// We don't need to synchronize on data.
			// If mutation queue for key is currently null then there is nothing
			// the garbage collection thread can do to modify the entry.
			data.put(key, mutations);
		}

		synchronized (mutations) {

			if (mutations.isDeleted()) {
				// This mutation queue was deleted after we took it from the map but before we locked it.
				if (deletion) {
					// Caller is deleting a key that has already been deleted
					return null;
				}

				mutations = new MutationQueue<>();
				synchronized (data) {
					// Garbage collection thread may be attempting to modify mutation
					// queue at key concurrently, synchronization prevents race condition.
					data.put(key, mutations);
				}
			}

			final Mutation<V> newest = mutations.peekLast();
			if (newest != null) {
				// Read the state of an existing mutation queue
				originalValue = newest.value;
				originalDeletionStatus = newest.deleted;
				if (deletion && originalDeletionStatus) {
					// Caller is deleting the same key twice
					return null;
				}
				insertion = originalDeletionStatus;
			}

			final boolean originallyEmpty = mutations.isEmpty();

			// Add the mutation
			final boolean mutationAdded = mutations.maybeAddLast(version, value, deletion);

			// Adjust the size of the map
			if (insertion) {
				size++;
			} else if (deletion) {
				size--;
			}

			// We don't need to schedule garbage collection on queues that don't grow unless there is a deletion
			// We don't need to schedule garbage collection on queues of size 1
			if ((mutationAdded && !originallyEmpty) || deletion) {
				garbageCollector.registerGarbageCollectionEvent(key, mutations, version);
			}

			return originalValue;
		}
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
	 * Not thread safe on an immutable copy if it is possible that another thread may have deleted the copy.
	 * Deletes and reads against an immutable copy must be externally synchronized. The function hasBeenDeleted()
	 * can be used to check to see if the copy has been deleted.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key) {
		if (key == null) {
			throw new NullPointerException("Null keys are not allowed");
		}
		Mutation<V> mutation = getLatestMutation((K) key);
		return mutation == null ? null : mutation.value;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V put(K key, V value) {
		return setValueAtKey(key, value);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public V remove(Object key) {
		return deleteValueAtKey((K) key);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		for (K k : keySet()) {
			remove(k);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<Entry<K, V>> entrySet() {
		Set<Entry<K, V>> set = entrySet;
		if (set == null) {
			set = new AbstractSet<>() {
				public Iterator<Entry<K, V>> iterator() {
					return new Iterator<>() {
						private Iterator<K> i = data.keySet().iterator();
						private K nextValidKey;
						private K previousValidKey;

						/**
						 * Some elements in the data iterator will not
						 * be in the current copy. After calling this
						 * method, nextValidKey will point to the
						 * next key in the map that is in the current
						 * copy of the map (if one exists))
						 */
						private void advanceIterator() {
							if (nextValidKey != null) {
								return;
							}
							while (i.hasNext()) {
								K nextKey = i.next();
								if (FCHashMap.this.containsKey(nextKey)) {
									nextValidKey = nextKey;
									break;
								}
							}
						}

						public boolean hasNext() {
							advanceIterator();
							return nextValidKey != null;
						}

						public Entry<K, V> next() {
							advanceIterator();
							if (nextValidKey != null) {
								previousValidKey = nextValidKey;
								nextValidKey = null;
								return new AbstractMap.SimpleEntry<>(
										previousValidKey, FCHashMap.this.get(previousValidKey));
							} else {
								throw new NoSuchElementException();
							}
						}

						public void remove() {
							if (previousValidKey != null) {
								FCHashMap.this.remove(previousValidKey);
								previousValidKey = null;
							} else {
								throw new IllegalStateException();
							}
						}
					};
				}

				public int size() {
					return FCHashMap.this.size();
				}

				public boolean isEmpty() {
					return FCHashMap.this.isEmpty();
				}

				public void clear() {
					FCHashMap.this.clear();
				}

				public boolean contains(Object k) {
					return FCHashMap.this.containsKey(k);
				}
			};
			entrySet = set;
		}
		return set;
	}

	/**
	 * Utility function for unit testing garbage collection. Check the number of mutations still in memory for a key.
	 * Not thread safe.
	 *
	 * @param key
	 * 		the key whose associated value is to be returned
	 * @return the number of mutations still in memory for a key
	 */
	public Integer mutationCountForKey(K key) {
		MutationQueue<V> mq = data.get(key);
		if (mq == null) {
			return null;
		} else {
			return mq.size();
		}
	}

	/**
	 * Utility function for testing garbage collector cleanup.
	 *
	 * @return if the garbage collection thread is still running.
	 */
	public boolean isGarbageCollectorStillRunning() {
		return garbageCollector.isRunning();
	}
}
