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

package com.swirlds.common.merkle.io;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableAbbreviated;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.IllegalChildCountException;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;

import static com.swirlds.common.io.SerializableStreamConstants.MerkleSerializationProtocolVersion.ADDED_OPTIONS;
import static com.swirlds.common.io.SerializableStreamConstants.MerkleSerializationProtocolVersion.CURRENT;
import static com.swirlds.common.io.SerializableStreamConstants.MerkleSerializationProtocolVersion.ORIGINAL;
import static com.swirlds.common.io.SerializableStreamConstants.NULL_CLASS_ID;

/**
 * A SerializableDataInputStream that can also handle merkle tree.
 */
public class MerkleDataInputStream extends SerializableDataInputStream {

	protected boolean abbreviated;

	protected Queue<PartiallyConstructedMerkleInternal> internalNodes;

	MerkleNode root;

	/**
	 * Creates a FCDataInputStream that uses the specified
	 * underlying InputStream.
	 *
	 * @param in
	 * 		the specified input stream.
	 * @param abbreviated
	 * 		whether the nodes are serialized in an abbreviated form
	 */
	public MerkleDataInputStream(InputStream in, boolean abbreviated) {
		super(in);
		this.abbreviated = abbreviated;
		this.internalNodes = new LinkedList<>();
	}

	/**
	 * Add a child to its parent.
	 *
	 * @param child
	 * 		the child to be added
	 */
	private void addToParent(MerkleNode child) {
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
	private void finishReadingLeaf(MerkleLeaf node, int version) throws IOException {
		if (abbreviated && node.isDataExternal()) {
			Hash hash = readSerializable();
			((SerializableAbbreviated) node).deserializeAbbreviated(this, hash, version);
		} else {
			node.deserialize(this, version);
		}
		addToParent(node);
		validateFlag(node);
	}

	/**
	 * Finish deserializing an internal node.
	 *
	 * @param node
	 * 		the internal node to be read
	 * @param version
	 * 		version of this internal node
	 */
	private void finishReadingInternal(MerkleInternal node, int version) throws IOException {
		int childCount = readInt();

		if (childCount < node.getMinimumChildCount(version) || childCount > node.getMaximumChildCount(version)) {
			throw new IllegalChildCountException(node.getClassId(), version, node.getMinimumChildCount(version),
					node.getMaximumChildCount(version), childCount);
		}

		addToParent(node);
		if (childCount > 0) {
			internalNodes.add(new PartiallyConstructedMerkleInternal(node, version, childCount));
		}
	}

	/**
	 * Read the node from the stream.
	 *
	 * @param options
	 * 		the options we used when reading the node
	 */
	void readNextNode(MerkleTreeSerializationOptions options) throws IOException {
		long classId = readLong();
		if (classId == NULL_CLASS_ID) {
			addToParent(null);
			return;
		}

		MerkleNode node = ConstructableRegistry.createObject(classId);
		if (node == null) {
			throw new com.swirlds.common.io.ClassNotFoundException(classId);
		}

		int classVersion = readInt();

		validateVersion(node, classVersion);

		if (node.isLeaf()) {
			finishReadingLeaf(node.asLeaf(), classVersion);
		} else {
			finishReadingInternal(node.asInternal(), classVersion);
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
	 * @return
	 * 		the merkle tree read from the stream
	 * @throws IOException
	 * 		thrown when version or the options or nodes count are invalid
	 */
	@SuppressWarnings("unchecked")
	public <T extends MerkleNode> T readMerkleTree(int maxNumberOfNodes) throws IOException {
		int merkleVersion = readInt();
		if (merkleVersion < ORIGINAL ||
				merkleVersion > CURRENT) {
			throw new IOException("Unhandled merkle version");
		}
		MerkleTreeSerializationOptions options;
		if (merkleVersion >= ADDED_OPTIONS) {
			options = readSerializable(false, MerkleTreeSerializationOptions::new);
		} else {
			boolean streamIsAbbreviated = readBoolean();
			options = MerkleTreeSerializationOptions.builder().setAbbreviated(streamIsAbbreviated);
		}
		if (options.isAbbreviated() != abbreviated) {
			throw new IOException("Stream is " + (options.isAbbreviated() ? "" : "not ") +
					"abbreviated, but this object is configured to deserialize data that is" +
					(abbreviated ? "" : " not") + " abbreviated.");
		}

		boolean rootIsNull = readBoolean();
		if (rootIsNull) {
			return null;
		}

		int nodesCount = 0;
		while (internalNodes.size() > 0 || root == null) {
			nodesCount++;
			if (nodesCount > maxNumberOfNodes) {
				throw new IOException("Node count exceeds maximum value.");
			}
			readNextNode(options);
		}

		if (root != null) {
			root.initializeTree();
		}

		return (T) root;
	}
}
