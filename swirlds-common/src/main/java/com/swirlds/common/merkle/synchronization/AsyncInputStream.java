/*
 * (c) 2016-2022 Swirlds, Inc.
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
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.threading.StandardWorkGroup;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Allows a thread to asynchronously read data from a SerializableDataInputStream.
 *
 * This object is not thread safe. Only one thread should attempt to read data from stream at any point in time.
 */
public class AsyncInputStream implements AutoCloseable {

	private final SerializableDataInputStream inputStream;

	private final BlockingQueue<SelfSerializable> anticipatedMessages;
	private final BlockingQueue<SelfSerializable> receivedMessages;

	/**
	 * The maximum amount of time to wait when reading a message.
	 */
	private final int pollTimeoutMs;

	/**
	 * Becomes 0 when the input thread is finished.
	 */
	private final CountDownLatch finishedLatch;

	private volatile boolean alive;

	private long totalAnticipatedMessages;
	private long totalMessagesRead;

	public AsyncInputStream(final SerializableDataInputStream inputStream, final StandardWorkGroup workGroup) {
		this(inputStream, workGroup, ReconnectSettingsFactory.get().getAsyncInputStreamTimeoutMilliseconds());
	}

	/**
	 * Create a new AsyncInputStream.
	 *
	 * @param inputStream
	 * 		An input stream to wrap.
	 * @param pollTimeoutMs
	 * 		The maximum amount of time to wait when reading a message.
	 */
	public AsyncInputStream(final SerializableDataInputStream inputStream, final StandardWorkGroup workGroup,
			final int pollTimeoutMs) {

		this.inputStream = inputStream;
		this.pollTimeoutMs = pollTimeoutMs;
		this.anticipatedMessages = new LinkedBlockingQueue<>();
		this.receivedMessages = new LinkedBlockingQueue<>();
		this.totalAnticipatedMessages = 0;
		this.totalMessagesRead = 0;
		this.finishedLatch = new CountDownLatch(1);
		this.alive = true;
		start(workGroup);

	}

	/**
	 * Returns true if the message pump is still running or false if the message pump has terminated or will terminate.
	 *
	 * @return true if the message pump is still running; false if the message pump has terminated or will terminate
	 */
	public boolean isAlive() {
		return alive;
	}

	public long getTotalAnticipatedMessages() {
		return totalAnticipatedMessages;
	}

	public long getTotalMessagesRead() {
		return totalMessagesRead;
	}

	public int getPollTimeoutMs() {
		return pollTimeoutMs;
	}

	protected SerializableDataInputStream getInputStream() {
		return inputStream;
	}

	/**
	 * {@inheritDoc}
	 */
	public void run() {
		try {
			while (isAlive() && !Thread.currentThread().isInterrupted()) {
				SelfSerializable message = null;
				try {
					message = anticipatedMessages.poll(10, TimeUnit.MILLISECONDS);
					if (message != null) {
						message.deserialize(inputStream, message.getVersion());
						receivedMessages.put(message);
					}
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				} catch (final IOException e) {
					throw new MerkleSynchronizationException(String.format(
							"Failed to deserialize object with class ID %d(0x%08X) (%s)",
							message.getClassId(), message.getClassId(), message.getClass().toString()), e);
				}
			}
		} finally {
			finishedLatch.countDown();
		}
	}

	/**
	 * Inform the buffer that a message is anticipated to be received at a future time.
	 * Anticipated messages must be received from the stream in the exact order
	 * anticipated or an exception will be thrown.
	 */
	public void addAnticipatedMessage(SelfSerializable message) {
		if (!ReconnectSettingsFactory.get().isAsyncStreams()) {
			return;
		}
		try {
			anticipatedMessages.put(message);
			totalAnticipatedMessages++;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Get an anticipated message. Blocks until the message is ready. Object returned will be
	 * the same object passed into addAnticipatedMessage, but deserialized from the stream.
	 */
	public <T> T readAnticipatedMessage() throws InterruptedException {
		if (ReconnectSettingsFactory.get().isAsyncStreams()) {
			return asyncRead();
		} else {
			return syncRead();
		}
	}

	/**
	 * This method should be called when the reader decides to stop reading from the stream (for example, if the reader
	 * encounters an exception). This method ensures that any resources used by the buffered messages are released.
	 */
	public void abort() {
		close();

		try {
			finishedLatch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		while (receivedMessages.size() > 0) {
			SelfSerializable message = receivedMessages.remove();
			if (message instanceof Releasable) {
				((Releasable) message).release();
			}
		}
	}

	/**
	 * Close this buffer and release resources.
	 */
	@Override
	public void close() {
		alive = false;
	}

	protected void start(StandardWorkGroup workGroup) {
		if (!ReconnectSettingsFactory.get().isAsyncStreams()) {
			return;
		}
		workGroup.execute("async-input-stream", this::run);
	}

	@SuppressWarnings("unchecked")
	private <T> T syncRead() {
		try {
			return inputStream.readSerializable();
		} catch (IOException e) {
			throw new MerkleSynchronizationException(e);
		}
	}

	/**
	 * Read a message. Will throw an exception if time equal to {@link #pollTimeoutMs} passes without a
	 * message becoming available.
	 */
	@SuppressWarnings("unchecked")
	private <T> T asyncRead() throws InterruptedException {
		if (totalMessagesRead >= totalAnticipatedMessages) {
			throw new MerkleSynchronizationException("No messages are anticipated");
		}
		totalMessagesRead++;

		T data = (T) receivedMessages.poll(pollTimeoutMs, TimeUnit.MILLISECONDS);
		if (data == null) {
			try {
				// An interrupt may not stop the thread if the thread is blocked on a stream read operation.
				// The only way to ensure that the stream is closed is to close the stream.
				inputStream.close();
			} catch (IOException e) {
				throw new MerkleSynchronizationException("Unable to close stream", e);
			}

			throw new MerkleSynchronizationException("Timed out waiting for data");
		}

		return data;
	}
}
