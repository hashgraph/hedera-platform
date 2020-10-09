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

package com.swirlds.common.crypto;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.IOException;

/**
 * Maintains a runningHash which is updated each time adding a new Hash
 */
public class RunningHash extends AbstractSerializableHashable {
	private static final long CLASS_ID = 0x91c6f7057745ac99L;
	private static final int CLASS_VERSION = 1;
	private static final DigestType digestType = DigestType.SHA_384;

	public RunningHash() {
	}

	public RunningHash(Hash initialHash) {
		setHash(initialHash);
	}

	/**
	 * calculates Hash for the concatenation of current runningHash and the given Hash;
	 * updates current runningHash to be the calculated Hash
	 *
	 * @param hash
	 * 		new Hash for calculating running hash
	 * @return updated runningHash
	 */
	public Hash addAndDigest(Hash hash) {
		// update current Hash
		CryptoFactory.getInstance().calcRunningHash(this, hash, digestType);
		return getHash();
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		Hash readHash = in.readSerializable();
		setHash(readHash);
	}

	/**
	 * we only serialize prev and lastAddedHash because we calculate current runningHash with these two fields
	 *
	 * @param out
	 * 		The stream to write to.
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeSerializable(getHash(), true);
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return CLASS_VERSION;
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder()
				.append(getHash().hashCode()).build();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || obj.getClass() != getClass()) {
			return false;
		}
		if (this == obj) {
			return true;
		}
		RunningHash that = (RunningHash) obj;
		return new EqualsBuilder().
				append(this.getHash(), that.getHash()).
				isEquals();
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).
				append("hash", getHash()).toString();
	}
}
