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

package com.swirlds.virtualmap.internal.hash;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.merkle.VirtualInternalNode;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;

import java.util.Objects;

import static com.swirlds.virtualmap.internal.Path.INVALID_PATH;
import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;

/**
 * A POJO containing the information needed by a future thread to be able to create a hash for some leaf
 * or internal node. The class itself is somewhat intentionally messy. To cut down on garbage collection
 * and object allocation for this very hot code path, we create these objects and then reuse them over
 * and over. Sometimes they need to represent a hash job for a leaf, and sometimes for an internal node,
 * so they have references to both types of records. The {@code left} and {@code right} hashes are null
 * for leaf types, but present for internals. All jobs have a path, and the {@code hash} keeps track of
 * the final result.
 *
 * @param <K>
 *     The key type
 * @param <V>
 *     The value type
 */
final class HashJob<K extends VirtualKey<? super K>, V extends VirtualValue> implements Hashable {
	/**
	 * A singleton reference to the Cryptography libraries. Used for hashing.
	 */
	private static final Cryptography CRYPTO = CryptoFactory.getInstance();

	private static final Hash NULL_HASH = CRYPTO.getNullHash();

	private long path;
	private Hash left;
	private Hash right;
	private VirtualLeafRecord<K, V> leaf;
	private VirtualInternalRecord internal;
	private volatile Hash hash;

	HashJob() {
	}

	void dirtyLeaf(final long path, final VirtualLeafRecord<K, V> leaf) {
		this.path = path;
		this.leaf = leaf;
	}

	void dirtyInternal(final long path, final VirtualInternalRecord internal, final Hash left, final Hash right) {
		this.path = path;
		this.internal = internal;
		this.left = left;
		this.right = right;
	}

	void copy(HashJob<K, V> other) {
		this.path = other.path;
		this.left = other.left;
		this.right = other.right;
		this.leaf = other.leaf;
		this.internal = other.internal;
		this.hash = other.hash;
	}

	void reset() {
		this.path = INVALID_PATH;
		this.left = this.right = null;
		this.hash = null;
		this.leaf = null;
		this.internal = null;
	}

	void hash(final HashBuilder builder) {
		if (leaf != null) {
			hash = CRYPTO.digestSync(leaf);
			leaf.setHash(hash);
		} else {
			Objects.requireNonNull(internal);

			final long classId = path == ROOT_PATH
					? VirtualRootNode.CLASS_ID
					: VirtualInternalNode.CLASS_ID;

			final int serId = path == ROOT_PATH
					? VirtualRootNode.ClassVersion.ORIGINAL
					: VirtualInternalNode.SERIALIZATION_VERSION;

			final Hash leftHash = left == null ? NULL_HASH : left;
			final Hash rightHash = right == null ? NULL_HASH : right;

			builder.reset();
			builder.update(classId);
			builder.update(serId);
			builder.update(leftHash);
			builder.update(rightHash);
			hash = builder.build();
			internal.setHash(hash);
		}
	}

	@Override
	public Hash getHash() {
		return hash;
	}

	@Override
	public void setHash(final Hash hash) {
		this.hash = hash;
	}

	VirtualInternalRecord getInternal() {
		return internal;
	}

	long getPath() {
		return path;
	}

	VirtualLeafRecord<K, V> getLeaf() {
		return leaf;
	}
}
