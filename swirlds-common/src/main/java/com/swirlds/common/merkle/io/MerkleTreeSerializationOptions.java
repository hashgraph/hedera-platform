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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.IOException;
import java.util.BitSet;

public class MerkleTreeSerializationOptions implements SelfSerializable {
	private static final long CLASS_ID = 0x76a4b529cfba0bccL;
	private static final int CLASS_VERSION = 1;

	public static final int MAX_LENGTH_BYTES = 128;

	private static final int WRITE_HASHES_BIT = 0;
	private static final int ABBREVIATED_BIT = 1;

	private BitSet options;

	public MerkleTreeSerializationOptions() {
		options = new BitSet();
	}

	public static MerkleTreeSerializationOptions builder() {
		return new MerkleTreeSerializationOptions();
	}

	public static MerkleTreeSerializationOptions defaults() {
		return builder();
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		this.options = BitSet.valueOf(in.readByteArray(MAX_LENGTH_BYTES));
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeByteArray(options.toByteArray());
	}

	public MerkleTreeSerializationOptions setWriteHashes(boolean writeHashes) {
		options.set(WRITE_HASHES_BIT, writeHashes);
		return this;
	}

	public boolean getWriteHashes() {
		return options.get(WRITE_HASHES_BIT);
	}

	public MerkleTreeSerializationOptions setAbbreviated(boolean abbreviated) {
		options.set(ABBREVIATED_BIT, abbreviated);
		return this;
	}

	public boolean isAbbreviated() {
		return options.get(ABBREVIATED_BIT);
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.append("abbreviated", isAbbreviated())
				.append("writeHashes", getWriteHashes())
				.toString();
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return CLASS_VERSION;
	}
}
