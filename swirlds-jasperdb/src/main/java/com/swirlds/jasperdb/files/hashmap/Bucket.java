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

package com.swirlds.jasperdb.files.hashmap;

import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.utility.Units;
import com.swirlds.jasperdb.files.DataFileOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static com.swirlds.jasperdb.files.hashmap.HalfDiskHashMap.KEY_HASHCODE_SIZE;
import static com.swirlds.jasperdb.files.hashmap.HalfDiskHashMap.SPECIAL_DELETE_ME_VALUE;
import static com.swirlds.jasperdb.files.hashmap.HalfDiskHashMap.VALUE_SIZE;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.JASPER_DB;


/**
 * Class for accessing the data in a bucket. This is designed to be used from a single thread.
 * <p>
 * Each bucket has a header containing:
 * <ul>
 *     <li><b>int</b> - Bucket index in map hash index</li>
 *     <li><b>int</b> - Bucket size, total number of bytes taken by bucket including header</li>
 *     <li><b>int</b> - Number of entries in this bucket</li>
 *     <li><b>Entry[]</b>  - array of entries</li>
 * </ul>
 * <p>
 * Each Entry contains:
 * <ul>
 *     <li><b>KEY_HASHCODE_SIZE(int/long)</b>  - key hash code</li>
 *     <li><b>value</b>  - the value of the key/value pair. It is here because it is fixed size</li>
 *     <li><b>key data</b>  - can be fixed size of entryKeySize or variable size</li>
 * </ul>
 */
@SuppressWarnings("unused")
public final class Bucket<K extends VirtualKey<? super K>> {
	private static final Logger LOG = LogManager.getLogger(Bucket.class);

	/**
	 * When increasing the capacity of a bucket, increase it by this many bytes.
	 */
	private static final int CAPACITY_INCREMENT = 1024;
	/** We assume 8KB will be enough for now for most buckets. */
	private static final int DEFAULT_BUCKET_BUFFER_SIZE = 8 * Units.KIBIBYTES_TO_BYTES;
	private static final int BUCKET_INDEX_SIZE = Integer.BYTES;
	private static final int BUCKET_SIZE_OFFSET = BUCKET_INDEX_SIZE;
	private static final int BUCKET_SIZE_SIZE = Integer.BYTES;
	private static final int BUCKET_ENTRY_COUNT_OFFSET = BUCKET_SIZE_OFFSET + BUCKET_SIZE_SIZE;
	private static final int BUCKET_ENTRY_COUNT_SIZE = Integer.BYTES;
	/** The amount of data used for a header in each bucket */
	private static final int BUCKET_HEADER_SIZE = BUCKET_ENTRY_COUNT_OFFSET + BUCKET_ENTRY_COUNT_SIZE;

	private static final int ENTRY_VALUE_OFFSET = KEY_HASHCODE_SIZE;
	/** Keep track of the largest bucket we have ever created for logging */
	private static final AtomicInteger LARGEST_SIZE_OF_BUCKET_CREATED = new AtomicInteger(0);

	private int keySerializationVersion;
	private ByteBuffer bucketBuffer;
	private KeySerializer<K> keySerializer;
	private final DataFileOutputStream reusableDataFileOutputStream;

	/**
	 * Create a new bucket with the default size.
	 *
	 * @param keySerializer
	 * 		The serializer responsible for converting keys to/from bytes
	 */
	Bucket(KeySerializer<K> keySerializer) {
		setKeySerializer(keySerializer);
		bucketBuffer = ByteBuffer.allocate(DEFAULT_BUCKET_BUFFER_SIZE);
		setSize(BUCKET_HEADER_SIZE);
		setBucketIndex(-1);
		reusableDataFileOutputStream = new DataFileOutputStream(keySerializer.getTypicalSerializedSize());
	}

	/**
	 * Reset for next use
	 *
	 * @return this bucket for each chaining
	 */
	public Bucket<K> clear() {
		// clear index
		setBucketIndex(-1);
		// set 0 for entry count
		setBucketEntryCount(0);
		// reset size
		setSize(BUCKET_HEADER_SIZE);
		// reset buffer
		bucketBuffer.position(0);
		bucketBuffer.limit(bucketBuffer.capacity());
		return this;
	}

	/**
	 * Change this bucket over to use new key serializer. It is a no-op if called with the same key serializer we are
	 * configured with already.
	 *
	 * @param keySerializer
	 * 		The new key serializer
	 */
	public void setKeySerializer(KeySerializer<K> keySerializer) {
		if (keySerializer != this.keySerializer) {
			// note, reusableDataFileOutputStream will grow as needed so no need to change its size
			this.keySerializer = keySerializer;
		}
	}

