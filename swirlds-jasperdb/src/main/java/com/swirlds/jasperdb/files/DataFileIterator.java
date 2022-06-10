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

package com.swirlds.jasperdb.files;

import com.swirlds.jasperdb.settings.JasperDbSettings;
import com.swirlds.jasperdb.settings.JasperDbSettingsFactory;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;

/**
 * Iterator class for iterating over data items in a DataFile. It is designed to be used in a while(iter.next()){...}
 * loop and you can then read the data items info for current item with getDataItemsKey, getDataItemsDataLocation and
 * getDataItemData.
 *
 * It is designed to be used from a single thread.
 *
 * @see DataFileWriter for definition of file structure
 */
@SuppressWarnings("rawtypes")
public final class DataFileIterator implements AutoCloseable {
	/**
	 * Since {@code com.swirlds.platform.Browser} populates settings, and it is loaded before
	 * any application classes that might instantiate a data source, the {@link JasperDbSettingsFactory}
	 * holder will have been configured by the time this static initializer runs.
	 */
	private static final JasperDbSettings settings = JasperDbSettingsFactory.get();

	/** Input stream this iterator is reading from */
	private final BufferedInputStream inputStream;
	/** The file metadata read from the end of file */
	private final DataFileMetadata metadata;
	/** The path to the file we are iterating over */
	private final Path path;
	/** The serializer used for reading data from the file */
	private final DataItemSerializer dataItemSerializer;
	/** Taken from the dataItemSerializer, this is the size of the header of each data item */
	private final int headerSize;

	/** Buffer that is reused for reading each data item */
	private ByteBuffer dataItemBuffer;
	/** Header for the current data item */
	private DataItemHeader currentDataItemHeader;
	/** Index of current data item this iterator is reading, zero being the first item, -1 being before start */
	private long currentDataItem = -1;
	/** The offset in bytes from start of file to the beginning of the current item. */
	private long currentDataItemFilePosition = 0;
	/** The current read position in the file in bytes from the beginning of the file. */
	private long currentFilePosition = 0;
	/** The size of the data most recently read */
	private int dataItemSize = 0;
	/** True if this iterator has been closed */
	private boolean closed = false;

	/**
	 * Create a new DataFileIterator on an existing file.
	 *
	 * @param path
	 * 		The path to the file to read.
	 * @param metadata
	 * 		The metadata read from the file.
	 * @throws IOException
	 * 		if there was a problem creating a new InputStream on the file at path
	 */
	public DataFileIterator(
			final Path path,
			final DataFileMetadata metadata,
			final DataItemSerializer dataItemSerializer
	) throws IOException {
		this.path = path;
		this.metadata = metadata;
		this.dataItemSerializer = dataItemSerializer;
		this.headerSize = dataItemSerializer.getHeaderSize();
		/* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3929 */
		this.inputStream = new BufferedInputStream(
						Files.newInputStream(path, StandardOpenOption.READ),
						settings.getIteratorInputBufferBytes());
	}

	/**
	 * Get the metadata for the file we are iterating over.
	 *
	 * @return File's metadata
	 */
	public DataFileMetadata getMetadata() {
		return metadata;
	}

	/**
	 * Close the file reader.
	 *
	 * @throws IOException
	 * 		– if this resource cannot be closed
	 */
	@Override
	public void close() throws IOException {
		if (!closed) {
			closed = true;
			dataItemBuffer = null;
			inputStream.close();
		}
	}

	/**
	 * Advance to the next dataItem.
	 *
	 * @return true if a dataItem was read or false if the end of the file has been reached.
	 * @throws IOException
	 * 		If there was a problem reading from file.
	 */
	public boolean next() throws IOException {
		if (closed) {
			throw new IllegalStateException("Cannot read from a closed iterator");
		}

		// Have we reached the end?
		if (currentDataItem >= metadata.getDataItemCount() - 1) {
			dataItemBuffer = null;
			return false;
		}

		// Move the current byte position forward past the last item.
		// Note: initially dataItemSize is zero, so calling next() for the first time
		// does not advance the pointer, but on subsequent calls to next(), it will.
		currentDataItemFilePosition += dataItemSize;

		// Read data item header to determine the variable length size of the data
		final ByteBuffer dataBuffer = fillBuffer(headerSize);
		currentDataItemHeader = dataItemSerializer.deserializeHeader(dataBuffer);
		dataItemSize = currentDataItemHeader.getSizeBytes();

		currentDataItem++;
		return true;
	}

