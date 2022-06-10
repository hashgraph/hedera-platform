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

package com.swirlds.virtualmap.datasource;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.internal.Path;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;

import java.io.IOException;
import java.util.Objects;

/**
 * A {@link VirtualRecord} for leaf data. The leaf record contains the hash, path, key, and value.
 * This record is {@link SelfSerializable} to support reconnect and state saving, where it is necessary
 * to take leaf records from caches that are not yet flushed to disk and write them to the stream.
 * We never send hashes in the stream.
 */
public final class VirtualLeafRecord<K extends VirtualKey, V extends VirtualValue>
		extends VirtualRecord
		implements SelfSerializable {

	private static final long CLASS_ID = 0x410f45f0acd3264L;

	private static final class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	private K key;
	private V value;

	/**
	 * Create a new leaf record. This constructor is <strong>only</strong> used by the serialization engine.
	 * It creates a leaf with a totally invalid leaf path.
	 */
	public VirtualLeafRecord() {
		super(-1, null);
	}

	/**
	 * Create a new leaf record, supplying all data required by the record.
	 *
	 * @param path
	 * 		The path. Must be positive (since 0 represents a root node, which is never a leaf),
	 * 		or {@link Path#INVALID_PATH}.
	 * @param hash
	 * 		The hash. May be null.
	 * @param key
	 * 		The key for this record. This should normally never be null, but may be for
	 * 		{@link VirtualNodeCache#DELETED_LEAF_RECORD}
	 * 		or other uses where the leaf record is meant to represent some invalid state.
	 * @param value
	 * 		The value for this record, which can be null.
	 */
	public VirtualLeafRecord(final long path, final Hash hash, final K key, final V value) {
		super(path, hash);
		this.key = key;
		this.value = value;
	}

	/**
	 * Gets the key.
	 * @return
	 * 		The key. This <strong>may</strong> be null in some cases, such as when the record is meant to
	 *		represent an invalid state, or when it is in the middle of serialization. No leaf that represnts
	 *		an actual leaf will ever return null here.
	 */
	public K getKey() {
		return key;
	}

	/**
	 * Gets the value.
	 * @return
	 * 		The value. May be null.
	 */
	public V getValue() {
		return value;
	}

	/**
	 * Sets the value. May be null. Must set the hash to null if the value has changed.
	 *
	 * @param value
	 * 		The value.
	 */
	public void setValue(final V value) {
		if (this.value != value) {
			this.value = value;
			this.setHash(null);
		}
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
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeLong(this.getPath());
		out.writeSerializable(this.key, true);
		out.writeSerializable(this.value, true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		setPath(in.readLong());
		this.key = in.readSerializable();
		this.value = in.readSerializable();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		if (!super.equals(o)) {
			return false;
		}

		final VirtualLeafRecord<?, ?> that = (VirtualLeafRecord<?, ?>) o;
		return Objects.equals(key, that.key) && Objects.equals(value, that.value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), key, value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "VirtualLeafRecord{" +
				"key=" + key +
				", value=" + value +
				", path=" + getPath() +
				", hash=" + getHash() + '}';
	}
}
