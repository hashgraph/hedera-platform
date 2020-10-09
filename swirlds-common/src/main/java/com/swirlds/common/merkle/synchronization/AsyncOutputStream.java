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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.threading.StandardWorkGroup;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Allows a thread to asynchronously send data over a SerializableDataOutputStream.
 *
 * This object is not thread safe. Only one thread should attempt to send data over this stream at any point in time.
 */
public class AsyncOutputStream {

	protected SerializableDataOutputStream out;

	protected BlockingQueue<SelfSerializable> outgoingMessages;

	protected volatile boolean alive;

	/**
	 * The number of messages that have been written to the stream but have not yet been flushed
	 */
	private int unflushedMessages;

	public AsyncOutputStream(SerializableDataOutputStream out, StandardWorkGroup workGroup) {
		this.out = out;
		this.outgoingMessages = new LinkedBlockingQueue<>();
		this.alive = true;
		start(workGroup);
	}

	private void start(StandardWorkGroup workGroup) {
		if (!ReconnectSettingsFactory.get().isAsyncStreams()) {
			return;
		}
		workGroup.execute("async-output-stream", this::run);
	}

	/**
	 * Flush the stream if necessary.
	 * @return true if the stream was flushed.
	 */
	private void flushIfRequired() {
		if (unflushedMessages > ReconnectSettingsFactory.get().getAsyncOutputStreamMaxUnflushedMessages()) {
			flush();
		}
	}

	protected boolean flush() {
		if (unflushedMessages > 0) {
			try {
				out.flush();
			} catch (IOException e) {
				throw new MerkleSynchronizationException(e);
			}
			unflushedMessages = 0;
			return true;
		}
		return false;
	}

	/**
	 * Send the next message if possible.
	 * @return true if a message was sent.
	 */
	protected boolean handleNextMessage() {
		if (outgoingMessages.size() > 0) {
			SelfSerializable message = outgoingMessages.remove();
			try {
				serializeMessage(message);
			} catch (IOException e) {
				throw new MerkleSynchronizationException(e);
			}
			unflushedMessages += 1;
			return true;
		}
		return false;
	}

	protected void serializeMessage(SelfSerializable message) throws IOException {
		message.serialize(out);
	}

	/**
	 * {@inheritDoc}
	 */
	public void run() {
		while ((alive || outgoingMessages.size() > 0) && !Thread.currentThread().isInterrupted()) {
			flushIfRequired();
			boolean workDone = handleNextMessage();
			if (!workDone) {
				workDone = flush();
				if (!workDone) {
					try {
						Thread.sleep(0, 1);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
		}
		flush();
	}

	private void asyncWrite(SelfSerializable message) throws InterruptedException {
		outgoingMessages.put(message);
	}

	private void syncWrite(SelfSerializable message) {
		try {
			out.writeSerializable(message, false);
			out.flush();
		} catch (IOException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Send a message asynchronously. Messages are guaranteed to be delivered in the order sent.
	 */
	public void sendAsync(SelfSerializable message) throws InterruptedException {
		if (!alive) {
			throw new MerkleSynchronizationException("Messages can not be sent after close has been called.");
		}
		if (ReconnectSettingsFactory.get().isAsyncStreams()) {
			asyncWrite(message);
		} else {
			syncWrite(message);
		}
	}

	/**
	 * Close this buffer and release resources.
	 * If there are still messages awaiting transmission then resources will not be immediately freed.
	 */
	public void close() {
		alive = false;
	}
}
