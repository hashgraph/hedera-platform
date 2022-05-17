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

package com.swirlds.common.io.extendable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * An {@link OutputStream} where data passes through it and provides methods to do extra work with that data.
 */
public class ExtendableOutputStream extends OutputStream {
	private final OutputStream stream;
	private final OutputStreamExtension extension;

	/**
	 * Extend an output stream.
	 *
	 * @param stream
	 * 		a stream to extend
	 * @param extensions
	 * 		zero or more extensions
	 * @return an extended stream
	 */
	public static OutputStream extendOutputStream(
			final OutputStream stream,
			final OutputStreamExtension... extensions) {

		if (extensions == null) {
			return stream;
		}
		OutputStream s = stream;
		for (final OutputStreamExtension extension : extensions) {
			s = new ExtendableOutputStream(s, extension);
		}
		return s;
	}

	/**
	 * Create a new output stream.
	 *
	 * @param stream
	 * 		the stream to wrap
	 * @param extension
	 * 		an extension
	 */
	public ExtendableOutputStream(final OutputStream stream, final OutputStreamExtension extension) {
		this.stream = Objects.requireNonNull(stream, "stream must not be null");
		this.extension = Objects.requireNonNull(extension, "extension must not be null");
		extension.init(stream);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(final int b) throws IOException {
		extension.write(b);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(final byte[] bytes, final int offset, final int length) throws IOException {
		extension.write(bytes, offset, length);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void flush() throws IOException {
		stream.flush();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() throws IOException {
		try {
			stream.close();
		} finally {
			extension.close();
		}
	}
}
