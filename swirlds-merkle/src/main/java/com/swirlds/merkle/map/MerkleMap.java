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

package com.swirlds.merkle.map;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.Archivable;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.ArchivedException;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.utility.AbstractBinaryMerkleInternal;
import com.swirlds.common.merkle.utility.DebugIterationEndpoint;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.fchashmap.FCHashMapSettingsFactory;
import com.swirlds.merkle.tree.MerkleBinaryTree;
import com.swirlds.merkle.tree.MerkleTreeInternalNode;
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

import static com.swirlds.common.merkle.copy.MerklePathReplacement.getParentInPath;
import static com.swirlds.common.merkle.copy.MerklePathReplacement.replacePath;
import static com.swirlds.common.merkle.utility.MerkleUtils.findChildPositionInParent;
import static com.swirlds.logging.LogMarker.RECONNECT;

/**
 * <p>
 * A map implemented with a binary merkle tree.
 * </p>
 *
 * <p>
 * This data structure utilizes an internal {@link FCHashMap} to provide O(1) read access
 * (but only to non-archived copies of the map). It uses a copy-on-write algorithm to provide
 * O(1) fast copies, with write operations costing O(log n) where n is the number of entries in the map.
 * </p>
 *
 * <p>
 * This data structure does not support null keys or null values.
 * </p>
 *
 * <p>
 * If this data structure is used inside a SwirldState it should be archived when SwirldState.archive() is invoked,
 * otherwise a much larger memory footprint than necessary may result.
 * </p>
 *
 * @param <K>
 * 		the type of the key. Must be effectively immutable. That is, after insertion into a map, no operation
 * 		on this key should be capable of changing the behavior of its {@link Object#hashCode()}
 * 		or {@link Object#equals(Object)} methods. It is STRONGLY recommended that this type not
 * 		implement {@link MerkleNode}. Although a merkle key will technically "work", it is quite
 * 		inefficient from a memory perspective.
 * @param <V>
 * 		value that implements {@link MerkleNode} and {@link Keyed}. Can be an internal node or a leaf.
 * 		If this value is an internal node the key will need to be stored inside a descendant leaf node.
 */
