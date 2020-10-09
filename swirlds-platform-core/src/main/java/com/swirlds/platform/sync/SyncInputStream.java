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

package com.swirlds.platform.sync;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.extendable.CountingStreamExtension;
import com.swirlds.common.io.extendable.ExtendableInputStream;
import com.swirlds.common.io.extendable.HashingStreamExtension;
import com.swirlds.common.io.extendable.StreamExtensionList;

import java.io.BufferedInputStream;
import java.io.InputStream;

public class SyncInputStream extends SerializableDataInputStream {
	private final CountingStreamExtension syncByteCounter;
	private final HashingStreamExtension hasher;

	private SyncInputStream(InputStream in, CountingStreamExtension syncByteCounter, HashingStreamExtension hasher) {
		super(in);
		this.syncByteCounter = syncByteCounter;
		this.hasher = hasher;
	}

	public static SyncInputStream createSyncInputStream(InputStream in, int bufferSize) {
		CountingStreamExtension syncCounter = new CountingStreamExtension();
		HashingStreamExtension hasher = new HashingStreamExtension(DigestType.SHA_384);

		// the buffered reader reads data first, for efficiency
		return new SyncInputStream(
				new ExtendableInputStream<>(
						new BufferedInputStream(in, bufferSize),
						new StreamExtensionList(syncCounter, hasher)
				),
				syncCounter,
				hasher
		);
	}

	public CountingStreamExtension getSyncByteCounter() {
		return syncByteCounter;
	}

	public HashingStreamExtension getHasher() {
		return hasher;
	}
}
