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

package com.swirlds.fcmap;

import com.swirlds.common.Archivable;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.AbstractBinaryMerkleInternal;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.fcmap.internal.FCMLeaf;
import com.swirlds.fcmap.internal.FCMTree;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

import static com.swirlds.logging.LogMarker.RECONNECT;

/**
 * Fast Copyable Map based implementation of the {@link Map} and {@link FastCopyable} interfaces,
 * which maps keys to values; and can be copied and serialized in a way specific to the
 * Swirlds platform. This implementation relies on a {@link HashMap} like data structure to guarantee
 * retrievals in O(1), and a Merkle Tree in order to trace the veracity and history of the
 * ledger, for which each leave contains the entry key and value. As a FastCopyable object,
 * the leaves of the Merkle Tree will be serialized, which requires serializing the key
 * and value; and thus key and value must implement {@link FastCopyable}.
 * <strong>Key and Value type are required to implement the methods {@code equals} and
 * {@code hashCode} in order to work accordingly internally.</strong>
 * <p>
 * The Fast Copyable Map (FCM or FCMap) cannot contain duplicate keys and each key can map
 * to at most one value. The FCM supports null values but not null keys.
 * </p>
 *
 * If this data structure is used inside a SwirldState it should be archived when SwirldState.archive() is invoked,
 * otherwise a much larger memory footprint than necessary may result.
 *
 * @param <K>
 * 		Key that implements {@link FastCopyable}
 * @param <V>
 * 		Value that implements {@link FastCopyable}
 */
