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
import java.io.InputStream;

/**
 * An {@link InputStream} where data passes through it and provides methods to do extra work with that data
 */
public class ExtendableInputStream<T extends StreamExtension> extends InputStream {
	private final InputStream stream;
	private final T extension;


	public ExtendableInputStream(InputStream stream, T extension) {
		CommonUtils.throwArgNull(stream, "stream");
		CommonUtils.throwArgNull(extension, "extension");
		this.stream = stream;
		this.extension = extension;
	}

	public T getExtension() {
		return extension;
	}

	@Override
	public int read() throws IOException {
		int aByte = stream.read();
		extension.newByte(aByte);
		return aByte;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int read = stream.read(b, off, len);
		extension.newBytes(b, off, read);
		return read;
	}

	@Override
	public byte[] readNBytes(int len) throws IOException {
		byte[] bytes = stream.readNBytes(len);
		extension.newBytes(bytes, 0, bytes.length);
		return bytes;
	}

	@Override
	public int readNBytes(byte[] b, int off, int len) throws IOException {
		int read = stream.readNBytes(b, off, len);
		extension.newBytes(b, off, read);
		return read;
	}

	@Override
	public long skip(long n) throws IOException {
		return stream.skip(n);
	}

	@Override
	public void skipNBytes(long n) throws IOException {
		stream.skipNBytes(n);
	}

	@Override
	public int available() throws IOException {
		return stream.available();
	}

	@Override
	public void close() throws IOException {
		stream.close();
	}

	@Override
	public synchronized void mark(int readlimit) {
		stream.mark(readlimit);
	}

	@Override
	public boolean markSupported() {
		return stream.markSupported();
	}

	@Override
	public synchronized void reset() throws IOException {
		stream.reset();
	}
}