	/**
	 * Get the current dataItems data. This is a shared buffer and must NOT be leaked from
	 * the call site or modified directly.
	 *
	 * @return ByteBuffer containing the key and value data. This will return null if the iterator has
	 * 		been closed, or if the iterator is in the before-first or after-last states.
	 */
	public ByteBuffer getDataItemData() throws IOException {
		return fillBuffer(dataItemSize);
	}

	/**
	 * Get the data location for current dataItem.
	 *
	 * @return file index and dataItem index combined location
	 */
	public long getDataItemsDataLocation() {
		return DataFileCommon.dataLocation(metadata.getIndex(), currentDataItemFilePosition);
	}

	/**
	 * Get current dataItems key.
	 *
	 * @return the key for current dataItem
	 */
	public long getDataItemsKey() {
		return currentDataItemHeader.getKey();
	}

	/**
	 * Get the creation time and data for the data file we are iterating over
	 *
	 * @return data file creation date
	 */
	public Instant getDataFileCreationDate() {
		return metadata.getCreationDate();
	}

	/**
	 * Get the index of the data file we are iterating over
	 *
	 * @return data file index
	 */
	public int getDataFileIndex() {
		return metadata.getIndex();
	}

	/** toString for debugging */
	@Override
	public String toString() {
		return "DataFileIterator{" +
				"fileIndex=" + metadata.getIndex() +
				", currentDataItemHeader=" + currentDataItemHeader +
				", currentDataItemIndex=" + currentDataItem +
				", currentDataItemByteOffset=" + currentDataItemFilePosition +
				", fileName=" + path.getFileName() +
				", metadata=" + metadata +
				'}';
	}

	/**
	 * Equals for use when comparing in collections, based on matching file paths and metadata
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final DataFileIterator that = (DataFileIterator) o;
		return path.equals(that.path) && metadata.equals(that.metadata);
	}

	/**
	 * hashCode for use when comparing in collections, based on file path and metadata
	 */
	@Override
	public int hashCode() {
		return Objects.hash(path, metadata);
	}

	// =================================================================================================================
	// Private methods

	/**
	 * Reads bytesToRead bytes from the current data item
	 * @param bytesToRead bytes to read
	 * @return ByteBuffer containing requested bytes
	 * @throws IOException if request can not be completed
	 */
	private ByteBuffer fillBuffer(int bytesToRead) throws IOException {
		if (bytesToRead <= 0) {
			throw new IOException("Malformed file [" + path + "], data item [" + currentDataItem +
					"], requested bytes [" + bytesToRead + "]");
		}

		// Create or resize the buffer if necessary
		if (dataItemBuffer == null || dataItemBuffer.capacity() < bytesToRead) {
			resizeBuffer(bytesToRead);
		}

		// This only happens if we have advanced currentDataItemFilePosition and need to strip off some bytes
		// from the buffered input stream to catch up.
		if (currentFilePosition < currentDataItemFilePosition) {
			inputStream.skipNBytes(currentDataItemFilePosition - currentFilePosition);
			currentFilePosition = currentDataItemFilePosition;
		}

		// Read from the input stream into the byte buffer
		final int offset = (int) (currentFilePosition - currentDataItemFilePosition);
		final int bytesRead = inputStream.read(dataItemBuffer.array(), offset, bytesToRead - offset);
		if (offset + bytesRead != bytesToRead) {
			throw new EOFException("Was trying to read a data item [" +
					currentDataItem + "] but ran out of data in the file [" + path + "].");
		}

		currentFilePosition += bytesRead;
		dataItemBuffer.position(0);
		dataItemBuffer.limit(bytesToRead);
		return dataItemBuffer;
	}

	/**
	 * Resizes the dataItemBuffer, or creates it if necessary, such that it is large enough
	 * to read the bytes provided.
	 *
	 * @param bytesToRead
	 * 		Number of bytes to be able to fit into the buffer.
	 */
	private void resizeBuffer(int bytesToRead) {
		final ByteBuffer newBuffer = ByteBuffer.allocate(bytesToRead);
		// Copy existing content from the existing buffer IF data has already been read
		final int offset = (int) (currentFilePosition - currentDataItemFilePosition);
		if (offset > 0) {
			// dataItemBuffer cannot be null here because the only way for offset to be > 0 is if
			// we have already read off some data.
			System.arraycopy(dataItemBuffer.array(), 0, newBuffer.array(), 0, offset);
		}
		dataItemBuffer = newBuffer;
	}
}
