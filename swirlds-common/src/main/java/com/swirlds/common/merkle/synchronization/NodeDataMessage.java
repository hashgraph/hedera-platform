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

package com.swirlds.common.merkle.synchronization;

import com.swirlds.common.Releasable;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static com.swirlds.common.io.SerializableStreamConstants.NULL_CLASS_ID;

/**
 * Used during the synchronization protocol to send data needed to reconstruct a node.
 */
public class NodeDataMessage implements Releasable, SelfSerializable {

	private final int version = 1;

	private final long classId = 0x98bc0d340d9bca1dL;

	private boolean currentNodeIsUpToDate;

	/**
	 * If currentNodeIsUpToDate, this value will be null.
	 *
	 * Otherwise, will hold a fully constructed node if a node is passed to the constructor or if node is a leaf,
	 * or a partially constructed node if the node is an internal node and this object was deserialized.
	 */
	private MerkleNode node;

	/**
	 * If initialized to contain an internal node, this will hold the hashes of that node's children.
	 */
	private List<Hash> childHashes;

	public NodeDataMessage() {
		currentNodeIsUpToDate = true;
	}

	public NodeDataMessage(MerkleNode node) throws MerkleSynchronizationException {
		currentNodeIsUpToDate = false;
		this.node = node;
		if (node instanceof MerkleInternal) {
			gatherChildHashes();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {

		out.writeBoolean(currentNodeIsUpToDate);
		if (currentNodeIsUpToDate) {
			return;
		}

		if (node == null) {
			out.writeLong(NULL_CLASS_ID);
			return;
		}

		out.writeLong(node.getClassId());
		out.writeInt(node.getVersion());

		if (node instanceof MerkleLeaf) {
			((MerkleLeaf) node).serialize(out);
		} else {
			out.writeSerializableList(childHashes, true, true);
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {

		currentNodeIsUpToDate = in.readBoolean();
		if (currentNodeIsUpToDate) {
			return;
		}

		long nodeType = in.readLong();

		if (nodeType == NULL_CLASS_ID) {
			node = null;
			return;
		}

		int nodeVersion = in.readInt();

		node = ConstructableRegistry.createObject(nodeType);
		if (node == null) {
			throw new IOException(
					String.format("Unable to construct node with class ID %d(0x%08X)", nodeType, nodeType));
		}

		if (node instanceof MerkleLeaf) {
			((MerkleLeaf) node).deserialize(in, nodeVersion);
		} else {
			try {
				childHashes = in.readSerializableList(MerkleInternal.MAX_CHILD_COUNT, true, Hash::new);
			} catch (Exception e) {
				throw new IOException(
						String.format(
								"Error while reading child hashes for node with class ID %d(0x%08X), class name '%s'",
								nodeType, nodeType, node.getClass().getName()),
						e);
			}

		}
	}

	protected void gatherChildHashes() throws MerkleSynchronizationException {
		childHashes = new LinkedList<>();
		MerkleInternal internal = (MerkleInternal) node;
		for (int childIndex = 0; childIndex < internal.getNumberOfChildren(); childIndex++) {
			MerkleNode child = internal.getChild(childIndex);
			if (child == null) {
				childHashes.add(CryptoFactory.getInstance().getNullHash());
			} else if (child.getHash() == null) {
				throw new MerkleSynchronizationException("Tree must be hashed prior to synchronization.");
			} else {
				childHashes.add(child.getHash());
			}
		}
	}

	public List<Hash> getChildHashes() {
		return childHashes;
	}

	public MerkleNode getNode() {
		return node;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return classId;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return version;
	}

	public boolean currentNodeIsUpToDate() {
		return currentNodeIsUpToDate;
	}

	public int getNumberOfChildren() {
		return childHashes == null ? 0 : childHashes.size();
	}

	public boolean isLeaf() {
		return node == null || node.isLeaf();
	}

	public String toString() {
		if (currentNodeIsUpToDate) {
			return "(current node is up to date)";
		} else {
			StringBuilder sb = new StringBuilder();
			sb.append(node.toString());
			if (!node.isLeaf()) {
				sb.append(": ");
				for (Hash hash : childHashes) {
					sb.append(hash).append(" ");
				}
			}
			return sb.toString();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void release() {
		if (node != null) {
			node.release();
		}
	}
}
