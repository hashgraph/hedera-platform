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

package com.swirlds.jasperdb.collections;

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
			fileChannel.write(buf, offset);
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
			fileChannel.read(buf, offset);
			final long filesOldValue = buf.getLong(0);
			if (filesOldValue == oldValue) {
				// write new value to file
				buf.putLong(0, newValue);
				buf.position(0);
				fileChannel.write(buf, offset);
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
				fc.transferFrom(fileChannel, 0, fileChannel.size());
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
			fileChannel.read(buf, offset);
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
		fileChannel.force(false);
		// now close
		fileChannel.close();
	}
}
