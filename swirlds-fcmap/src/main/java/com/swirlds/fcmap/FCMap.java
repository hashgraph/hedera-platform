/*
 * (c) 2016-2020 Swirlds, Inc.
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
import com.swirlds.common.FCMKey;
import com.swirlds.common.FCMValue;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.utility.AbstractMerkleInternal;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.fcmap.internal.FCMLeaf;
import com.swirlds.fcmap.internal.FCMTree;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

import static com.swirlds.common.merkle.MerkleUtils.invalidateTree;
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
public class FCMap<K extends FCMKey, V extends FCMValue>
		extends AbstractMerkleInternal
		implements Archivable,
		Map<K, V>,
		MerkleInternal,
		FCMValue {

	public static final long CLASS_ID = 0x941550bf023ad8f6L;

	/** This version number should be used to handle compatibility issues that may arise from any future changes */
	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	private static final Marker COPY_FROM = MarkerManager.getMarker("FCM_COPY_FROM");

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
	 * {@inheritDoc}
	 */
	@Override
	public int getNumberOfChildren() {
		return ChildIndices.CHILD_COUNT;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMinimumChildCount(int version) {
		return ChildIndices.CHILD_COUNT;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMaximumChildCount(int version) {
		return ChildIndices.CHILD_COUNT;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean childHasExpectedType(int index, long childClassId, int version) {
		return childClassId == FCMTree.CLASS_ID;
	}

	protected FCMTree<K, V> getTree() {
		return getChild(ChildIndices.TREE);
	}

	private void setTree(FCMTree<K, V> tree) {
		setChild(ChildIndices.TREE, tree);
	}

	/**
	 * Creates an instance of {@link FCMap}
	 *
	 * @param keyProvider
	 * 		Key deserializer
	 * @param valueProvider
	 * 		Value deserializer
	 */
	public FCMap(final SerializedObjectProvider keyProvider, final SerializedObjectProvider valueProvider) {
		this(DEFAULT_INITIAL_MAP_CAPACITY, keyProvider, valueProvider);
	}

	/**
	 * Creates an instance of {@link FCMap}
	 *
	 * @param initialCapacity
	 * 		Initial capacity of internal hash map
	 * @param keyProvider
	 * 		Key deserializer
	 * @param valueProvider
	 * 		Value deserializer
	 */
	public FCMap(final int initialCapacity,
			final SerializedObjectProvider keyProvider,
			final SerializedObjectProvider valueProvider) {
		this.internalMap = new FCHashMap<>(initialCapacity);
		setTree(new FCMTree<>(keyProvider, valueProvider));
		setImmutable(false);
		lock = new StampedLock();
	}

	/**
	 * Creates an instance of {@link FCMap}
	 *
	 * @param initialCapacity
	 * 		Initial capacity of internal hash map
	 */
	public FCMap(final int initialCapacity) {
		this(initialCapacity, null, null);
	}

	/**
	 * Creates an instance of {@link FCMap} that mustn't need the
	 * Key's and Value's SerializedObjectProviders.
	 * {@link SerializableHashable}
	 */
	public FCMap() {
		this(DEFAULT_INITIAL_MAP_CAPACITY, null, null);
	}

	/**
	 * Creates an immutable FCMap based a provided FCMap
	 *
	 * @param map
	 * 		An FCMap
	 */
	protected FCMap(final FCMap<K, V> map) {
		super();
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
		final long stamp = lock.readLock();
		try {
			return getTree().size();
		} finally {
			lock.unlockRead(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * Creates an immutable fast copy of this FCMap.
	 *
	 * @return A fast copied FCMap
	 */
	@Override
	public FCMap<K, V> copy() {
		throwIfImmutable();
		throwIfReleased();
		final long stamp = lock.readLock();
		try {
			return new FCMap<>(this);
		} finally {
			lock.unlockRead(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * Deserializes an FCMap into the current object, from the provided DataInputStream.
	 *
	 * @param inStream
	 * 		the stream to read from
	 * @throws IOException
	 *        {@inheritDoc}
	 */
	@Override
	public void copyFrom(SerializableDataInputStream inStream) throws IOException {
		final long stamp = lock.writeLock();
		try {
			getTree().copyFrom(inStream);
		} finally {
			lock.unlockWrite(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @param inStream
	 * 		the stream to read from
	 * @throws IOException
	 *        {@inheritDoc}
	 */
	@Override
	public void copyFromExtra(SerializableDataInputStream inStream) throws IOException {
		final long stamp = lock.writeLock();
		try {
			final long startTime = System.nanoTime();
			final int beginMarker = inStream.readInt();
			if (FCMTree.BEGIN_MARKER_VALUE != beginMarker) {
				throw new IOException("The stream is not at the beginning of a serialized FCMap");
			}

			// Discard the version number
			inStream.readLong();

			final byte[] rootHash = FCMTree.deserializeRootHash(inStream);
			final List<FCMLeaf<K, V>> leaves = getTree().copyTreeFrom(inStream);
			final int endMarker = inStream.readInt();
			if (FCMTree.END_MARKER_VALUE != endMarker) {
				throw new IOException("The serialized FCMap stream ends unexpectedly");
			}

			if (this.internalMap != null) {
				this.internalMap.release();
			}
			this.internalMap = new FCHashMap<>(leaves.size());
			for (FCMLeaf<K, V> leaf : leaves) {
				this.internalMap.put(leaf.getKey(), leaf);
			}

			final long endTime = System.nanoTime();
			final long totalExecutionTime = (endTime - startTime) / 1_000_000;
			LOG.trace(COPY_FROM, "copyFrom took {} milliseconds for a tree of size {}", () -> totalExecutionTime,
					leaves::size);
		} finally {
			lock.unlockWrite(stamp);
		}
	}

	@Override
	protected void onRelease() {
		internalMap.release();
	}

	/**
	 * After calling this function, read operations against this FCMap will be performed in O(n) time
	 * instead of O(m) time (where m is the number of recent mutations on a key).
	 *
	 * Calling this method allows for the garbage collector to prune the size of the data structure.
	 * It also allows for newer copies to perform read operations more quickly by reducing the number
	 * of recent modifications.
	 *
	 * An FCMap copy is not required to call this method before being deleted.
	 */
	@Override
	public void archive() {
		final long stamp = lock.writeLock();
		try {
			if (!isImmutable()) {
				throw new IllegalStateException("A mutable FCMap may not have fast read access revoked.");
			}
			if (!internalMap.isReleased()) {
				this.internalMap.release();
			}
		} finally {
			lock.unlockWrite(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 *
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
		final long stamp = lock.writeLock();
		try {
			final FCMLeaf<K, V> leaf = this.internalMap.remove(key);
			if (leaf == null) {
				return null;
			}

			final V value = leaf.getValue();
			getTree().delete(leaf);
			this.invalidateHash();
			return value;
		} finally {
			lock.unlockWrite(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 *
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
	 * This operation takes O(1) time if it is the original instance. The
	 * copies of an FCMap take O(n) to search for an element.
	 * </p>
	 * <p>
	 * Reading a value while a another transaction is modifying it is allowed,
	 * but some fields might have the old values and other fields might
	 * have the new values.
	 * </p>
	 */
	@Override
	public V get(final Object key) {
		final long stamp = lock.readLock();
		try {
			final FCMLeaf<K, V> leaf;
			if (this.internalMap.isReleased()) {
				leaf = getTree().findLeafByKey((K) key);
			} else {
				leaf = this.internalMap.get(key);
			}

			return leaf == null ? null : leaf.getValue();
		} finally {
			lock.unlockRead(stamp);
		}
	}

	public V getForModify(final Object key) {
		throwIfImmutable();
		final long stamp = lock.readLock();
		try {
			final FCMLeaf<K, V> leaf = this.internalMap.get(key);
			if (leaf == null) {
				return null;
			}

			return leaf.getValueForModify();
		} finally {
			lock.unlockRead(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 *
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
		final long stamp = lock.writeLock();
		try {
			if (this.internalMap.containsKey(key)) {
				return this.replaceInternal(key, value);
			} else {
				final FCMLeaf<K, V> leaf = new FCMLeaf<>(key, value);
				getTree().insert(leaf);
				this.internalMap.put(key, leaf);
				this.invalidateHash();
				return null;
			}
		} finally {
			lock.unlockWrite(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 *
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
	 *
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
	public boolean containsKey(Object key) {
		return this.internalMap.containsKey(key);
	}

	/**
	 * {@inheritDoc}
	 *
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
		final long stamp = lock.readLock();
		try {
			return getTree().isEmpty();
		} finally {
			lock.unlockRead(stamp);
		}
	}

	/**
	 * The implementation of replace without locks. This allows for the replace operation to be performed
	 * while a lock is already held by the outer context.
	 */
	private V replaceInternal(K key, V value) {
		final FCMLeaf<K, V> oldLeaf = this.internalMap.get(key);
		if (oldLeaf == null) {
			return null;
		}

		// Invalidate the hash of value since the value may have changed.
		// Fail-safe if app developer does not clear the value hash when updating (not guaranteed to work in all cases).
		if (value != null) {
			invalidateTree(value);
		}

		this.invalidateHash();
		if (oldLeaf.getValue() == value) {
			oldLeaf.nullifyHashPath();
			getTree().invalidateHash();
			return value;
		}

		final FCMLeaf<K, V> newLeaf = new FCMLeaf<>(key, value);
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
	public V replace(K key, V value) {
		throwIfImmutable();
		final long stamp = lock.writeLock();
		try {
			return replaceInternal(key, value);
		} finally {
			lock.unlockWrite(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * Removes all of the mappings from this map.
	 * The map will be empty after this call returns.
	 */
	@Override
	public void clear() {
		throwIfImmutable();
		final long stamp = lock.writeLock();
		try {
			clearWithoutLocking();
		} finally {
			lock.unlockWrite(stamp);
		}
	}

	/**
	 * Equivalent to calling clear() but without first acquiring a write lock. Expects that the caller will have already
	 * locked this data structure.
	 *
	 * This is needed since FCMap's locks are not reentrant (due to performance constraints).
	 */
	public void clearWithoutLocking() {
		this.internalMap.clear();
		getTree().clear();
		this.invalidateHash();
	}

	/**
	 * {@inheritDoc}
	 *
	 * Returns a mutable Set view of the keys contained in this map at the moment the method is called.
	 * <p>
	 * If the map is modified later on, this set remains invariant to those changes.
	 * </p>
	 *
	 * @return a mutable set view of the keys contained in this map at the moment the method is called.
	 */
	@Override
	public Set<K> keySet() {
		final long stamp = lock.readLock();
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
			lock.unlockRead(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 *
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
		final long stamp = lock.readLock();
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
			lock.unlockRead(stamp);
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
		final long stamp = lock.readLock();
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
			lock.unlockRead(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * Returns {@code true} if this map maps one or more xkeys to the
	 * specified value.
	 *
	 * @param value
	 * 		value whose presence in this map is to be tested
	 * @return {@code true} if this map maps one or more keys to the
	 * 		specified value
	 */
	@Override
	public boolean containsValue(Object value) {
		final long stamp = lock.readLock();
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
			lock.unlockRead(stamp);
		}
	}

	/**
	 * {@inheritDoc}
	 *
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
		for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
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
	public boolean equals(Object o) {
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
		final long stamp = lock.readLock();
		try {
			final Hash hash = getTree().getHash();
			if (hash == null) {
				return super.hashCode();
			}

			return hash.hashCode();
		} finally {
			lock.unlockRead(stamp);
		}
	}

	/**
	 * @return The root hash value
	 */
	public Hash getRootHash() {
		final long stamp = lock.readLock();
		try {
			return getRootHashWithoutLocking();
		} finally {
			lock.unlockRead(stamp);
		}
	}

	/**
	 * Equivalent to calling getRootHash() but without first acquiring a read lock.
	 * Expects that the caller will have already locked this data structure.
	 *
	 * This is needed since FCMap's locks are not reentrant (due to performance constraints).
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
	public void initialize(final MerkleInternal oldNode) {
		final Iterator<FCMLeaf<K, V>> leafIterator = getTree().leafIterator();
		while (leafIterator.hasNext()) {
			final FCMLeaf<K, V> nextLeaf = leafIterator.next();
			this.internalMap.put(nextLeaf.getKey(), nextLeaf);
		}

		LOG.debug(RECONNECT.getMarker(), "FCMap Initialized [ internalMapSize = {}, treeSize = {} ]",
				internalMap::size, () -> getTree().size());
	}
}
