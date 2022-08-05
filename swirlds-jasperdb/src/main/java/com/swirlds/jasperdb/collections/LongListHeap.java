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

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLongArray;

import static java.nio.ByteBuffer.allocateDirect;

/**
 * A {@link LongList} that stores its contents on-heap via a {@link CopyOnWriteArrayList} of {@link AtomicLongArray}s.
 * Each {@link AtomicLongArray} is the same size, so the "chunk" containing the value for any given index is easily
 * found using modular arithmetic.
 *
 * It is important to note that if indexes are not used sequentially from zero, many (or most) of the chunks in the list
 * may consume RAM without storing any longs. So this data structure is only appropriate for use cases where list
 * indices are filled in roughly ascending order, starting from zero.
 *
 * Per the {@link LongList} contract, this class is thread-safe for both concurrent reads and writes.
 *
 * Some others have tried similar but different ideas (see for example
 * https://philosopherdeveloper.com/posts/how-to-build-a-thread-safe-lock-free-resizable-array.html).
 */
@SuppressWarnings("unused")
public final class LongListHeap extends LongList implements Closeable {

	/**
	 * A copy-on-write list of data arrays. Expands as needed.
	 */
	private final CopyOnWriteArrayList<AtomicLongArray> data = new CopyOnWriteArrayList<>();

	/**
	 * Construct a new LongListHeap with the default number of longs per chunk.
	 */
	public LongListHeap() {
		this(DEFAULT_NUM_LONGS_PER_CHUNK);
	}

	/**
	 * Construct a new LongListHeap with the specified number of longs per chunk and default max longs to store.
	 */
	public LongListHeap(final int numLongsPerChunk) {
		super(numLongsPerChunk, DEFAULT_MAX_LONGS_TO_STORE);
	}

	/**
	 * Construct a new LongListHeap with the specified number of longs per chunk and maximum number of longs.
	 *
	 * @param numLongsPerChunk
	 * 		number of longs to store in each chunk of memory allocated
	 * @param maxLongs
	 * 		the maximum number of longs permissible for this LongList
	 */
	public LongListHeap(final int numLongsPerChunk, final long maxLongs) {
		super(numLongsPerChunk, maxLongs);
	}

	/**
	 * Create a {@link LongListHeap} from a file that was saved.
	 *
	 * @throws IOException
	 * 		If there was a problem reading the file
	 */
	public LongListHeap(final Path file) throws IOException {
		super(FileChannel.open(file, StandardOpenOption.READ));
		// read data
		int numOfArrays = (int) Math.ceil((double) size() / (double) numLongsPerChunk);
		ByteBuffer buffer = allocateDirect(memoryChunkSize);
		buffer.order(ByteOrder.nativeOrder());
		for (int i = 0; i < numOfArrays; i++) {
			final AtomicLongArray atomicLongArray = new AtomicLongArray(numLongsPerChunk);
			buffer.clear();
			fileChannel.read(buffer);
			buffer.flip();
			int index = 0;
			while (buffer.remaining() > 0) {
				atomicLongArray.set(index, buffer.getLong());
				index++;
			}
			data.add(atomicLongArray);
		}
		// close file channel as we are done with it
		fileChannel.close();
		fileChannel = null;
	}

	/**
	 * Close and free all resources
	 */
	@Override
	public void close() {
		maxIndexThatCanBeStored.set(0);
		size.set(0);
		data.clear();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void put(final long index, final long value) {
		checkValueAndIndex(value, index);
		expandIfNeeded(index);
		final int dataIndex = (int) (index / numLongsPerChunk);
		final int subIndex = (int) (index % numLongsPerChunk);
		data.get(dataIndex).set(subIndex, value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean putIfEqual(final long index, final long oldValue, final long newValue) {
		checkValueAndIndex(newValue, index);
		expandIfNeeded(index);
		final int dataIndex = (int) (index / numLongsPerChunk);
		final AtomicLongArray chunk = data.get(dataIndex);
		final int subIndex = (int) (index % numLongsPerChunk);
		return chunk.compareAndSet(subIndex, oldValue, newValue);
	}

	// =================================================================================================================
	// Protected methods

	/**
	 * Write the long data to file, This it is expected to be in one simple block of raw longs.
	 *
	 * @param fc
	 * 		The file channel to write to
	 * @throws IOException
	 * 		if there was a problem writing longs
	 */
	@Override
	protected void writeLongsData(final FileChannel fc) throws IOException {
		// write data
		final ByteBuffer tempBuffer = allocateDirect(1024 * 1024);
		tempBuffer.order(ByteOrder.nativeOrder());
		final LongBuffer tempLongBuffer = tempBuffer.asLongBuffer();
		for (long i = 0; i < size(); i++) {
			// if buffer is full then write
			if (!tempLongBuffer.hasRemaining()) {
				tempBuffer.clear();
				fc.write(tempBuffer);
				tempLongBuffer.clear();
			}
			// add value to buffer
			tempLongBuffer.put(get(i, 0));
		}
		// write any remaining
		if (tempLongBuffer.position() > 0) {
			tempBuffer.position(0);
			tempBuffer.limit(tempLongBuffer.position() * Long.BYTES);
			fc.write(tempBuffer);
		}
	}

	// =================================================================================================================
	// Private helper methods

	/**
	 * Expand the available data storage if needed to allow storage of an item at newIndex
	 *
	 * @param newIndex
	 * 		the index of the new item we would like to add to storage
	 */
	private void expandIfNeeded(final long newIndex) {
		// This is important to be lock free which means two or more threads can be inside the loop at a time. This
		// means two threads can be making the buffer bigger at the same time. The most that can happen in this case is
		// the buffer is bigger than needed. Because the index for inserting the value is fixed, there is no contention
		// over the index only over the size of the buffer.
		while (newIndex > maxIndexThatCanBeStored.get()) {
			// need to expand
			data.add(new AtomicLongArray(numLongsPerChunk));
			maxIndexThatCanBeStored.addAndGet(numLongsPerChunk);
		}

		// updates the index to the max of new index and its current value. If two threads are trying to do this at the
		// same time they will both keep trying till each one gets a clean chance of setting the value. The largest will
		// always win, which is what matters.
		size.getAndUpdate(oldSize -> newIndex >= oldSize ? (newIndex + 1) : oldSize);
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
	protected long lookupInChunk(final long chunkIndex, final long subIndex) {
		return data.get((int) chunkIndex).get((int) subIndex);
	}
}
