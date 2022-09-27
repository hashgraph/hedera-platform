/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.jasperdb.collections;

import com.swirlds.jasperdb.utilities.JasperDBFileUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A direct on disk implementation of LongList
 */
public class LongListDisk extends LongList implements Closeable {
	/** A temp byte buffer for reading and writing longs */
	private static final ThreadLocal<ByteBuffer> TEMP_LONG_BUFFER_THREAD_LOCAL = ThreadLocal.withInitial(() ->
			ByteBuffer.allocateDirect(Long.BYTES).order(ByteOrder.nativeOrder()));
	/** The disk file this LongList is based on */
	private final Path file;

	/**
	 * Create a {@link LongListDisk} on a file, if the file doesn't exist it will be created.
	 *
	 * @param file
	 * 		The file to read and write to
	 * @throws IOException
	 * 		If there was a problem reading the file
	 */
	public LongListDisk(Path file) throws IOException {
		super(FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE));
		this.file = file;
	}

	/**
	 * Stores a long at the given index.
	 *
	 * @param index
	 * 		the index to use
	 * @param value
	 * 		the long to store
	 * @throws IndexOutOfBoundsException
	 * 		if the index is negative or beyond the max capacity of the list
	 * @throws IllegalArgumentException
	 * 		if the value is zero
	 */
	@Override
	public synchronized void put(long index, long value) {
		checkValueAndIndex(value, index);
		try {
			final ByteBuffer buf = TEMP_LONG_BUFFER_THREAD_LOCAL.get();
			long offset = FILE_HEADER_SIZE + (index * Long.BYTES);
			// write new value to file
			buf.putLong(0, value);
			buf.position(0);
			JasperDBFileUtils.completelyWrite(fileChannel, buf, offset);
			// update size
			size.getAndUpdate(oldSize -> index >= oldSize ? (index + 1) : oldSize);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Stores a long at the given index, on the condition that the current long therein has a given value.
	 *
	 * @param index
	 * 		the index to use
	 * @param oldValue
	 * 		the value that must currently obtain at the index
	 * @param newValue
	 * 		the new value to store
	 * @return whether the newValue was set
	 * @throws IndexOutOfBoundsException
	 * 		if the index is negative or beyond the max capacity of the list
	 * @throws IllegalArgumentException
	 * 		if old value is zero (which could never be true)
	 */
	@Override
	public synchronized boolean putIfEqual(long index, long oldValue, long newValue) {
		checkValueAndIndex(newValue, index);
		try {
			final ByteBuffer buf = TEMP_LONG_BUFFER_THREAD_LOCAL.get();
			long offset = FILE_HEADER_SIZE + (index * Long.BYTES);
			// first read old value
			buf.clear();
			JasperDBFileUtils.completelyRead(fileChannel, buf, offset);
			final long filesOldValue = buf.getLong(0);
			if (filesOldValue == oldValue) {
				// write new value to file
				buf.putLong(0, newValue);
				buf.position(0);
				JasperDBFileUtils.completelyWrite(fileChannel, buf, offset);
				// update size
				size.getAndUpdate(oldSize -> index >= oldSize ? (index + 1) : oldSize);
				return true;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return false;
	}

	/**
	 * Write all longs in this LongList into a file
	 * <p><b>
	 * It is not guaranteed what version of data will be written if the LongList is changed via put methods while
	 * this LongList is being written to a file. If you need consistency while calling put concurrently then use a
	 * BufferedLongListWrapper.
	 * </b></p>
	 *
	 * @param newFile
	 * 		The file to write into, it should not exist but its parent directory should exist and be writable.
	 * @throws IOException
	 * 		If there was a problem creating or writing to the file.
	 */
	@Override
	public void writeToFile(Path newFile) throws IOException {
		// finish writing to current file
		fileChannel.force(true);
		// if new file is provided then copy to it
		if (!file.equals(newFile)) {
			try (final FileChannel fc = FileChannel.open(newFile, StandardOpenOption.CREATE,
					StandardOpenOption.WRITE)) {
				fileChannel.position(0);
				JasperDBFileUtils.completelyTransferFrom(fc, fileChannel, 0, fileChannel.size());
			}
		}
	}

	/**
	 * No-op as we override writeToFile directly
	 */
	@Override
	protected void writeLongsData(FileChannel fc) {
	}

	/**
	 * Lookup a long in data
	 *
	 * @param chunkIndex
	 * 		the index of the chunk the long is contained in
	 * @param subIndex
	 * 		The sub index of the long in that chunk
	 * @return The stored long value at given index
	 */
	@Override
	protected long lookupInChunk(long chunkIndex, long subIndex) {
		try {
			final ByteBuffer buf = TEMP_LONG_BUFFER_THREAD_LOCAL.get();
			long offset = FILE_HEADER_SIZE + (((chunkIndex * numLongsPerChunk) + subIndex) * Long.BYTES);
			buf.clear();
			JasperDBFileUtils.completelyRead(fileChannel, buf, offset);
			return buf.getLong(0);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Closes the open file
	 *
	 * @throws IOException
	 * 		if an I/O error occurs
	 */
	@Override
	public void close() throws IOException {
        // flush
        if (fileChannel.isOpen()) {
            fileChannel.force(false);
		}
		// now close
		fileChannel.close();
	}
}
