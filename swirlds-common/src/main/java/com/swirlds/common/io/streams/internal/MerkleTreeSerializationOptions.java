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

package com.swirlds.common.io.streams.internal;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.IOException;
import java.util.BitSet;

public class MerkleTreeSerializationOptions implements SelfSerializable {
	private static final long CLASS_ID = 0x76a4b529cfba0bccL;
	private static final int CLASS_VERSION = 1;

	public static final int MAX_LENGTH_BYTES = 128;

	private static final int WRITE_HASHES_BIT = 0;
	private static final int EXTERNAL_BIT = 1;

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

	public MerkleTreeSerializationOptions setExternal(final boolean external) {
		options.set(EXTERNAL_BIT, external);
		return this;
	}

	public boolean isExternal() {
		return options.get(EXTERNAL_BIT);
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.append("external", isExternal())
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
