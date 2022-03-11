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

import com.swirlds.common.io.ExternalSelfSerializable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.io.internal.MerkleTreeSerializationOptions;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.function.Predicate;

import static com.swirlds.common.io.SerializableStreamConstants.MerkleSerializationProtocolVersion.CURRENT;
import static com.swirlds.common.merkle.io.SerializationStrategy.DEFAULT_MERKLE_INTERNAL;
import static com.swirlds.common.merkle.io.SerializationStrategy.EXTERNAL_SELF_SERIALIZATION;
import static com.swirlds.common.merkle.io.SerializationStrategy.SELF_SERIALIZATION;
import static com.swirlds.common.merkle.iterators.MerkleIterationOrder.BREADTH_FIRST;

/**
 * A SerializableDataOutputStream that also handles merkle trees.
 */
public class MerkleDataOutputStream extends SerializableDataOutputStream {

	private final MerkleTreeSerializationOptions options;
	private File externalDirectory;

	/**
	 * Create a new merkle stream.
	 *
	 * @param out
	 * 		the output stream
	 */
	public MerkleDataOutputStream(final OutputStream out) {
		super(out);

		options = MerkleTreeSerializationOptions.defaults();
	}

	/**
	 * Does this stream allow external writes?
	 *
	 * @return true if external serialization is enabled
	 */
	public boolean isExternal() {
		return options.isExternal();
	}

	/**
	 * Set if external serialization is enabled.
	 *
	 * @param external
	 * 		if true then enable external serialization, else disable it
	 * @return this object
	 */
	public MerkleDataOutputStream setExternal(final boolean external) {
		options.setExternal(external);
		return this;
	}

	/**
	 * Does this stream write the hashes nodes?
	 *
	 * @return true if hashes are written
	 */
	public boolean getWriteHashes() {
		return options.getWriteHashes();
	}

	/**
	 * Set if this stream writes the hashes of nodes.
	 *
	 * @param writeHashes
	 * 		if true then write hashes of nodes to the stream
	 * @return this object
	 */
	public MerkleDataOutputStream setWriteHashes(final boolean writeHashes) {
		options.setWriteHashes(writeHashes);
		return this;
	}

	/**
	 * Get the external directory used for external serialization.
	 *
	 * @return the external directory, or null if no directory has been specified
	 */
	public File getExternalDirectory() {
		return externalDirectory;
	}

	/**
	 * Set the external directory used for external serialization.
	 *
	 * @param externalDirectory
	 * 		the directory to use for serialization
	 * @return this object
	 */
	public MerkleDataOutputStream setExternalDirectory(final File externalDirectory) {
		if (externalDirectory != null && !externalDirectory.exists() && externalDirectory.isDirectory()) {
			throw new IllegalArgumentException("invalid external directory " + externalDirectory.getAbsolutePath());
		}
		this.externalDirectory = externalDirectory;
		return this;
	}

	/**
	 * Write a node that implements the type {@link ExternalSelfSerializable}.
	 */
	private void writeExternalSelfSerializableNode(final MerkleNode node) throws IOException {
		writeClassIdVersion(node, true);
		writeSerializable(node.getHash(), true);
		((ExternalSelfSerializable) node).serializeExternal(this, externalDirectory);
	}

	/**
	 * Write a node that implements the type {@link SelfSerializable}.
	 */
	private void writeSerializableNode(final MerkleNode node) throws IOException {
		writeSerializable((SelfSerializable) node, true);
	}

	/**
	 * Default serialization algorithm for internal nodes that do not implement their own serializaiton.
	 */
	private void writeDefaultInternalNode(final MerkleInternal node) throws IOException {
		writeLong(node.getClassId());
		writeInt(node.getVersion());
		writeInt(node.getNumberOfChildren());
	}

	/**
	 * Writes a MerkleInternal node to the stream.
	 */
	private void writeInternal(final MerkleInternal node) throws IOException {
		final int version = node.getVersion();
		if (options.isExternal() && node.supportedSerialization(version).contains(EXTERNAL_SELF_SERIALIZATION)) {
			writeExternalSelfSerializableNode(node);
		} else if (node.supportedSerialization(version).contains(SELF_SERIALIZATION)) {
			writeSerializable(node.cast(), true);
		} else if (node.supportedSerialization(version).contains(DEFAULT_MERKLE_INTERNAL)) {
			writeDefaultInternalNode(node);
		} else {
			throw new MerkleSerializationException("illegal serialization strategy requested", node);
		}
	}

	/**
	 * Write a leaf node to the stream.
	 */
	private void writeLeaf(final MerkleLeaf node) throws IOException {
		final int version = node.getVersion();
		if (options.isExternal() && node.supportedSerialization(version).contains(EXTERNAL_SELF_SERIALIZATION)) {
			writeExternalSelfSerializableNode(node);
		} else if (node.supportedSerialization(version).contains(SELF_SERIALIZATION)) {
			writeSerializableNode(node);
		} else {
			throw new MerkleSerializationException("illegal serialization strategy requested", node);
		}
	}

	/**
	 * Write a null leaf to the stream.
	 */
	private void writeNull() throws IOException {
		writeSerializable(null, true);
	}

	/**
	 * Writes a merkle tree to a stream.
	 *
	 * @param root
	 * 		the root of the tree
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeMerkleTree(final MerkleNode root) throws IOException {
		writeInt(CURRENT);
		writeSerializable(options, false);
		writeBoolean(root == null);

		if (root == null) {
			return;
		}

		final Predicate<MerkleInternal> descendantFilter;
		if (options.isExternal()) {
			descendantFilter = node ->
					!node.supportedSerialization(node.getVersion()).contains(SELF_SERIALIZATION) &&
							!node.supportedSerialization(node.getVersion()).contains(EXTERNAL_SELF_SERIALIZATION);
		} else {
			descendantFilter = node -> !node.supportedSerialization(node.getVersion()).contains(SELF_SERIALIZATION);
		}

		final Iterator<MerkleNode> it = root.treeIterator()
				.setOrder(BREADTH_FIRST)
				.setDescendantFilter(descendantFilter)
				.ignoreNull(false);

		while (it.hasNext()) {
			final MerkleNode node = it.next();
			if (node == null) {
				writeNull();
			} else if (node.isLeaf()) {
				writeLeaf(node.asLeaf());
			} else {
				writeInternal(node.asInternal());
			}
			if (node != null && options.getWriteHashes()) {
				writeSerializable(node.getHash(), false);
			}
		}
	}
}
