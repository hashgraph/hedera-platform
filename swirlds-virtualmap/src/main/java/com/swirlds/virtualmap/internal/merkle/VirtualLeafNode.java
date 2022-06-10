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

package com.swirlds.virtualmap.internal.merkle;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.MerkleSerializationStrategy;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;

import java.io.IOException;
import java.util.Set;

/**
 * Implementation of a VirtualLeaf
 */
public final class VirtualLeafNode<K extends VirtualKey<? super K>, V extends VirtualValue>
		extends VirtualNode<K, V, VirtualLeafRecord<K, V>>
		implements MerkleLeaf {

	public static final long CLASS_ID = 0x499677a326fb04caL;

	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	private static final Set<MerkleSerializationStrategy> STRATEGIES = Set.of();

	public VirtualLeafNode() {
	}

	public VirtualLeafNode(final VirtualRootNode<K, V> root, final VirtualLeafRecord<K, V> virtualRecord) {
		super(root, virtualRecord);
	}

	/**
	 * Get the key represented held within this leaf.
	 *
	 * @return the key
	 */
	public K getKey() {
		return virtualRecord.getKey();
	}

	/**
	 * Get the value held within this leaf.
	 *
	 * @return the value
	 */
	public V getValue() {
		return virtualRecord.getValue();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<MerkleSerializationStrategy> supportedSerialization(final int version) {
		return STRATEGIES;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VirtualLeafNode<K, V> copy() {
		throw new UnsupportedOperationException("Don't use this");
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
	public String toString() {
		return "VirtualLeafNode{" + virtualRecord + "}";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeSerializable(virtualRecord, true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		this.virtualRecord = in.readSerializable();
	}
}
