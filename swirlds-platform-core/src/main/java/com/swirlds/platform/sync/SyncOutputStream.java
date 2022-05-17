/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.platform.sync;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.network.ByteConstants;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.swirlds.common.io.extendable.ExtendableOutputStream.extendOutputStream;

public class SyncOutputStream extends SerializableDataOutputStream {
	private final CountingStreamExtension syncByteCounter;
	private final CountingStreamExtension connectionByteCounter;
	private final AtomicReference<Instant> requestSent;

	protected SyncOutputStream(OutputStream out,
			CountingStreamExtension syncByteCounter,
			CountingStreamExtension connectionByteCounter) {
		super(out);
		this.syncByteCounter = syncByteCounter;
		this.connectionByteCounter = connectionByteCounter;
		this.requestSent = new AtomicReference<>(null);
	}

	public static SyncOutputStream createSyncOutputStream(OutputStream out, int bufferSize) {
		CountingStreamExtension syncByteCounter = new CountingStreamExtension();
		CountingStreamExtension connectionByteCounter = new CountingStreamExtension();

		// we write the data to the buffer first, for efficiency
		return new SyncOutputStream(
				new BufferedOutputStream(
						extendOutputStream(out, syncByteCounter, connectionByteCounter),
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

	/**
	 * @return the time the last sync request was sent
	 */
	public Instant getRequestSentTime() {
		return requestSent.get();
	}

	/**
	 * Send a sync request
	 *
	 * @throws IOException
	 * 		if a stream exception occurs
	 */
	public void requestSync() throws IOException {
		writeByte(ByteConstants.COMM_SYNC_REQUEST);
		requestSent.set(Instant.now());
	}

	/**
	 * Accepts a previously requested sync
	 *
	 * @throws IOException
	 * 		if a stream exception occurs
	 */
	public void acceptSync() throws IOException {
		writeByte(ByteConstants.COMM_SYNC_ACK);
	}

	/**
	 * Rejects a previously requested sync
	 *
	 * @throws IOException
	 * 		if a stream exception occurs
	 */
	public void rejectSync() throws IOException {
		writeByte(ByteConstants.COMM_SYNC_NACK);
	}

	/**
	 * Write this node's generation numbers to an output stream
	 *
	 * @throws IOException
	 * 		if a stream exception occurs
	 */
	public void writeGenerations(final SyncGenerations generations) throws IOException {
		writeSerializable(generations, false);
	}

	/**
	 * Write to the {@link SyncOutputStream} the hashes of the tip events from this node's shadow graph
	 *
	 * @throws IOException
	 * 		iff the {@link SyncOutputStream} throws
	 */
	public void writeTipHashes(final List<Hash> tipHashes) throws IOException {
		writeSerializableList(tipHashes, false, true);
	}

	/**
	 * Write event data
	 *
	 * @param event
	 * 		the event to write
	 * @throws IOException
	 * 		iff the {@link SyncOutputStream} instance throws
	 */
	public void writeEventData(final EventImpl event) throws IOException {
		writeSerializable(event.getBaseEventHashedData(), false);
		writeSerializable(event.getBaseEventUnhashedData(), false);
	}
}
