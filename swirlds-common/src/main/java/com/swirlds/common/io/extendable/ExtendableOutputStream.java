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

package com.swirlds.common.io.extendable;

import com.swirlds.common.CommonUtils;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An {@link OutputStream} where data passes through it and provides methods to do extra work with that data
 */
public class ExtendableOutputStream<T extends StreamExtension> extends OutputStream {
	private final OutputStream stream;
	private final T extension;

	public ExtendableOutputStream(T extension) {
		CommonUtils.throwArgNull(extension, "extension");
		stream = OutputStream.nullOutputStream();
		this.extension = extension;
	}

	public ExtendableOutputStream(OutputStream stream, T extension) {
		CommonUtils.throwArgNull(stream, "stream");
		CommonUtils.throwArgNull(extension, "extension");
		this.stream = stream;
		this.extension = extension;
	}

	public T getExtension() {
		return extension;
	}

	@Override
	public void write(int b) throws IOException {
		stream.write(b);
		extension.newByte(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		stream.write(b, off, len);
		extension.newBytes(b, off, len);
	}

	@Override
	public void flush() throws IOException {
		stream.flush();
	}

	@Override
	public void close() throws IOException {
		stream.close();
	}
}
