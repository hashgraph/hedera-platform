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

import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.io.extendable.CountingStreamExtension;
import com.swirlds.common.io.extendable.ExtendableOutputStream;
import com.swirlds.common.io.extendable.StreamExtensionList;

import java.io.BufferedOutputStream;
import java.io.OutputStream;

public class SyncOutputStream extends SerializableDataOutputStream {
	private final CountingStreamExtension syncByteCounter;
	private final CountingStreamExtension connectionByteCounter;

	private SyncOutputStream(OutputStream out,
			CountingStreamExtension syncByteCounter,
			CountingStreamExtension connectionByteCounter) {
		super(out);
		this.syncByteCounter = syncByteCounter;
		this.connectionByteCounter = connectionByteCounter;
	}

	public static SyncOutputStream createSyncOutputStream(OutputStream out, int bufferSize) {
		CountingStreamExtension syncByteCounter = new CountingStreamExtension();
		CountingStreamExtension connectionByteCounter = new CountingStreamExtension();

		// we write the data to the buffer first, for efficiency
		return new SyncOutputStream(
				new BufferedOutputStream(
						new ExtendableOutputStream<>(
								out,
								new StreamExtensionList(syncByteCounter, connectionByteCounter)
						),
						bufferSize),
				syncByteCounter,
				connectionByteCounter
		);
	}

	public CountingStreamExtension getSyncByteCounter() {
		return syncByteCounter;
	}

	public CountingStreamExtension getConnectionByteCounter() {
		return connectionByteCounter;
	}
}
