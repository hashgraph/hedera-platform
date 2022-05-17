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

package com.swirlds.jasperdb.files.hashmap;

import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.jasperdb.files.DataItemHeader;
import com.swirlds.jasperdb.files.DataItemSerializer;
import com.swirlds.virtualmap.VirtualKey;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Serializer for writing buckets into a DataFile.
 *
 * @param <K>
 * 		The map key type stored in the buckets
 */
public class BucketSerializer<K extends VirtualKey<? super K>> implements DataItemSerializer<Bucket<K>> {
	/**
	 * Temporary bucket buffers. There is an open question if this should be static, the reason it is not is we need
	 * different ThreadLocals for each key type.
	 */
	@SuppressWarnings("rawtypes")
	private static final ThreadLocal<Bucket> REUSABLE_BUCKETS = new ThreadLocal<>();

	/**
	 * How many of the low-order bytes in the serialization version are devoted to non-key serialization metadata.
	 */
	private static final int LOW_ORDER_BYTES_FOR_NON_KEY_SERIALIZATION_VERSION = 32;
	/** The version number for serialization data format for this bucket */
	private static final int BUCKET_SERIALIZATION_VERSION = 1;

	/** The current combined serialization version, for both bucket header and key serializer */
	private final long currentSerializationVersion;
	/** The key serializer that we use for keys in buckets */
	private final KeySerializer<K> keySerializer;

	public BucketSerializer(final KeySerializer<K> keySerializer) {
		this.keySerializer = keySerializer;
		long keyVersion = keySerializer.getCurrentDataVersion();
		if (Long.numberOfLeadingZeros(keyVersion) < Integer.SIZE) {
			throw new IllegalArgumentException("KeySerializer versions used in buckets have to be less than a integer.");
		}
		currentSerializationVersion =
				(keySerializer.getCurrentDataVersion() << LOW_ORDER_BYTES_FOR_NON_KEY_SERIALIZATION_VERSION) |
						BUCKET_SERIALIZATION_VERSION;
	}

	/**
	 * Get a reusable bucket for current thread, cleared as an empty bucket
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Bucket<K> getReusableEmptyBucket() {
		Bucket reusableBucket = REUSABLE_BUCKETS.get();
		Bucket<K> bucket;
		if (reusableBucket == null) {
			bucket = new Bucket<>(keySerializer);
			REUSABLE_BUCKETS.set(bucket);
		} else {
			bucket = reusableBucket;
			bucket.setKeySerializer(keySerializer);
			bucket.clear();
		}
		return bucket;
	}

	/**
	 * Get the number of bytes used for data item header
	 *
	 * @return size of header in bytes
	 */
	@Override
	public int getHeaderSize() {
		return Integer.BYTES + Integer.BYTES;
	}

	/**
	 * Deserialize data item header from the given byte buffer
	 *
	 * @param buffer
	 * 		Buffer to read from
	 * @return The read header
	 */
	@Override
	public DataItemHeader deserializeHeader(final ByteBuffer buffer) {
		int bucketIndex = buffer.getInt();
		int size = buffer.getInt();
		return new DataItemHeader(size, bucketIndex);
	}

	/**
	 * Get if the number of bytes a data item takes when serialized is variable or fixed
	 *
	 * @return true if getSerializedSize() == DataFileCommon.VARIABLE_DATA_SIZE
	 */
	@Override
	public boolean isVariableSize() {
		return true;
	}

	/**
	 * Get the number of bytes a data item takes when serialized
	 *
	 * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
	 */
	@Override
	public int getSerializedSize() {
		return DataFileCommon.VARIABLE_DATA_SIZE;
	}

	/**
	 * Get the current serialization version. This a combination of the bucket header's serialization version and the
	 * KeySerializer's serialization version.
	 */
	@Override
	public long getCurrentDataVersion() {
		return currentSerializationVersion;
	}

	/**
	 * Deserialize a data item from a byte buffer, that was written with given data version
	 *
	 * @param buffer
	 * 		The buffer to read from
	 * @param dataVersion
	 * 		The serialization version the data item was written with
	 * @return Deserialized data item
	 */
	@Override
	public Bucket<K> deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
		Bucket<K> bucket = getReusableEmptyBucket();
		bucket.putAllData(buffer);
		// split bucketSerializationVersion
		bucket.setKeySerializationVersion((int) (dataVersion >> LOW_ORDER_BYTES_FOR_NON_KEY_SERIALIZATION_VERSION));
		return bucket;
	}

	/**
	 * Serialize a data item to the output stream returning the size of the data written
	 *
	 * @param bucket
	 * 		The data item to serialize
	 * @param outputStream
	 * 		Output stream to write to
	 */
	@Override
	public int serialize(final Bucket<K> bucket, final SerializableDataOutputStream outputStream) throws IOException {
		return bucket.writeToOutputStream(outputStream);
	}

	/**
	 * Copy the serialized data item in dataItemData into the writingStream. Important if serializedVersion is not the
	 * same as current serializedVersion then update the data to the latest serialization.
	 *
	 * @param serializedVersion
	 * 		The serialized version of the data item in dataItemData
	 * @param dataItemSize
	 * 		The size in bytes of the data item dataItemData
	 * @param dataItemData
	 * 		Buffer containing complete data item including the data item header
	 * @param writingStream
	 * 		The stream to write data item out to
	 * @return the number of bytes written, this could be the same as dataItemSize or bigger or smaller if
	 * 		serialization version has changed.
	 * @throws IOException
	 * 		if there was a problem writing data item to stream or converting it
	 */
	@Override
	public int copyItem(
			final long serializedVersion,
			final int dataItemSize,
			final ByteBuffer dataItemData,
			final SerializableDataOutputStream writingStream
	) throws IOException {
		/* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3942 */
		return DataItemSerializer.super.copyItem(serializedVersion, dataItemSize, dataItemData, writingStream);
	}
}