@DebugIterationEndpoint
public class MerkleMap<K, V extends MerkleNode & Keyed<K>>
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
	private static final Logger LOG = LogManager.getLogger(MerkleMap.class);

	private static final int DEFAULT_INITIAL_MAP_CAPACITY = 2_000_000;

	/**
	 * Internal map to guarantee O(1) access
	 */
	protected FCHashMap<K, V> index;

	/**
	 * Used to prevent concurrent reads, writes, copies, and archives.
	 */
	private final StampedLock lock;

	/**
	 * True if this object has been archived, otherwise false.
	 */
	private boolean archived;

	private static class ChildIndices {
		/**
		 * Internal Merkle Tree
		 */
		public static final int TREE = 0;

		public static final int CHILD_COUNT = 1;
	}

	/**
	 * <p>
	 * If you attempt to enter methods in this class with a debugger then it can cause deadlock.
	 * Set this to {@code true} to disable locks for testing.
	 * </p>
	 *
	 * <p>
	 * IMPORTANT: never commit this file without reverting the value of this variable to {@code false}.
	 * </p>
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
	 * <p>
	 * Release a write lock acquired by {@link #writeLock()}.
	 * </p>
	 *
	 * <p>
	 * Note: if you attempt to enter this class with a debugger then it can cause deadlock.
	 * Temporarily disable these locks if you wish to visit this class with a debugger.
	 * </p>
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
			return childClassId == MerkleBinaryTree.CLASS_ID;
		}

		return true;
	}

	protected MerkleBinaryTree<V> getTree() {
		return getChild(ChildIndices.TREE);
	}

	private void setTree(final MerkleBinaryTree<V> tree) {
		setChild(ChildIndices.TREE, tree);
	}

	/**
	 * Creates an instance of {@link MerkleMap}
	 */
	public MerkleMap() {
		this(DEFAULT_INITIAL_MAP_CAPACITY);
	}

	/**
	 * Creates an instance of {@link MerkleMap}
	 *
	 * @param initialCapacity
	 * 		Initial capacity of internal hash map
	 */
	public MerkleMap(final int initialCapacity) {
		index = new FCHashMap<>(initialCapacity);
		setTree(new MerkleBinaryTree<>());
		setImmutable(false);
		lock = new StampedLock();
	}

	/**
	 * Creates an immutable MerkleMap based a provided MerkleMap
	 *
	 * @param that
	 * 		a MerkleMap to copy
	 */
	protected MerkleMap(final MerkleMap<K, V> that) {
		super(that);
		setTree(that.getTree().copy());
		// The internal map will never be deleted from a mutable copy
		index = that.index.copy();
		lock = new StampedLock();

		setImmutable(false);
		that.setImmutable(true);
	}

	/**
	 * <p>
	 * Returns the number of key-value mappings in this map. This method returns a {@code long}
	 * which is more suitable than {@link #size} when the number of keys is greater than
	 * the maximum value of an {@code int}.
	 * </p>
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
	 * Creates an immutable fast copy of this MerkleMap.
	 * </p>
	 *
	 * @return A fast copied MerkleMap
	 */
	@Override
	public MerkleMap<K, V> copy() {
		throwIfImmutable();
		throwIfReleased();
		final long stamp = readLock();
		try {
			return new MerkleMap<>(this);
		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * This method updates the {@link FCHashMap} based index. Called when entries are fast copied
	 * by the {@link MerkleBinaryTree}.
	 *
	 * @param entry
	 * 		the entry that needs to be updated in the cache
	 */
	private void updateCache(final V entry) {
		index.put(entry.getKey(), entry);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected synchronized void onRelease() {
		if (!isArchived()) {
			index.release();
		}
	}

	/**
	 * <p>
	 * After calling this function, read operations against this FCMap fail.
	 * </p>
	 *
	 * <p>
	 * Calling this method allows for the garbage collector to prune the size of the data structure.
	 * It also allows for newer copies to perform read operations more quickly by reducing the number
	 * of recent modifications.
	 * </p>
	 *
	 * <p>
	 * A MerkleMap copy is not required to call this method before being deleted.
	 * </p>
	 *
	 * @throws ArchivedException
	 * 		if this copy of the map has been archived
	 */
	@Override
	public synchronized void archive() {
		if (!isImmutable()) {
			throw new ArchivedException("can not archive the mutable copy of a map");
		}

		if (FCHashMapSettingsFactory.get().isArchiveEnabled()) {
			final long stamp = writeLock();

			// Don't archive twice
			throwIfArchived();
			archived = true;

			try {
				if (!isImmutable()) {
					throw new ArchivedException("A mutable FCMap may not have fast read access revoked.");
				}
				index.release();
				index = null;
			} finally {
				releaseWriteLock(stamp);
			}
		}
	}

	/**
	 * <p>
	 * {@inheritDoc}
	 * </p>
	 *
	 * <p>
	 * Important: this method is not synchronized, and is unsafe to call concurrently with another thread
	 * that may archive this object. For thread safe access, acquire a read lock on the map first.
	 * </p>
	 */
	@Override
	public boolean isArchived() {
		return archived;
	}

	/**
	 * <p>
	 * {@inheritDoc}
	 * </p>
	 *
	 * <p>
	 * Important: this method is not synchronized, and is unsafe to call concurrently with another thread
	 * that may archive this object. For thread safe access, acquire a read lock on the map first.
	 * </p>
	 */
	@Override
	public void throwIfArchived() {
		Archivable.super.throwIfArchived();
	}

	/**
	 * <p>
	 * {@inheritDoc}
	 * </p>
	 *
	 * <p>
	 * Removes the mapping for the specified key from this map if present.
	 * </p>
	 *
	 * <p>
	 * This operation takes O(log n) time
	 * </p>
	 *
	 * @param key
	 * 		key whose mapping is to be removed from the map
	 * @return the previous value associated with {@code key}, or
	 *        {@code null} if there was no mapping for {@code key}.
	 */
	@Override
	public V remove(final Object key) {
		throwIfImmutable();
		final long stamp = writeLock();
		try {
			final V entry = index.remove(key);
			if (entry == null) {
				return null;
			}

			getTree().delete(entry, this::updateCache);
			invalidateHash();
			return entry;
		} finally {
			releaseWriteLock(stamp);
		}
	}

	/**
	 * <p>
	 * Returns the value to which the specified key is mapped,
	 * or {@code null} if this map contains no mapping for the key.
	 * </p>
	 *
	 * <p>
	 * More formally, if this map contains a mapping from a key
	 * {@code k} to a value {@code v} such that {@code (key.equals(k))},
	 * then this method returns {@code v}; otherwise it returns {@code null}.
	 * (There can be at most one such mapping.)
	 * </p>
	 *
	 * <p>
	 * This data structure does not support null values, so if null is returned for a key then it can be inferred that
	 * the map does not contain an entry for that specific key.
	 * </p>
	 *
	 * <p>
	 * This operation takes O(1) time for both the mutable copy and for all immutable copies.
	 * </p>
	 *
	 * <p>
	 * The value returned by this method should not be directly modified. If a value requires modification,
	 * call {@link #getForModify(Object)} and modify the value returned by that method instead.
	 * </p>
	 *
	 * <p>
	 * The value returned by this method should not be directly modified. If a value requires modification,
	 * call {@link #getForModify(Object)} and modify the value returned by that method instead.
	 * </p>
	 *
	 * @throws ArchivedException
	 * 		if this copy of the map has been archived
	 */
	@Override
	public V get(final Object key) {
		throwIfArchived();

		StopWatch watch = null;

		if (MerkleMapStatistics.isRegistered()) {
			watch = new StopWatch();
			watch.start();
		}

		final long stamp = readLock();
		try {
			final V entry;
			if (index.isReleased()) {
				entry = getTree().findValue((final V v) -> Objects.equals(key, v.getKey()));
			} else {
				entry = index.get(key);
			}

			return entry;
		} finally {
			releaseReadLock(stamp);

			if (watch != null) {
				watch.stop();
				MerkleMapStatistics.mmmGetMicroSec.recordValue(watch.getTime(TimeUnit.MICROSECONDS));
			}
		}
	}

	/**
	 * <p>
	 * Get the value associated with a given key. Value is safe to directly modify. If given key is not in the
	 * map then null is returned.
	 * </p>
	 *
	 * <p>
	 * In a prior implementation of this method it was necessary to re-insert the modified value back into the tree
	 * via the replace() method. In the current implementation this is no longer required.
	 * Replacing a value returned by this method has no negative side effects, although it will have minor
	 * performance overhead and should be avoided if possible.
	 * </p>
	 *
	 * @param key
	 * 		the key that will be used to look up the value
	 * @return an object that is safe to directly modify, or null if the requested key is not in the map
	 */
	public V getForModify(final K key) {
		throwIfImmutable();

		StopWatch watch = null;

		if (MerkleMapStatistics.isRegistered()) {
			watch = new StopWatch();
			watch.start();
		}

		final long stamp = readLock();
		try {

			final FCHashMap.ModifiableValue<V> value = index.getForModify(key);

			if (value == null) {
				return null;
			}

			final V copy = value.value();
			final V original = value.original();
			final MerkleRoute route = original.getRoute();

			if (copy != original) {
				// Replace path down to parent of the entry
				final MerkleNode[] path = replacePath(getTree(), route, 1);

				final MerkleTreeInternalNode parent = getParentInPath(path);
				final int indexInParent = findChildPositionInParent(parent, original);

				parent.setChild(indexInParent, copy, route, false);
				getTree().registerCopy(original, copy);
			}

			return copy;

		} finally {
			releaseReadLock(stamp);

			if (watch != null) {
				watch.stop();
				MerkleMapStatistics.mmGfmMicroSec.recordValue(watch.getTime(TimeUnit.MICROSECONDS));
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V put(final K key, final V value) {
		throwIfImmutable();
		if (key == null) {
			throw new NullPointerException("null keys are not supported");
		}
		if (value == null) {
			throw new NullPointerException("null values are not supported");
		}

		StopWatch watch = null;

		if (MerkleMapStatistics.isRegistered()) {
			watch = new StopWatch();
			watch.start();
		}

		final V val = putInternal(key, value);
		if (watch != null) {
			watch.stop();
			MerkleMapStatistics.mmPutMicroSec.recordValue(watch.getTime(TimeUnit.MICROSECONDS));
		}

		return val;
	}

	private V putInternal(K key, V value) {
		final long stamp = writeLock();

		try {
			if (index.containsKey(key)) {
				return replaceInternal(key, value);
			} else {

				if (value.getReferenceCount() != 0) {
					throw new IllegalArgumentException("Value is in another tree, can not insert");
				}

				value.setKey(key);

				getTree().insert(value, this::updateCache);
				index.put(key, value);
				invalidateHash();
				return null;
			}
		} finally {
			releaseWriteLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int size() {
		return (int) getSize();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean containsKey(final Object key) {
		throwIfArchived();
		final long stamp = readLock();
		try {
			return index.containsKey(key);
		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEmpty() {
		throwIfArchived();
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
		final V oldEntry = index.get(key);
		if (oldEntry == null) {
			throw new IllegalStateException("Can not replace value that is not in the map");
		}

		if (oldEntry == value) {
			// Value is already in this exact position, no work needed.
			return value;
		}

		if (value.getReferenceCount() != 0) {
			throw new IllegalArgumentException("Value is already in a tree, can not insert into map");
		}

		// Once fast copies are managed by a utility, these manual hash invalidations will no longer be necessary.
		invalidateHash();
		getTree().invalidateHash();
		getTree().getRoot().invalidateHash();

		value.setKey(key);

		getTree().update(oldEntry, value);
		index.put(key, value);

		return oldEntry;
	}

	/**
	 * <p>
	 * Replaces the entry for the specified key if and only if it is currently mapped to some value.
	 * </p>
	 *
	 * <p>
	 * This operation takes O(lg n) time where <i>n</i> is the current
	 * number of keys.
	 * </p>
	 *
	 * @param key
	 * 		key with which the specified value is to be associated and a previous value is
	 * 		already associated with. Null is not supported.
	 * @param value
	 * 		new value to be associated with the specified key, can not be null
	 * @return the previous value associated with {@code key}, or
	 *        {@code null} if the key was not previously in the map
	 * @throws NullPointerException
	 * 		if the key or value is null
	 */
	@Override
	public V replace(final K key, final V value) {
		throwIfImmutable();
		if (key == null) {
			throw new NullPointerException("null keys are not supported");
		}
		if (value == null) {
			throw new NullPointerException("null values are not supported");
		}

		StopWatch watch = null;

		if (MerkleMapStatistics.isRegistered()) {
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
				MerkleMapStatistics.mmReplaceMicroSec.recordValue(watch.getTime(TimeUnit.MICROSECONDS));
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		throwIfImmutable();
		final long stamp = writeLock();
		try {
			index.clear();
			getTree().clear();
			invalidateHash();
		} finally {
			releaseWriteLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<K> keySet() {
		throwIfArchived();
		final long stamp = readLock();
		try {

			final Iterator<V> entryIterator = getTree().iterator();
			final Set<K> keys = new HashSet<>();
			while (entryIterator.hasNext()) {
				final V entry = entryIterator.next();
				keys.add(entry.getKey());
			}

			return keys;
		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Collection<V> values() {
		throwIfArchived();

		final long stamp = readLock();
		try {
			final Iterator<V> entryIterator = getTree().iterator();
			final Set<V> values = new HashSet<>();
			while (entryIterator.hasNext()) {
				values.add(entryIterator.next());
			}

			return values;
		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<Entry<K, V>> entrySet() {
		throwIfArchived();

		final long stamp = readLock();
		try {
			return index.entrySet()
					.stream()
					.collect(HashMap<K, V>::new,
							(m, v) -> m.put(v.getKey(), v.getValue()),
							HashMap::putAll)
					.entrySet();

		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean containsValue(Object value) {
		throwIfArchived();

		final long stamp = readLock();
		try {
			return this.values().stream().anyMatch(v -> Objects.equals(v, value));
		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		for (final Entry<? extends K, ? extends V> entry : m.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof MerkleMap)) {
			return false;
		}

		final MerkleMap<?, ?> merkleMap = (MerkleMap<?, ?>) o;
		final Hash rootHash = getRootHash();
		final Hash otherRootHash = merkleMap.getRootHash();
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
			return getTree().getHash();
		} finally {
			releaseReadLock(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return The String format of this MerkleMap object.
	 */
	@Override
	public String toString() {
		return String.format("Size: %d - %s", getTree().size(), getRootHash());
	}

	/**
	 * Utility method for unit tests. Return the internal map used for fast lookup operations.
	 */
	protected FCHashMap<K, V> getIndex() {
		return index;
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
		final Iterator<V> entryIterator = getTree().iterator();
		while (entryIterator.hasNext()) {
			final V nextEntry = entryIterator.next();
			index.put(nextEntry.getKey(), nextEntry);
		}

		LOG.debug(RECONNECT.getMarker(), "MerkleMap Initialized [ internalMapSize = {}, treeSize = {} ]",
				index::size, () -> getTree().size());
	}
}