	/**
	 * Set the serialization version to use for keys
	 */
	public void setKeySerializationVersion(int keySerializationVersion) {
		this.keySerializationVersion = keySerializationVersion;
	}

	/**
	 * Get the index for this bucket
	 */
	public int getBucketIndex() {
		return bucketBuffer.getInt(0);
	}

	/**
	 * Set the index for this bucket
	 */
	public void setBucketIndex(int bucketIndex) {
		this.bucketBuffer.putInt(0, bucketIndex);
	}

	/**
	 * Get the number of entries stored in this bucket
	 */
	public int getBucketEntryCount() {
		return bucketBuffer.getInt(BUCKET_ENTRY_COUNT_OFFSET);
	}

	/**
	 * Set the number of entries stored in this bucket
	 */
	public void setBucketEntryCount(int count) {
		this.bucketBuffer.putInt(BUCKET_ENTRY_COUNT_OFFSET, count);
	}

	/**
	 * Add one to the number of entries stored in this bucket
	 */
	private void incrementBucketEntryCount() {
		this.bucketBuffer.putInt(BUCKET_ENTRY_COUNT_OFFSET, bucketBuffer.getInt(BUCKET_ENTRY_COUNT_OFFSET) + 1);
	}

	/**
	 * Subtract one to the number of entries stored in this bucket
	 */
	private void decrementBucketEntryCount() {
		this.bucketBuffer.putInt(BUCKET_ENTRY_COUNT_OFFSET, bucketBuffer.getInt(BUCKET_ENTRY_COUNT_OFFSET) - 1);
	}

	/**
	 * Get the size of this bucket in bytes, including header
	 */
	public int getSize() {
		return this.bucketBuffer.getInt(BUCKET_SIZE_OFFSET);
	}

	/**
	 * Set the size of this bucket in bytes, including header
	 */
	private void setSize(int size) {
		this.bucketBuffer.putInt(BUCKET_SIZE_OFFSET, size);
		final int maxSize = LARGEST_SIZE_OF_BUCKET_CREATED.get();
		if (size > maxSize) {
			final int newMaxSize = LARGEST_SIZE_OF_BUCKET_CREATED.updateAndGet(
					oldMaxSize -> Math.max(oldMaxSize, size));
			if (newMaxSize > BUCKET_HEADER_SIZE) {
				LOG.info(JASPER_DB.getMarker(), "New largest buckets, now = {} bytes, {} entries", newMaxSize,
						getBucketEntryCount() + 1);
			}
		}
	}

	/**
	 * Find a value for given key
	 *
	 * @param keyHashCode
	 * 		the int hash for the key
	 * @param key
	 * 		the key object
	 * @param notFoundValue
	 * 		the long to return if the key is not found
	 * @return the stored value for given key or notFoundValue if nothing is stored for the key
	 * @throws IOException
	 * 		If there was a problem reading the value from file
	 */
	public long findValue(
			final int keyHashCode,
			final K key,
			final long notFoundValue
	) throws IOException {
		final FindResult found = findEntryOffset(keyHashCode, key);
		if (found.found) {
			// yay! we found it
			return getValue(found.entryOffset);
		} else {
			return notFoundValue;
		}
	}

