/*
 * (c) 2016-2021 Swirlds, Inc.
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
import org.apache.commons.lang3.time.StopWatch;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Allows a thread to asynchronously send data over a SerializableDataOutputStream.
 *
 * This object is not thread safe. Only one thread should attempt to send data over this stream at any point in time.
 */
public class AsyncOutputStream implements AutoCloseable {


	/**
	 * The stream which all data is written to.
	 */
	private final SerializableDataOutputStream outputStream;

	/**
	 * A queue of messages that need to be written to the output stream.
	 */
	private final BlockingQueue<SelfSerializable> outgoingMessages;

	/**
	 * The time that has elapsed since the last flush was attempted.
	 */
	private final StopWatch timeSinceLastFlush;

	/**
	 * The maximum amount of time that is permitted to pass without a flush being attempted.
	 */
	private final int flushIntervalMs;

	/**
	 * If this becomes false then this object's worker thread will stop transmitting messages.
	 */
	private volatile boolean alive;

	/**
	 * The number of messages that have been written to the stream but have not yet been flushed
	 */
	private int bufferedMessageCount;


	/**
	 * Constructs a new instance using the given underlying {@link SerializableDataOutputStream} and {@link
	 * StandardWorkGroup}.
	 *
	 * @param outputStream
	 * 		the outputStream to which all objects are written
	 * @param workGroup
	 * 		the work group that should be used to execute this thread
	 */
	public AsyncOutputStream(final SerializableDataOutputStream outputStream, final StandardWorkGroup workGroup) {
		this.outputStream = outputStream;
		this.outgoingMessages = new LinkedBlockingQueue<>();
		this.alive = true;
		this.timeSinceLastFlush = new StopWatch();
		this.timeSinceLastFlush.start();
		this.flushIntervalMs =
				(int) Math.floor(ReconnectSettingsFactory.get().getAsyncInputStreamTimeoutMilliseconds() *
						ReconnectSettingsFactory.get().getAsyncOutputStreamFlushFraction());
		start(workGroup);
	}

	/**
	 * Returns the maximum time (in milliseconds) allowed to elapse before a flush is required.
	 *
	 * @return the maximum time (in milliseconds) allowed to elapse before a flush is required
	 */
	public int getFlushIntervalMs() {
		return flushIntervalMs;
	}

	/**
	 * Returns true if the message pump is still running or false if the message pump has terminated or will terminate.
	 *
	 * @return true if the message pump is still running; false if the message pump has terminated or will terminate
	 */
	public boolean isAlive() {
		return alive;
	}

	protected SerializableDataOutputStream getOutputStream() {
		return outputStream;
	}

	protected BlockingQueue<SelfSerializable> getOutgoingMessages() {
		return outgoingMessages;
	}

	/**
	 * {@inheritDoc}
	 */
	public void run() {
		while ((isAlive() || !outgoingMessages.isEmpty()) && !Thread.currentThread().isInterrupted()) {
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

	/**
	 * Send a message asynchronously. Messages are guaranteed to be delivered in the order sent.
	 */
	public void sendAsync(SelfSerializable message) throws InterruptedException {
		if (!isAlive()) {
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
	@Override
	public void close() {
		alive = false;
	}

	/**
	 * Send the next message if possible.
	 *
	 * @return true if a message was sent.
	 */
	private boolean handleNextMessage() {
		if (!outgoingMessages.isEmpty()) {
			SelfSerializable message = outgoingMessages.remove();
			try {
				serializeMessage(message);
			} catch (IOException e) {
				throw new MerkleSynchronizationException(e);
			}

			bufferedMessageCount += 1;
			return true;
		}
		return false;
	}

	protected void serializeMessage(SelfSerializable message) throws IOException {
		message.serialize(outputStream);
	}

	private boolean flush() {
		timeSinceLastFlush.reset();
		timeSinceLastFlush.start();
		if (bufferedMessageCount > 0) {
			try {
				outputStream.flush();
			} catch (IOException e) {
				throw new MerkleSynchronizationException(e);
			}
			bufferedMessageCount = 0;
			return true;
		}
		return false;
	}

	/**
	 * Flush the stream if necessary.
	 */
	private void flushIfRequired() {
		if (timeSinceLastFlush.getTime(TimeUnit.MILLISECONDS) > flushIntervalMs) {
			flush();
		}
	}

	private void start(StandardWorkGroup workGroup) {
		if (!ReconnectSettingsFactory.get().isAsyncStreams()) {
			return;
		}
		workGroup.execute("async-output-stream", this::run);
	}

	private void asyncWrite(SelfSerializable message) throws InterruptedException {
		outgoingMessages.put(message);
	}

	private void syncWrite(SelfSerializable message) {
		try {
			outputStream.writeSerializable(message, false);
			outputStream.flush();
		} catch (IOException e) {
			Thread.currentThread().interrupt();
		}
	}
}
