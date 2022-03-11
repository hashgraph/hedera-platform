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

package com.swirlds.jasperdb;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileOutputStream;
import com.swirlds.jasperdb.files.DataItemHeader;
import com.swirlds.jasperdb.files.DataItemSerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import static com.swirlds.jasperdb.files.DataFileCommon.VARIABLE_DATA_SIZE;
import static com.swirlds.jasperdb.utilities.HashTools.byteBufferToHash;

/**
 * VirtualLeafRecordSerializer serializer responsible for serializing and deserializing virtual leaf records. It depends
 * on the serialization implementations of the VirtualKey and VirtualValue.
 *
 * @param <K>
 * 		VirtualKey type
 * @param <V>
 * 		VirtualValue type
 */
@SuppressWarnings("DuplicatedCode")
public class VirtualLeafRecordSerializer<K extends VirtualKey<? super K>, V extends VirtualValue>
		implements DataItemSerializer<VirtualLeafRecord<K, V>>, SelfSerializable {

	private static final long CLASS_ID = 0x39f4704ad17104fL;

	private static final class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	private static final int DEFAULT_TYPICAL_VARIABLE_SIZE = 1024;
	/** DataFileOutputStream needed when we are writing variable sized data */
	private static final ThreadLocal<DataFileOutputStream> DATA_FILE_OUTPUT_STREAM_THREAD_LOCAL =
			ThreadLocal.withInitial(() -> new DataFileOutputStream(DEFAULT_TYPICAL_VARIABLE_SIZE));

	/** The current serialization version for hash, key and value */
	private long currentVersion;
	/** Constructor for creating new key objects during de-serialization */
	private SelfSerializableSupplier<K> keyConstructor;
	/** Constructor for creating new value objects during de-serialization */
	private SelfSerializableSupplier<V> valueConstructor;
	/** computed based on keySizeBytes or valueSizeBytes == DataFileCommon.VARIABLE_DATA_SIZE */
	private boolean hasVariableDataSize;
	/** Total size for serialized data for hash, key and value */
	private int totalSerializedSize;
	/** The size of the header in bytes */
	private int headerSize;
	/** True for when max serialized size can fit in one byte */
	private boolean byteMaxSize;

	public VirtualLeafRecordSerializer() {

	}

	/**
	 * Construct a new VirtualLeafRecordSerializer
	 *
	 * @param hashSerializationVersion
	 * 		The serialization version for hash, less than 65,536
	 * @param hashDigest
	 * 		The digest uses for hashes
	 * @param keySerializationVersion
	 * 		The serialization version for key, less than 65,536
	 * @param keySizeBytes
	 * 		The number of bytes used by a serialized keu, can be DataFileCommon.VARIABLE_DATA_SIZE
	 * @param keyConstructor
	 * 		Constructor for creating new key instances during deserialization
	 * @param valueSerializationVersion
	 * 		The serialization version for value, less than 65,536
	 * @param valueSizeBytes
	 * 		The number of bytes used by a serialized value, can be DataFileCommon.VARIABLE_DATA_SIZE
	 * @param valueConstructor
	 * 		Constructor for creating new value instances during deserialization
	 * @param maxKeyValueSizeLessThan198
	 * 		Is max size of serialized key and value is less than (255-(1+8+48)) = 198
	 */
	public VirtualLeafRecordSerializer(
			final short hashSerializationVersion,
			final DigestType hashDigest,
			final short keySerializationVersion,
			final int keySizeBytes,
			final SelfSerializableSupplier<K> keyConstructor,
			final short valueSerializationVersion,
			final int valueSizeBytes,
			final SelfSerializableSupplier<V> valueConstructor,
			final boolean maxKeyValueSizeLessThan198) {
		/* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3941 */
		this.currentVersion = (0x000000000000FFFFL & hashSerializationVersion) |
				((0x000000000000FFFFL & keySerializationVersion) << 16) |
				((0x000000000000FFFFL & valueSerializationVersion) << 32);
		this.keyConstructor = keyConstructor;
		this.valueConstructor = valueConstructor;
		this.byteMaxSize = maxKeyValueSizeLessThan198;
		this.hasVariableDataSize = keySizeBytes == VARIABLE_DATA_SIZE || valueSizeBytes == VARIABLE_DATA_SIZE;
		this.totalSerializedSize = hasVariableDataSize ? VARIABLE_DATA_SIZE :
				(Long.BYTES + hashDigest.digestLength() + keySizeBytes + valueSizeBytes);
		this.headerSize = Long.BYTES + (byteMaxSize ? 1 : Integer.BYTES);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}

	/**
	 * Get the number of bytes used for data item header
	 *
	 * @return size of header in bytes
	 */
	@Override
	public int getHeaderSize() {
		return headerSize;
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
		final int size;
		if (isVariableSize()) {
			if (byteMaxSize) {
				size = buffer.get();
			} else {
				size = buffer.getInt();
			}
		} else {
			size = totalSerializedSize;
		}
		final long key = buffer.getLong();
		return new DataItemHeader(size, key);
	}

	/**
	 * Get if the number of bytes a data item takes when serialized is variable or fixed
	 *
	 * @return true if getSerializedSize() == DataFileCommon.VARIABLE_DATA_SIZE
	 */
	@Override
	public boolean isVariableSize() {
		return hasVariableDataSize;
	}

	/**
	 * Get the number of bytes a data item takes when serialized
	 *
	 * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
	 */
	@Override
	public int getSerializedSize() {
		return totalSerializedSize;
	}

	/**
	 * For variable sized data get the typical  number of bytes a data item takes when serialized
	 *
	 * @return Either for fixed size same as getSerializedSize() or a estimated typical size for data items
	 */
	@Override
	public int getTypicalSerializedSize() {
		/* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3938 */
		return hasVariableDataSize ? DEFAULT_TYPICAL_VARIABLE_SIZE : totalSerializedSize;
	}

	/**
	 * Get the current data item serialization version
	 */
	@Override
	public long getCurrentDataVersion() {
		return currentVersion;
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
	public VirtualLeafRecord<K, V> deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
		final int hashSerializationVersion = (int) (0x000000000000FFFFL & dataVersion);
		final int keySerializationVersion = (int) (0x000000000000FFFFL & (dataVersion >>> 16));
		final int valueSerializationVersion = (int) (0x000000000000FFFFL & (dataVersion >>> 32));
		final DataItemHeader dataItemHeader = deserializeHeader(buffer);
		// deserialize path
		final long path = dataItemHeader.getKey();
		// deserialize hash
		final Hash hash = byteBufferToHash(buffer, hashSerializationVersion);
		// deserialize key
		final K key = keyConstructor.get();
		key.deserialize(buffer, keySerializationVersion);
		// deserialize value
		final V value = valueConstructor.get();
		value.deserialize(buffer, valueSerializationVersion);
		// return new VirtualLeafRecord
		return new VirtualLeafRecord<>(path, hash, key, value);
	}

	/**
	 * Serialize a data item including header to the output stream returning the size of the data written
	 *
	 * @param leafRecord
	 * 		The virtual record data item to serialize
	 * @param outputStream
	 * 		Output stream to write to
	 */
	@Override
	public int serialize(
			final VirtualLeafRecord<K, V> leafRecord,
			final SerializableDataOutputStream outputStream
	) throws IOException {
		final SerializableDataOutputStream serializableDataOutputStream = hasVariableDataSize ?
				DATA_FILE_OUTPUT_STREAM_THREAD_LOCAL.get().reset() : outputStream;
		// put path (data item key)
		serializableDataOutputStream.writeLong(leafRecord.getPath());
		// put hash
		serializableDataOutputStream.write(leafRecord.getHash().getValue());
		// put key
		leafRecord.getKey().serialize(serializableDataOutputStream);
		// put value
		leafRecord.getValue().serialize(serializableDataOutputStream);
		// get bytes written and write buffer if needed
		int bytesWritten;
		if (hasVariableDataSize) {
			serializableDataOutputStream.flush();
			bytesWritten = ((DataFileOutputStream) serializableDataOutputStream).bytesWritten();
			// write size to stream
			if (byteMaxSize) {
				bytesWritten += 1;
				outputStream.write((byte) bytesWritten);
			} else {
				bytesWritten += Integer.BYTES;
				outputStream.writeInt(bytesWritten);
			}
			// write buffered serialized data to stream
			((DataFileOutputStream) serializableDataOutputStream).writeTo(outputStream);
		} else {
			bytesWritten = totalSerializedSize;
		}
		return bytesWritten;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeLong(currentVersion);
		out.writeSerializable(keyConstructor, true);
		out.writeSerializable(valueConstructor, true);
		out.writeBoolean(hasVariableDataSize);
		out.writeInt(totalSerializedSize);
		out.writeInt(headerSize);
		out.writeBoolean(byteMaxSize);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		currentVersion = in.readLong();
		keyConstructor = in.readSerializable();
		valueConstructor = in.readSerializable();
		hasVariableDataSize = in.readBoolean();
		totalSerializedSize = in.readInt();
		headerSize = in.readInt();
		byteMaxSize = in.readBoolean();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final VirtualLeafRecordSerializer<?, ?> that = (VirtualLeafRecordSerializer<?, ?>) o;
		return currentVersion == that.currentVersion &&
				hasVariableDataSize == that.hasVariableDataSize &&
				totalSerializedSize == that.totalSerializedSize &&
				headerSize == that.headerSize &&
				byteMaxSize == that.byteMaxSize &&
				Objects.equals(keyConstructor, that.keyConstructor) &&
				Objects.equals(valueConstructor, that.valueConstructor);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Objects.hash(currentVersion, keyConstructor, valueConstructor,
				hasVariableDataSize, totalSerializedSize, headerSize, byteMaxSize);
	}
}
