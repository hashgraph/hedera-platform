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

package com.swirlds.common.throttle;

import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * This class wraps an {@link OutputStream} to throttle the bytes per second sent.
 * Internally uses the {@link Throttle} class to treat each byte as a transaction.
 * <p>
 * Notice that if we want to send 100 bytes, and we throttle at 50 bytes per second
 * we will send the first 50 bytes in 1 second, and the next 50 bytes will be send in
 * 50 milliseconds, so the transfer is going to be measured as it took just above 1
 * second. This example is only to create awareness that the lower bound for data
 * transfer is given by {@code n / r - 1}, where {@code n = } amount of bytes to
 * transfer, {@code r = } rate of bytes per second.
 * </p>
 */
public class ThrottledOutputStream extends SerializableDataOutputStream {

	private static final long DEFAULT_WAIT_TIME_IN_MS = 200;

	private final Throttle throttle;

	/**
	 * Creates a new data output stream to write data to the specified
	 * underlying output stream.
	 *
	 * @param out
	 * 		the underlying output stream, to be saved for later
	 * 		use.
	 * @see FilterOutputStream#out
	 */
	public ThrottledOutputStream(final OutputStream out, final ThrottledOutputStreamSettings settings) {
		super(out);
		this.throttle = new Throttle(settings.getBytesPerSecond());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void write(final int arg) throws IOException {
		throttle(1);
		super.write(arg);
	}

	/**
	 * {@inheritDoc}
	 *
	 * Throttling is based on the rate of bytes per second set, not the remaining data
	 * to be sent, i.e., assuming we want to send two blocks of 60 bytes, and we throttle
	 * at 50 bytes per second, the data will be sent as follows:
	 * <ol>
	 *     <li>We send 50 bytes</li>
	 *     <li>We throttle until 1 second has passed</li>
	 *     <li>We send the remaining 10 bytes</li>
	 *     <li>We throttle until 1 second has passed</li>
	 *     <li>We send the next 50 bytes </li>
	 *     <li>We throttle until 1 second has passed</li>
	 *     <li>We send the remaining 10 bytes</li>
	 * </ol>
	 *
	 * Notice that other implementations might have a different behavior which considers
	 * the available data to be sent instead of a fix block. This consideration must be
	 * taken into account given that this version might throttle the data a bit more
	 * than expected.
	 */
	@Override
	public synchronized void write(final byte[] b, final int off, final int len) throws IOException {
		if (len <= this.throttle.getTps()) {
			throttle(len);
			super.write(b, off, len);
			return;
		}

		int bytesWritten = 0;
		int offset = off;
		final int length = (int) this.throttle.getTps();
		while (bytesWritten < len) {
			throttle(length);
			final int bytesToWrite = bytesWritten + length < len ? length : (len - bytesWritten);
			super.write(b, offset, bytesToWrite);
			offset += bytesToWrite;
			bytesWritten += bytesToWrite;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(final byte[] b) throws IOException {
		this.write(b, 0, b.length);
	}

	private void throttle(final int numberOfBytes) {
		try {
			while (!this.throttle.allow(numberOfBytes)) {
				TimeUnit.MILLISECONDS.sleep(DEFAULT_WAIT_TIME_IN_MS);
			}
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
