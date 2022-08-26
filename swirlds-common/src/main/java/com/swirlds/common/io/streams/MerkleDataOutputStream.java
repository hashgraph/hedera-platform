/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.io.streams;

import com.swirlds.common.io.ExternalSelfSerializable;
import com.swirlds.common.io.streams.internal.MerkleSerializationProtocol;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.function.Predicate;

import static com.swirlds.common.merkle.iterators.MerkleIterationOrder.BREADTH_FIRST;
import static com.swirlds.logging.LogMarker.STATE_TO_DISK;

/**
 * A SerializableDataOutputStream that also handles merkle trees.
 */
public class MerkleDataOutputStream extends SerializableDataOutputStream {

	private static final Logger LOG = LogManager.getLogger(MerkleDataOutputStream.class);

	private static final Predicate<MerkleInternal> DESCENDANT_FILTER =
			node -> !(node instanceof ExternalSelfSerializable);

	/**
	 * Create a new merkle stream.
	 *
	 * @param out
	 * 		the output stream
	 */
	public MerkleDataOutputStream(final OutputStream out) {
		super(out);
	}

	/**
	 * Write a node that implements the type {@link ExternalSelfSerializable}.
	 */
	private void writeSerializableNode(
			final File directory,
			final ExternalSelfSerializable node) throws IOException {

		writeClassIdVersion(node, true);
		node.serialize(this, directory);
	}

	/**
	 * Default serialization algorithm for internal nodes that do not implement their own serialization.
	 */
	private void writeDefaultInternalNode(final MerkleInternal node) throws IOException {
		writeLong(node.getClassId());
		writeInt(node.getVersion());
		writeInt(node.getNumberOfChildren());
	}

	/**
	 * Writes a MerkleInternal node to the stream.
	 */
	private void writeInternal(final File directory, final MerkleInternal node) throws IOException {
		if (node instanceof ExternalSelfSerializable externalSelfSerializable) {
			writeSerializableNode(directory, externalSelfSerializable);
		} else {
			writeDefaultInternalNode(node);
		}
	}

	/**
	 * Write a leaf node to the stream.
	 */
	private void writeLeaf(final File directory, final MerkleLeaf node) throws IOException {
		writeSerializableNode(directory, node);
	}

	/**
	 * Write a null leaf to the stream.
	 */
	private void writeNull() throws IOException {
		writeSerializable(null, true);
	}

	/**
	 * Perform basic sanity checks on the output directory.
	 */
	@SuppressWarnings("DuplicatedCode")
	private static void validateDirectory(final File directory) {
		if (directory == null) {
			throw new IllegalArgumentException("directory must not be null");
		}
		if (!directory.exists()) {
			throw new IllegalArgumentException("directory " + directory + " does not exist");
		}
		if (!directory.isDirectory()) {
			throw new IllegalArgumentException("'directory' " + directory + " is not a directory");
		}
		if (!Files.isReadable(directory.toPath())) {
			throw new IllegalArgumentException("invalid read permissions for directory " + directory);
		}
		if (!Files.isWritable(directory.toPath())) {
			throw new IllegalArgumentException("invalid write permissions for directory " + directory);
		}

		final File[] contents = directory.listFiles();
		if (contents != null && contents.length > 0) {
			LOG.info(STATE_TO_DISK.getMarker(),
					"merkle tree being written to directory {} that is not empty", directory);
		}
	}

	/**
	 * Writes a merkle tree to a stream.
	 *
	 * @param directory
	 * 		a directory where additional data will be written
	 * @param root
	 * 		the root of the tree
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeMerkleTree(final File directory, final MerkleNode root) throws IOException {
		writeInt(MerkleSerializationProtocol.CURRENT);
		writeBoolean(root == null);

		validateDirectory(directory);

		if (root == null) {
			return;
		}

		root.treeIterator()
				.setOrder(BREADTH_FIRST)
				.setDescendantFilter(DESCENDANT_FILTER)
				.ignoreNull(false)
				.forEachRemainingWithIO((final MerkleNode node) -> {
					if (node == null) {
						writeNull();
					} else if (node.isLeaf()) {
						writeLeaf(directory, node.asLeaf());
					} else {
						writeInternal(directory, node.asInternal());
					}
				});
	}
}