	/**
	 * Put a key/value entry into this bucket.
	 *
	 * @param key
	 * 		the entry key
	 * @param value
	 * 		the entry value, this can also be special HalfDiskHashMap.SPECIAL_DELETE_ME_VALUE to mean delete
	 */
	public void putValue(final int keyHashCode, final K key, final long value) {
		try {
			// scan over all existing key/value entries and see if there is already one for this key. If there is
			// then update it, otherwise we have at least worked out the entryOffset for the end of existing entries
			// and can use that for appending a new entry if there is room
			final FindResult result = findEntryOffset(keyHashCode, key);
			// handle DELETE
			if (value == SPECIAL_DELETE_ME_VALUE) {
				if (result.found) {
					final int entryCount = getBucketEntryCount();
					final int currentSize = getSize();
					// read the key size so we can calculate entry size
					final int entrySize = KEY_HASHCODE_SIZE + VALUE_SIZE + getKeySize(result.entryOffset);
					// check if not last entry
					if (result.entryIndex < (entryCount - 1)) {
						// move all entries after this one up
						final int offsetOfNextEntry = result.entryOffset + entrySize;
						final int sizeOfEntriesToMove = currentSize - offsetOfNextEntry;
						//  FUTURE WORK For Java 17 do this https://github.com/swirlds/swirlds-platform/issues/4090
						// bucketBuffer.put(result.entryOffset,bucketBuffer,offsetOfNextEntry,sizeOfEntriesToMove);
						final byte[] bucketBytes = bucketBuffer.array();
						System.arraycopy(bucketBytes, offsetOfNextEntry,
								bucketBytes, result.entryOffset, sizeOfEntriesToMove);
					}
					// decrement count
					decrementBucketEntryCount();
					// update size by removing entry size from size
					setSize(currentSize - entrySize);
					// we are done deleting
					return;
				} else {
					// was not found, and we are deleting so nothing to do
					return;
				}
			}
			// handle UPDATE
			if (result.found) {
				// yay! we found it, so update value
				setValue(result.entryOffset, value);
				return;
			}
			/* We have to serialize a variable-size key to a temp byte buffer to check
			   if there is going to be enough room to store it in this bucket. */
			if (keySerializer.isVariableSize()) {
				reusableDataFileOutputStream.reset();
				key.serialize(reusableDataFileOutputStream);
				reusableDataFileOutputStream.flush();
				final int keySizeBytes = reusableDataFileOutputStream.bytesWritten();
				final int newSize = result.entryOffset + KEY_HASHCODE_SIZE + VALUE_SIZE + keySizeBytes;
				ensureCapacity(newSize);
				setSize(newSize);
				// add a new entry
				bucketBuffer.position(result.entryOffset);
				bucketBuffer.putInt(keyHashCode);
				bucketBuffer.putLong(value);
				reusableDataFileOutputStream.writeTo(bucketBuffer);
				// increment count and update size
				incrementBucketEntryCount();
			} else {
				final int newSize = result.entryOffset + KEY_HASHCODE_SIZE + VALUE_SIZE +
						keySerializer.getSerializedSize();
				ensureCapacity(newSize);
				setSize(newSize);
				// add a new entry
				bucketBuffer.position(result.entryOffset);
				bucketBuffer.putInt(keyHashCode);
				bucketBuffer.putLong(value);
				key.serialize(bucketBuffer);
				// increment count
				incrementBucketEntryCount();
			}
		} catch (IOException e) {
			LOG.error(EXCEPTION.getMarker(), "Failed putting key={} value={} in a bucket", key, value, e);
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Fill this bucket with the data contained in the given ByteBuffer.
	 *
	 * @param dataBuffer
	 * 		Buffer containing new data for this bucket
	 */
	public void putAllData(ByteBuffer dataBuffer) {
		ensureCapacity(dataBuffer.limit());
		/* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3939 Maybe we should wrap not copy buffer */
		bucketBuffer.rewind().put(dataBuffer);
	}

	/**
	 * Write the complete data bytes for this bucket to a output stream.
	 *
	 * @param outputStream
	 * 		The stream to write to
	 * @return the number of bytes written
	 * @throws IOException
	 * 		If there was a problem writing
	 */
	public int writeToOutputStream(final SerializableDataOutputStream outputStream) throws IOException {
		final int bucketSize = getSize();
		outputStream.write(bucketBuffer.array(), 0, bucketSize);
		return bucketSize;
	}

	// =================================================================================================================
	// Private API

	/**
	 * Expand the capacity of this bucket to make sure it is at least big enough to contain neededSize
	 */
	private void ensureCapacity(int neededSize) {
		int capacity = bucketBuffer.capacity();
		if (neededSize > capacity) {
			while (capacity < neededSize) {
				capacity += CAPACITY_INCREMENT;
			}
			ByteBuffer newBucketBuffer = ByteBuffer.allocate(capacity);
			bucketBuffer.clear();
			newBucketBuffer.put(bucketBuffer);
			bucketBuffer = newBucketBuffer;
		}
	}

	/**
	 * Find the offset in bucket for an entry matching the given key, if not found then just return the offset for
	 * the end of all entries.
	 *
	 * @param keyHashCode
	 * 		hash code for the key to search for
	 * @param key
	 * 		the key to search for
	 * @return either true and offset for found key entry or false and offset for end of all entries in this bucket
	 * @throws IOException
	 * 		If there was a problem reading bucket
	 */
	private FindResult findEntryOffset(final int keyHashCode, final K key) throws IOException {
		final int entryCount = getBucketEntryCount();
		int entryOffset = BUCKET_HEADER_SIZE;
		for (int i = 0; i < entryCount; i++) {
			final int readHashCode = bucketBuffer.getInt(entryOffset);
			if (readHashCode == keyHashCode) {
				// now check the full key
				bucketBuffer.position(entryOffset + KEY_HASHCODE_SIZE + VALUE_SIZE);
				if (keySerializer.equals(bucketBuffer, keySerializationVersion, key)) {
					// yay! we found it
					return new FindResult(entryOffset, i, true);
				}
			}
			// now read the key size so we can jump
			/* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3932 */
			int keySize = getKeySize(entryOffset);
			// move to next entry
			entryOffset += KEY_HASHCODE_SIZE + VALUE_SIZE + keySize;
		}
		return new FindResult(entryOffset, -1, false);
	}

	/**
	 * Read the size of the key for an entry
	 *
	 * @param entryOffset
	 * 		the offset to start of entry
	 * @return the size of the key in bytes
	 */
	private int getKeySize(final int entryOffset) {
		if (!keySerializer.isVariableSize()) {
			return keySerializer.getSerializedSize();
		}
		bucketBuffer.position(entryOffset + KEY_HASHCODE_SIZE + VALUE_SIZE);
		return keySerializer.deserializeKeySize(bucketBuffer);
	}

	/**
	 * Read a key for a given entry
	 *
	 * @param entryOffset
	 * 		the offset for the entry
	 * @return The key deserialized from bucket
	 * @throws IOException
	 * 		If there was a problem reading or deserializing the key
	 */
	private K getKey(int entryOffset) throws IOException {
		bucketBuffer.position(entryOffset + Integer.BYTES);
		return keySerializer.deserialize(bucketBuffer, keySerializationVersion);
	}

	/**
	 * Read a value for a given entry
	 *
	 * @param entryOffset
	 * 		the offset for the entry
	 * @return the value stored in given entry
	 */
	private long getValue(int entryOffset) {
		return bucketBuffer.getLong(entryOffset + ENTRY_VALUE_OFFSET);
	}

	/**
	 * Read a value for a given entry
	 *
	 * @param entryOffset
	 * 		the offset for the entry
	 * @param value
	 * 		the value to set for entry
	 */
	private void setValue(int entryOffset, long value) {
		bucketBuffer.putLong(entryOffset + ENTRY_VALUE_OFFSET, value);
	}

	/**
	 * toString for debugging
	 */
	@SuppressWarnings("StringConcatenationInsideStringBufferAppend")
	@Override
	public String toString() {
		final int entryCount = getBucketEntryCount();
		final int size = getSize();
		final StringBuilder sb = new StringBuilder(
				"Bucket{bucketIndex=" + getBucketIndex()
						+ ", entryCount=" + entryCount
						+ ", size=" + size + "\n");
		try {
			int entryOffset = BUCKET_HEADER_SIZE;
			for (int i = 0; i < entryCount; i++) {
				final int keySize = getKeySize(entryOffset);
				bucketBuffer.position(entryOffset);
				final int readHash = bucketBuffer.getInt();
				final long value = bucketBuffer.getLong();
				final K key = getKey(entryOffset);
				sb.append(
						"    ENTRY[" + i + "] value= " + value
								+ " keyHashCode=" + readHash
								+ " keyVer=" + keySerializationVersion
								+ " key=" + key
								+ " keySize=" + keySize + "\n");
				entryOffset += KEY_HASHCODE_SIZE + VALUE_SIZE + keySize;
			}
		} catch (IOException e) {
			LOG.error(EXCEPTION.getMarker(), "Failed enumerating bucket entries", e);
		}
		bucketBuffer.clear();
		sb.append("} RAW DATA = ");
		final byte[] bucketArray = bucketBuffer.array();
		for (int i = 0; i < size; i++) {
			sb.append(String.format("%02X ", bucketArray[i]).toUpperCase());
		}
		return sb.toString();
	}


	// =================================================================================================================
	// FindResult class

	/**
	 * Simple struct style data class for allowing return of two things
	 */
	private static class FindResult {
		public final int entryOffset;
		public final int entryIndex;
		public final boolean found;

		public FindResult(final int entryOffset, final int entryIndex, final boolean found) {
			this.entryOffset = entryOffset;
			this.entryIndex = entryIndex;
			this.found = found;
		}
	}

	/**
	 * Get bucket buffer for tests
	 */
	ByteBuffer getBucketBuffer() {
		return bucketBuffer;
	}
}