public class FCMap<K extends MerkleNode, V extends MerkleNode>
		extends AbstractBinaryMerkleInternal
		implements Archivable, Map<K, V> {

	public static final long CLASS_ID = 0x941550bf023ad8f6L;

	/**
	 * This version number should be used to handle compatibility issues that may arise from any future changes
	 */
	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	/**
	 * use this for all logging, as controlled by the optional data/log4j2.xml file
	 */
	private static final Logger LOG = LogManager.getLogger(FCMap.class);

	private static final int DEFAULT_INITIAL_MAP_CAPACITY = 2_000_000;

	/**
	 * Internal map to guarantee O(1) access
	 */
	protected FCHashMap<K, FCMLeaf<K, V>> internalMap;

	/**
	 * Used to prevent concurrent reads, writes, copies, and archives.
	 */
	private volatile StampedLock lock;

	private static class ChildIndices {
		/**
		 * Internal Merkle Tree
		 */
		public static final int TREE = 0;

		public static final int CHILD_COUNT = 1;
	}

	/**
	 * If you attempt to enter methods in this class with a debugger then it can cause deadlock.
	 * Set this to {@code true} to disable locks for testing.
	 *
	 * IMPORTANT: never commit this file without reverting the value of this variable to {@code false}.
	 */
	private static final boolean LOCKS_DISABLED_FOR_DEBUGGING = false; // this MUST be false at commit time

	/**
	 * Acquire a read lock. Released by {@link #releaseReadLock(long)}.
	 *
	 * @return the stamp that must be used when calling {@link #releaseReadLock(long)}
	 */
	private long readLock() {
		if (LOCKS_DISABLED_FOR_DEBUGGING) {
			return 0;
		} else {
			return lock.readLock();
		}
	}

	/**
	 * Release a read lock acquired by {@link #readLock()}.
	 *
	 * @param stamp
	 * 		the value returned by the previous call to {@link #readLock()}
	 */
	private void releaseReadLock(final long stamp) {
		if (!LOCKS_DISABLED_FOR_DEBUGGING) {
			lock.unlockRead(stamp);
		}
	}

	/**
	 * Acquire a write lock. Released by {@link #releaseWriteLock(long)}.
	 *
	 * @return the stamp that must be used when calling {@link #releaseWriteLock(long)}
	 */
	private long writeLock() {
		if (LOCKS_DISABLED_FOR_DEBUGGING) {
			return 0;
		} else {
			return lock.writeLock();
		}
	}

	/**
	 * Release a write lock acquired by {@link #writeLock()}.
	 *
	 * Note: if you attempt to enter this class with a debugger then it can cause deadlock.
	 * Temporarily disable these locks if you wish to visit this class with a debugger.
	 *
	 * @param stamp
	 * 		the value returned by the previous call to {@link #writeLock()}
	 */
	private void releaseWriteLock(final long stamp) {
		if (!LOCKS_DISABLED_FOR_DEBUGGING) {
			lock.unlockWrite(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean childHasExpectedType(final int index, final long childClassId, final int version) {
		if (index == ChildIndices.TREE) {
			return childClassId == FCMTree.CLASS_ID;
		}

		return true;
	}

	protected FCMTree<K, V> getTree() {
		return getChild(ChildIndices.TREE);
	}

	private void setTree(final FCMTree<K, V> tree) {
		setChild(ChildIndices.TREE, tree);
	}

	/**
	 * Creates an instance of {@link FCMap}
	 */
	public FCMap() {
		this(DEFAULT_INITIAL_MAP_CAPACITY);
	}

	/**
	 * Creates an instance of {@link FCMap}
	 *
	 * @param initialCapacity
	 * 		Initial capacity of internal hash map
	 */
	public FCMap(final int initialCapacity) {
		this.internalMap = new FCHashMap<>(initialCapacity);
		setTree(new FCMTree<>());
		setImmutable(false);
		lock = new StampedLock();
	}

	/**
	 * Creates an immutable FCMap based a provided FCMap
	 *
	 * @param map
	 * 		An FCMap
	 */
	protected FCMap(final FCMap<K, V> map) {
		super(map);
		setTree(map.getTree().copy());
		// The internal map will never be deleted from a mutable copy
		this.internalMap = map.internalMap.copy();
		lock = new StampedLock();

		setImmutable(false);
		map.setImmutable(true);
	}

	/**
	 * Returns the number of key-value mappings in this map. This method returns a {@code long}
	 * which is more suitable than {@link #size} when the number of keys is greater than
	 * the maximum value of an {@code int}.
	 *
	 * <p>
	 * This operation takes O(1) time
	 * </p>
	 *
	 * @return the number of key-value mappings in this map
	 */
	public long getSize() {
		final long stamp = readLock();
		try {
			return getTree().size();
		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Creates an immutable fast copy of this FCMap.
	 *
	 * @return A fast copied FCMap
	 */
	@Override
	public FCMap<K, V> copy() {
		throwIfImmutable();
		throwIfReleased();
		final long stamp = readLock();
		try {
			return new FCMap<>(this);
		} finally {
			releaseReadLock(stamp);
		}
	}

	private void updateCache(final FCMLeaf<K, V> leaf) {
		internalMap.put(leaf.getKey(), leaf);
	}

	@Override
	protected void onRelease() {
		internalMap.release();
	}

	/**
	 * After calling this function, read operations against this FCMap will be performed in O(n) time
	 * instead of O(m) time (where m is the number of recent mutations on a key).
	 * <p>
	 * Calling this method allows for the garbage collector to prune the size of the data structure.
	 * It also allows for newer copies to perform read operations more quickly by reducing the number
	 * of recent modifications.
	 * <p>
	 * An FCMap copy is not required to call this method before being deleted.
	 */
	@Override
	public void archive() {
		final long stamp = writeLock();
		try {
			if (!isImmutable()) {
				throw new IllegalStateException("A mutable FCMap may not have fast read access revoked.");
			}
			if (!internalMap.isReleased()) {
				this.internalMap.release();
			}
		} finally {
			releaseWriteLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Removes the mapping for the specified key from this map if present.
	 * <p>
	 * This operation takes O(lg n) time
	 * </p>
	 *
	 * @param key
	 * 		key whose mapping is to be removed from the map
	 * @return the previous value associated with {@code key}, or
	 *        {@code null} if there was no mapping for {@code key}.
	 * 		(A {@code null} return can also indicate that the map
	 * 		previously associated {@code null} with {@code key}.)
	 */
	@Override
	public V remove(final Object key) {
		throwIfImmutable();
		final long stamp = writeLock();
		try {
			final FCMLeaf<K, V> leaf = this.internalMap.remove(key);
			if (leaf == null) {
				return null;
			}

			final V value = leaf.getValue();
			getTree().delete(leaf, this::updateCache);
			this.invalidateHash();
			return value;
		} finally {
			releaseWriteLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Returns the value to which the specified key is mapped,
	 * or {@code null} if this map contains no mapping for the key.
	 *
	 * <p>
	 * More formally, if this map contains a mapping from a key
	 * {@code k} to a value {@code v} such that {@code (key.equals(k))},
	 * then this method returns {@code v}; otherwise it returns {@code null}.
	 * (There can be at most one such mapping.)
	 * </p>
	 * <p>
	 * A return value of {@code null} does not <i>necessarily</i>
	 * indicate that the map contains no mapping for the key; it's also
	 * possible that the map explicitly maps the key to {@code null}.
	 * The {@link #containsKey containsKey} operation may be used to
	 * distinguish these two cases.
	 * </p>
	 * <p>
	 * This operation takes O(1) time for both the mutable copy and for all immutable copies.
	 * <p>
	 * The value returned by this method should not be directly modified. If a value requires modification,
	 * call {@link #getForModify(MerkleNode)} and modify the value returned by that method instead.
	 * </p>
	 * <p>
	 * Technically speaking, it is ok to modify the value returned by this method if the value has already been
	 * fast copied in this version or if the value is not referenced in any other version. But it is highly,
	 * highly recommended to simply use {@link #getForModify(MerkleNode)}, as merkle copy semantics are nuanced and
	 * easy use incorrectly.
	 * </p>
	 */
	@SuppressWarnings("unchecked")
	@Override
	public V get(final Object key) {
		StopWatch watch = null;

		if (FCMapStatistics.isRegistered()) {
			watch = new StopWatch();
			watch.start();
		}

		final long stamp = readLock();
		try {
			final FCMLeaf<K, V> leaf;
			if (this.internalMap.isReleased()) {
				leaf = getTree().findLeafByKey((K) key);
			} else {
				leaf = this.internalMap.get(key);
			}

			return leaf == null ? null : leaf.getValue();
		} finally {
			releaseReadLock(stamp);

			if (watch != null) {
				watch.stop();
				FCMapStatistics.fcmGetMicroSec.recordValue(watch.getTime(TimeUnit.MICROSECONDS));
			}
		}
	}

	/**
	 * Get the value associated with a given key. Value is safe to directly modify.
	 * <p>
	 * In a prior implementation of this method it was necessary to re-insert the modified value back into the tree
	 * via the replace() method. In the current implementation this is no longer required.
	 * Replacing a value returned by this method has no negative side effects, although it will have minor
	 * performance overhead and should be avoided if possible.
	 * </p>
	 *
	 * @param key
	 * 		the key that will be used to look up the value
	 * @return an object that is safe to directly modify
	 */
	public V getForModify(final K key) {
		throwIfImmutable();

		StopWatch watch = null;

		if (FCMapStatistics.isRegistered()) {
			watch = new StopWatch();
			watch.start();
		}

		final long stamp = readLock();
		try {
			final FCMLeaf<K, V> originalLeaf = internalMap.get(key);
			if (originalLeaf == null) {
				return null;
			}

			FCMLeaf<K, V> newLeaf = getTree().getForModify(originalLeaf, this::updateCache);

			return newLeaf.getValue();
		} finally {
			releaseReadLock(stamp);

			if (watch != null) {
				watch.stop();
				FCMapStatistics.fcmGfmMicroSec.recordValue(watch.getTime(TimeUnit.MICROSECONDS));
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Associates the specified value with the specified key in this map.
	 * If the map previously contained a mapping for the key, the old
	 * value is replaced.
	 *
	 * <p>
	 * This operation takes O(lg n) time where <i>n</i> is the current
	 * number of keys.
	 * </p>
	 *
	 * @param key
	 * 		key with which the specified value is to be associated
	 * @param value
	 * 		value to be associated with the specified key
	 * @return the previous value associated with {@code key}, or
	 *        {@code null} if there was no mapping for {@code key}.
	 * 		(A {@code null} return can also indicate that the map
	 * 		previously associated {@code null} with {@code key}.)
	 */
	@Override
	public V put(final K key, final V value) {
		throwIfImmutable();
		StopWatch watch = null;

		if (FCMapStatistics.isRegistered()) {
			watch = new StopWatch();
			watch.start();
		}

		final V val = putInternal(key, value);
		if (watch != null) {
			watch.stop();
			FCMapStatistics.fcmPutMicroSec.recordValue(watch.getTime(TimeUnit.MICROSECONDS));
		}

		return val;
	}

	private V putInternal(K key, V value) {
		final long stamp = writeLock();

		try {
			if (internalMap.containsKey(key)) {
				return replaceInternal(key, value);
			} else {

				if (key.getReferenceCount() != 0) {
					throw new IllegalArgumentException("Key is in another tree, can not insert");
				}

				if (value != null && value.getReferenceCount() != 0) {
					throw new IllegalArgumentException("Value is in another tree, can not insert");
				}

				final FCMLeaf<K, V> leaf = new FCMLeaf<>(key, value);
				getTree().insert(leaf, this::updateCache);
				internalMap.put(key, leaf);
				invalidateHash();
				return null;
			}
		} finally {
			releaseWriteLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Returns the number of key-value mappings in this map.
	 *
	 * <p>
	 * This operation takes O(1) time
	 * </p>
	 *
	 * @return the number of key-value mappings in this map
	 */
	@Override
	public int size() {
		return (int) this.getSize();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Returns {@code true} if this map contains a mapping for the
	 * specified key.
	 *
	 * <p>
	 * This operation takes O(1) time in the original instance
	 * and O(n) in the copied map.
	 * </p>
	 *
	 * @param key
	 * 		The key whose presence in this map is to be tested
	 * @return {@code true} if this map contains a mapping for the specified
	 * 		key.
	 */
	@Override
	public boolean containsKey(final Object key) {
		return this.internalMap.containsKey(key);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Returns {@code true} if this map contains no key-value mappings.
	 *
	 * <p>
	 * This operation takes O(1) time
	 * </p>
	 *
	 * @return {@code true} if this map contains no key-value mappings
	 */
	@Override
	public boolean isEmpty() {
		final long stamp = readLock();
		try {
			return getTree().isEmpty();
		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * The implementation of replace without locks. This allows for the replace operation to be performed
	 * while a lock is already held by the outer context.
	 */
	private V replaceInternal(final K key, final V value) {
		final FCMLeaf<K, V> oldLeaf = this.internalMap.get(key);
		if (oldLeaf == null) {
			throw new IllegalStateException("Can not replace value that is not in the map");
		}

		final K oldKey = oldLeaf.getKey();
		final V oldValue = oldLeaf.getValue();

		if (oldLeaf.getValue() == value) {
			// Value is already in this exact position, no work needed.
			return value;
		}

		if (value != null && value.getReferenceCount() != 0) {
			throw new IllegalArgumentException("Value is already in a tree, can not insert into map");
		}

		// Once fast copies are managed by a utility, these manual hash invalidations will no longer be necessary.
		this.invalidateHash();
		getTree().invalidateHash();
		getTree().getRoot().invalidateHash();

		final FCMLeaf<K, V> newLeaf = new FCMLeaf<>();

		// For the sake of efficiency, it is critically important that routes are not recreated unnecessarily.
		// Recycle the existing routes from oldLeaf.
		newLeaf.setRoute(oldLeaf.getRoute());
		newLeaf.emplaceChildren(key, oldKey.getRoute(), value, oldValue == null ? null : oldValue.getRoute());

		getTree().update(oldLeaf, newLeaf);
		this.internalMap.put(key, newLeaf);

		return oldLeaf.getValue();
	}

	/**
	 * Replaces the entry for the specified key if and only if it is currently mapped to some value.
	 *
	 * <p>
	 * This operation takes O(lg n) time where <i>n</i> is the current
	 * number of keys.
	 * </p>
	 *
	 * @param key
	 * 		key with which the specified value is to be associated and a previous value is
	 * 		already associated with
	 * @param value
	 * 		new value to be associated with the specified key
	 * @return the previous value associated with {@code key}, or
	 *        {@code null} if it was previously associated with
	 * 		the {@code key}. Note that {@code null} can also indicate
	 * 		that there was no previously associated value to the {@code key}
	 * 		for which case no association was performed.
	 */
	@Override
	public V replace(final K key, final V value) {
		throwIfImmutable();
		StopWatch watch = null;

		if (FCMapStatistics.isRegistered()) {
			watch = new StopWatch();
			watch.start();
		}

		final long stamp = writeLock();
		try {
			return replaceInternal(key, value);
		} finally {
			releaseWriteLock(stamp);

			if (watch != null) {
				watch.stop();
				FCMapStatistics.fcmReplaceMicroSec.recordValue(watch.getTime(TimeUnit.MICROSECONDS));
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Removes all of the mappings from this map.
	 * The map will be empty after this call returns.
	 */
	@Override
	public void clear() {
		throwIfImmutable();
		final long stamp = writeLock();
		try {
			clearWithoutLocking();
		} finally {
			releaseWriteLock(stamp);
		}
	}

	/**
	 * Equivalent to calling clear() but without first acquiring a write lock. Expects that the caller will have already
	 * locked this data structure.
	 * <p>
	 * This is needed since FCMap's locks are not reentrant (due to performance constraints).
	 */
	public void clearWithoutLocking() {
		this.internalMap.clear();
		getTree().clear();
		this.invalidateHash();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Returns a mutable Set view of the keys contained in this map at the moment the method is called.
	 * <p>
	 * If the map is modified later on, this set remains invariant to those changes.
	 * </p>
	 *
	 * @return a mutable set view of the keys contained in this map at the moment the method is called.
	 */
	@Override
	public Set<K> keySet() {
		final long stamp = readLock();
		try {
			if (this.internalMap.size() > 0) {
				return new HashSet<>(this.internalMap.keySet());
			}

			final Iterator<FCMLeaf<K, V>> leafIterator = getTree().leafIterator();
			final Set<K> keys = new HashSet<>();
			while (leafIterator.hasNext()) {
				final FCMLeaf<K, V> leaf = leafIterator.next();
				keys.add(leaf.getKey());
			}

			return keys;
		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Returns a mutable Collection view of the values contained in this map at the moment the
	 * method is called.
	 * <p>
	 * If the map is modified later on, this collection remains invariant to those changes.
	 * </p>
	 *
	 * @return a mutable view of the values contained in this map at the moment the method is called.
	 */
	@Override
	public Collection<V> values() {
		final long stamp = readLock();
		try {
			if (this.internalMap.size() > 0) {
				return this.internalMap.values().stream().map(FCMLeaf::getValue).collect(Collectors.toList());
			}

			final Iterator<FCMLeaf<K, V>> leafIterator = getTree().leafIterator();
			final Set<V> values = new HashSet<>();
			while (leafIterator.hasNext()) {
				final FCMLeaf<K, V> leaf = leafIterator.next();
				values.add(leaf.getValue());
			}

			return values;
		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * Returns a mutable Set view of the mappings contained in this map at the moment the method
	 * is called.
	 * <p>
	 * If the map is modified later on, this set remains invariant to those changes
	 * </p>
	 *
	 * @return a mutable set view of the mappings contained in this map at the moment the method is called.
	 */
	@Override
	public Set<Entry<K, V>> entrySet() {
		final long stamp = readLock();
		try {
			if (this.internalMap.size() > 0) {
				return this.internalMap.entrySet()
						.stream()
						.collect(HashMap<K, V>::new,
								(m, v) -> m.put(v.getKey(), v.getValue().getValue()),
								HashMap::putAll)
						.entrySet();
			}

			final Set<Entry<K, V>> entrySet = new HashSet<>();
			final Iterator<FCMLeaf<K, V>> leafIterator = getTree().leafIterator();
			while (leafIterator.hasNext()) {
				entrySet.add(leafIterator.next().getEntry());
			}

			return entrySet;
		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Returns {@code true} if this map maps one or more keys to the
	 * specified value.
	 *
	 * @param value
	 * 		value whose presence in this map is to be tested
	 * @return {@code true} if this map maps one or more keys to the
	 * 		specified value
	 */
	@Override
	public boolean containsValue(Object value) {
		final long stamp = readLock();
		try {
			if (this.internalMap.size() > 0) {
				return this.values().stream().anyMatch(v -> Objects.equals(v, value));
			}

			final Iterator<FCMLeaf<K, V>> leafIterator = getTree().leafIterator();
			while (leafIterator.hasNext()) {
				final FCMLeaf<K, V> leaf = leafIterator.next();
				if (leaf.getValue().equals(value)) {
					return true;
				}
			}

			return false;
		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Copies all of the mappings from the specified map to this map.
	 * These mappings will replace any mappings that this map had for
	 * any of the keys currently in the specified map.
	 *
	 * @param m
	 * 		mappings to be stored in this map
	 * @throws NullPointerException
	 * 		if the specified map is null
	 */
	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		for (final Entry<? extends K, ? extends V> entry : m.entrySet()) {
			this.put(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @param o
	 *        {@inheritDoc}
	 * @return {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof FCMap)) {
			return false;
		}

		final FCMap<?, ?> fcMap = (FCMap<?, ?>) o;
		final Hash rootHash = this.getRootHash();
		final Hash otherRootHash = fcMap.getRootHash();
		return rootHash.equals(otherRootHash);
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		final long stamp = readLock();
		try {
			final Hash hash = getTree().getHash();
			if (hash == null) {
				return super.hashCode();
			}

			return hash.hashCode();
		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * @return The root hash value
	 */
	public Hash getRootHash() {
		final long stamp = readLock();
		try {
			return getRootHashWithoutLocking();
		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * Equivalent to calling getRootHash() but without first acquiring a read lock.
	 * Expects that the caller will have already locked this data structure.
	 * <p>
	 * This is needed since FCMap's locks are not reentrant (due to performance constraints).
	 *
	 * @return The root hash
	 */
	public Hash getRootHashWithoutLocking() {
		return this.getTree().getHash();
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return The String format of this FCMap object.
	 */
	@Override
	public String toString() {
		return String.format("Size: %d - %s", getTree().size(), this.getRootHash());
	}

	/**
	 * Utility method for unit tests. Return the internal map used for fast lookup operations.
	 */
	protected FCHashMap<K, FCMLeaf<K, V>> getInternalMap() {
		return internalMap;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void initialize() {
		final Iterator<FCMLeaf<K, V>> leafIterator = getTree().leafIterator();
		while (leafIterator.hasNext()) {
			final FCMLeaf<K, V> nextLeaf = leafIterator.next();
			this.internalMap.put(nextLeaf.getKey(), nextLeaf);
		}

		LOG.debug(RECONNECT.getMarker(), "FCMap Initialized [ internalMapSize = {}, treeSize = {} ]",
				internalMap::size, () -> getTree().size());
	}
}
