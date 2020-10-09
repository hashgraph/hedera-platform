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

package com.swirlds.common.merkle.io;

import com.swirlds.common.io.SerializableAbbreviated;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.iterators.MerkleBreadthFirstIterator;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import static com.swirlds.common.io.SerializableStreamConstants.MerkleSerializationProtocolVersion.CURRENT;

/**
 * A SerializableDataOutputStream that also handles merkle trees.
 */
public class MerkleDataOutputStream extends SerializableDataOutputStream {

	private final MerkleTreeSerializationOptions options;

	/**
	 * Creates a new output stream to write data to the specified
	 * underlying output stream.
	 *
	 * @param out
	 * 		the underlying output stream, to be saved for later use.
	 * @param abbreviated
	 * 		If true then this stream will emit abbreviated data.
	 * @deprecated use {@link #MerkleDataOutputStream(OutputStream, MerkleTreeSerializationOptions)} instead
	 */
	@Deprecated
	public MerkleDataOutputStream(OutputStream out, boolean abbreviated) {
		this(out, MerkleTreeSerializationOptions.defaults().setAbbreviated(abbreviated));
	}

	/**
	 * Creates a new output stream to write data to the specified
	 * underlying output stream.
	 *
	 * @param out
	 * 		the underlying output stream, to be saved for later use.
	 * @param options
	 * 		all options for writing the tree
	 */
	public MerkleDataOutputStream(OutputStream out, MerkleTreeSerializationOptions options) {
		super(out);
		this.options = options;
	}

	/**
	 * Writes a MerkleInternal node to the stream.
	 */
	private void writeMerkleInternal(MerkleInternal node) throws IOException {
		writeLong(node.getClassId());
		writeInt(node.getVersion());
		writeInt(node.getNumberOfChildren());
	}

	/**
	 * Write a serializable object in abbreviated form.
	 *
	 * @param serializable
	 * 		The object to serialize. Does not accept null values.
	 * @param writeClassId
	 * 		whether to write the class ID or not
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	protected void writeSerializableAbbreviated(SerializableAbbreviated serializable, boolean writeClassId)
			throws IOException {
		if (serializable == null) {
			throw new NullPointerException();
		}
		writeClassIdVersion(serializable, writeClassId);
		writeSerializable(serializable.getHash(), true);
		serializable.serializeAbbreviated(this);
		writeFlag(serializable.getClassId());
	}

	private void writeMerkleLeaf(MerkleLeaf leaf) throws IOException {
		if (options.isAbbreviated() && leaf != null && leaf.isDataExternal()) {
			writeSerializableAbbreviated((SerializableAbbreviated) leaf, true);
		} else {
			writeSerializable(leaf, true);
		}
	}

	/**
	 * Writes a merkle tree to a stream.
	 *
	 * @param root
	 * 		the root of the tree
	 */
	public void writeMerkleTree(MerkleNode root) throws IOException {
		writeInt(CURRENT);
		writeSerializable(options, false);
		writeBoolean(root == null);
		Iterator<MerkleNode> it = new MerkleBreadthFirstIterator<>(root);
		while (it.hasNext()) {
			MerkleNode node = it.next();
			if (node == null || node.isLeaf()) {
				writeMerkleLeaf((MerkleLeaf) node);
			} else {
				writeMerkleInternal((MerkleInternal) node);
			}
			if (node != null && options.getWriteHashes()) {
				writeSerializable(node.getHash(), false);
			}
		}
	}
}
