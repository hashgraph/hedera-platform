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

package com.swirlds.common.merkle.io;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.ExternalSelfSerializable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.IllegalChildCountException;
import com.swirlds.common.merkle.io.internal.MerkleTreeSerializationOptions;
import com.swirlds.common.merkle.io.internal.PartiallyConstructedMerkleInternal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;

import static com.swirlds.common.io.SerializableStreamConstants.MerkleSerializationProtocolVersion.CURRENT;
import static com.swirlds.common.io.SerializableStreamConstants.NULL_CLASS_ID;
import static com.swirlds.common.merkle.copy.MerkleInitialize.initializeTreeAfterDeserialization;
import static com.swirlds.common.merkle.copy.MerkleInitialize.initializeTreeAfterExternalDeserialization;
import static com.swirlds.common.merkle.io.SerializationStrategy.DEFAULT_MERKLE_INTERNAL;
import static com.swirlds.common.merkle.io.SerializationStrategy.EXTERNAL_SELF_SERIALIZATION;
import static com.swirlds.common.merkle.io.SerializationStrategy.SELF_SERIALIZATION;

/**
 * A SerializableDataInputStream that can also handle merkle tree.
 */
public class MerkleDataInputStream extends SerializableDataInputStream {

	private final File externalDirectory;
	private final Queue<PartiallyConstructedMerkleInternal> internalNodes;
	private MerkleNode root;

	/**
	 * Creates a FCDataInputStream that uses the specified
	 * underlying InputStream.
	 *
	 * @param in
	 * 		the specified input stream.
	 */
	public MerkleDataInputStream(final InputStream in) {
		this(in, null);
	}

	public MerkleDataInputStream(final InputStream in, final File externalDirectory) {
		super(in);
		if (externalDirectory != null && (!externalDirectory.exists() || !externalDirectory.isDirectory())) {
			throw new IllegalArgumentException("invalid external directory " + externalDirectory.getAbsolutePath());
		}
		this.externalDirectory = externalDirectory;
		internalNodes = new LinkedList<>();
	}

	/**
	 * Add a child to its parent.
	 *
	 * @param child
	 * 		the child to be added
	 */
	private void addToParent(final MerkleNode child) {

		if (internalNodes.size() == 0) {
			root = child;
		} else {
			PartiallyConstructedMerkleInternal nextParent = internalNodes.peek();
			nextParent.addChild(child);
			if (nextParent.hasAllChildren()) {
				nextParent.finishConstruction();
				internalNodes.remove();
			}
		}
	}

	/**
	 * Finish deserializing a leaf node.
	 *
	 * @param node
	 * 		the leaf node to be read
	 * @param version
	 * 		version of this leaf
	 */
	private void finishReadingLeaf(
			final MerkleTreeSerializationOptions options,
			final MerkleLeaf node,
			final int version) throws IOException {

		if (options.isExternal() && node.supportedSerialization(version).contains(EXTERNAL_SELF_SERIALIZATION)) {
			final Hash hash = readSerializable();
			((ExternalSelfSerializable) node).deserializeExternal(this, externalDirectory, hash, version);
		} else if (node.supportedSerialization(version).contains(SELF_SERIALIZATION)) {
			node.deserialize(this, version);
		} else {
			throw new MerkleSerializationException("Illegal deserialization strategy requested", node);
		}
		addToParent(node);
	}

	/**
	 * Finish deserializing an internal node.
	 *
	 * @param node
	 * 		the internal node to be read
	 * @param version
	 * 		version of this internal node
	 */
	private void finishReadingInternal(
			final MerkleTreeSerializationOptions options,
			final MerkleInternal node,
			final int version) throws IOException {

		if (options.isExternal() && node.supportedSerialization(version).contains(EXTERNAL_SELF_SERIALIZATION)) {
			final Hash hash = readSerializable();
			((ExternalSelfSerializable) node).deserializeExternal(this, externalDirectory, hash, version);
			addToParent(node);
		} else if (node.supportedSerialization(version).contains(SELF_SERIALIZATION)) {
			((SelfSerializable) node).deserialize(this, version);
			addToParent(node);
		} else if (node.supportedSerialization(version).contains(DEFAULT_MERKLE_INTERNAL)) {
			final int childCount = readInt();

			if (childCount < node.getMinimumChildCount(version) || childCount > node.getMaximumChildCount(version)) {
				throw new IllegalChildCountException(node.getClassId(), version, node.getMinimumChildCount(version),
						node.getMaximumChildCount(version), childCount);
			}

			addToParent(node);
			if (childCount > 0) {
				internalNodes.add(new PartiallyConstructedMerkleInternal(node, version, childCount));
			}
		} else {
			throw new MerkleSerializationException("Illegal deserialization strategy requested", node);
		}
	}

	/**
	 * Read the node from the stream.
	 *
	 * @param options
	 * 		the options we used when reading the node
	 */
	private void readNextNode(
			final MerkleTreeSerializationOptions options) throws IOException {
		final long classId = readLong();
		if (classId == NULL_CLASS_ID) {
			addToParent(null);
			return;
		}

		final MerkleNode node = ConstructableRegistry.createObject(classId);
		if (node == null) {
			throw new com.swirlds.common.io.ClassNotFoundException(classId);
		}

		final int classVersion = readInt();

		validateVersion(node, classVersion);

		if (node.isLeaf()) {
			finishReadingLeaf(options, node.asLeaf(), classVersion);
		} else {
			finishReadingInternal(options, node.asInternal(), classVersion);
		}

		if (options.getWriteHashes()) {
			final Hash hash = readSerializable(false, Hash::new);
			if (!node.isSelfHashing()) {
				node.setHash(hash);
			}
		}
	}

	/**
	 * Read a merkle tree from a stream.
	 *
	 * @param maxNumberOfNodes
	 * 		maximum number of nodes to read
	 * @param <T>
	 * 		Type of the node
	 * @return the merkle tree read from the stream
	 * @throws IOException
	 * 		thrown when version or the options or nodes count are invalid
	 */
	public <T extends MerkleNode> T readMerkleTree(final int maxNumberOfNodes) throws IOException {
		final int merkleVersion = readInt();
		if (merkleVersion != CURRENT) {
			throw new MerkleSerializationException("Unhandled merkle version " + merkleVersion);
		}
		final MerkleTreeSerializationOptions options =
				readSerializable(false, MerkleTreeSerializationOptions::new);

		final boolean rootIsNull = readBoolean();
		if (rootIsNull) {
			return null;
		}

		int nodeCount = 0;
		while (internalNodes.size() > 0 || root == null) {
			nodeCount++;
			if (nodeCount > maxNumberOfNodes) {
				throw new MerkleSerializationException("Node count exceeds maximum value of " + maxNumberOfNodes + ".");
			}
			readNextNode(options);
		}

		if (options.isExternal()) {
			initializeTreeAfterExternalDeserialization(root);
		} else {
			initializeTreeAfterDeserialization(root);
		}

		return root.cast();
	}
}
