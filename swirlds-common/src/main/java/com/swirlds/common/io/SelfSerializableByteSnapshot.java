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

package com.swirlds.common.io;

import com.swirlds.common.CommonUtils;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;

public class SelfSerializableByteSnapshot<T extends SelfSerializable> {
	private static final DigestType DIGEST_TYPE = DigestType.SHA_384;

	private final byte[] bytes;
	private final Hash hash;

	private SelfSerializableByteSnapshot(byte[] bytes, Hash hash) {
		this.bytes = bytes;
		this.hash = hash;
	}

	public static <T extends SelfSerializable> SelfSerializableByteSnapshot<T> createSnapshot(T ss) {
		try (
				ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
				HashingOutputStream hashOut = new HashingOutputStream(
						MessageDigest.getInstance(DIGEST_TYPE.algorithmName()),
						byteOut
				);
				SerializableDataOutputStream out = new SerializableDataOutputStream(hashOut);
		) {
			out.writeSerializable(ss, true);
			return new SelfSerializableByteSnapshot<>(
					byteOut.toByteArray(),
					new Hash(hashOut.getDigest(), DIGEST_TYPE)
			);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public T deserialize() {
		try (SerializableDataInputStream in = new SerializableDataInputStream(new ByteArrayInputStream(bytes));) {
			return in.readSerializable();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Hash getHash() {
		return hash;
	}

	public String getBytesAsHexString() {
		return CommonUtils.hex(bytes);
	}
}
