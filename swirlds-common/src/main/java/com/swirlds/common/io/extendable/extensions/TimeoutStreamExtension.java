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

package com.swirlds.common.io.extendable.extensions;

import com.swirlds.common.io.extendable.InputStreamExtension;
import com.swirlds.common.io.extendable.OutputStreamExtension;
import com.swirlds.common.io.extendable.extensions.internal.StreamTimeoutManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.x509.Time;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.swirlds.common.utility.CompareTo.isGreaterThan;
import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * <p>
 * This extension will trigger an exception on a timeout if a single operation takes too long on a stream.
 * If a timeout is triggered then the stream is closed.
 * </p>
 *
 * <p>
 * This extension will not trigger an exception if no data is passing through the stream, and no data has
 * been requested to pass through the stream.
 * </p>
 *
 * <p>
 * Timing is approximate in the positive direction. That is, things make take longer than the configured duration
 * to time out, but will never time out in less than the configured duration.
 * </p>
 */
public class TimeoutStreamExtension implements InputStreamExtension, OutputStreamExtension {

	private static final Logger LOG = LogManager.getLogger(TimeoutStreamExtension.class);

	private final Duration timeoutPeriod;

	private final AtomicLong operationStartNumber = new AtomicLong();
	private final AtomicLong operationFinishNumber = new AtomicLong();
	private long lastTimeoutCheck = -1;
	private Instant watchStart;

	private InputStream inputStream;
	private OutputStream outputStream;

	private final AtomicBoolean closed = new AtomicBoolean(false);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void init(final InputStream baseStream) {
		inputStream = baseStream;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void init(final OutputStream baseStream) {
		outputStream = baseStream;
	}

	/**
	 * Construct and register a timeout stream extension. This is handled by a static method due
	 * to registration requirements with the timeout stream manager.
	 *
	 * @param timeoutPeriod
	 * 		the time required
	 * @return a new timeout extension
	 */
	public static TimeoutStreamExtension buildTimeoutStreamExtension(final Duration timeoutPeriod) {
		final TimeoutStreamExtension extension = new TimeoutStreamExtension(timeoutPeriod);
		StreamTimeoutManager.register(extension);
		return extension;
	}

	/**
	 * Create a new timeout stream extension.
	 *
	 * @param timeoutPeriod
	 * 		the maximum period of time permitted for a single operation
	 */
	private TimeoutStreamExtension(final Duration timeoutPeriod) {
		this.timeoutPeriod = timeoutPeriod;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		closed.getAndSet(true);
	}

	/**
	 * Called periodically to check if the timeout has expired. Closes the stream once the timeout has occurred.
	 *
	 * @return true if the stream is still open and valid, false if the stream has been closed
	 */
	public boolean checkTimeout() {
		if (closed.get()) {
			return false;
		}

		final long finishNumber = operationFinishNumber.get();
		final long startNumber = operationStartNumber.get();

		if (lastTimeoutCheck == startNumber && finishNumber < startNumber) {
			// Since the last time the timeout was checked, no new operation has started, and
			// the finish number indicates that the started operation was never completed.

			if (watchStart == null) {
				// We not yet started a watch on this operation.

				watchStart = Instant.now();
			} else {
				// We have already started a watch on this operation. Check if the watch
				// has been open for longer than the timeout duration.

				final Duration elapsedTime = Duration.between(watchStart, Instant.now());
				if (isGreaterThan(elapsedTime, timeoutPeriod)) {
					triggerTimeout();
				}
			}
		} else {
			lastTimeoutCheck = operationStartNumber.get();
			watchStart = null;
		}

		return true;
	}

	/**
	 * Called on a stream after it has timed out.
	 */
	private void triggerTimeout() {
		LOG.error(EXCEPTION.getMarker(), "operation timed out on stream");
		close();
		try {
			if (inputStream != null) {
				inputStream.close();
			}
			if (outputStream != null) {
				outputStream.close();
			}
		} catch (final IOException e) {
			LOG.error(EXCEPTION.getMarker(), "exception while attempting to close timed out stream", e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int read() throws IOException {
		final long op = operationStartNumber.incrementAndGet();
		final int aByte = inputStream.read();
		operationFinishNumber.set(op);
		return aByte;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int read(final byte[] bytes, final int offset, final int length) throws IOException {
		final long op = operationStartNumber.incrementAndGet();
		final int count = inputStream.read(bytes, offset, length);
		operationFinishNumber.set(op);
		return count;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] readNBytes(final int length) throws IOException {
		final long op = operationStartNumber.incrementAndGet();
		final byte[] data = inputStream.readNBytes(length);
		operationFinishNumber.set(op);
		return data;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int readNBytes(final byte[] bytes, final int offset, final int length) throws IOException {
		final long op = operationStartNumber.incrementAndGet();
		final int count = inputStream.readNBytes(bytes, offset, length);
		operationFinishNumber.set(op);
		return count;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(final int b) throws IOException {
		final long op = operationStartNumber.incrementAndGet();
		outputStream.write(b);
		operationFinishNumber.set(op);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(final byte[] bytes, final int offset, final int length) throws IOException {
		final long op = operationStartNumber.incrementAndGet();
		outputStream.write(bytes, offset, length);
		operationFinishNumber.set(op);
	}
}
