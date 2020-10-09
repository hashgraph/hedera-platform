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
package com.swirlds.common.io;

import com.swirlds.common.CommonUtils;
import com.swirlds.common.constructable.ConstructableRegistry;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.swirlds.common.io.SerializableStreamConstants.NULL_CLASS_ID;
import static com.swirlds.common.io.SerializableStreamConstants.NULL_LIST_ARRAY_LENGTH;
import static com.swirlds.common.io.SerializableStreamConstants.NULL_VERSION;
import static com.swirlds.common.io.SerializableStreamConstants.SERIALIZATION_PROTOCOL_VERSION;

/**
 * A drop-in replacement for {@link DataInputStream}, which handles SerializableDet classes specially.
 * It is designed for use with the SerializableDet interface, and its use is described there.
 */
public class SerializableDataInputStream extends ExtendedDataInputStream {
	private int protocolVersion = SERIALIZATION_PROTOCOL_VERSION;
	// Use {@inheritDoc} when it starts working--right now (9/26/19) doesn't seem to

	/**
	 * Creates a FCDataInputStream that uses the specified
	 * underlying InputStream.
	 *
	 * @param in
	 * 		the specified input stream
	 */
	public SerializableDataInputStream(InputStream in) {
		super(in);
	}

	/**
	 * Verify that the flag written at the end of an operation matches the expected value.
	 */
	protected void validateFlag(SelfSerializable object) throws IOException {
		if (SerializableDataOutputStream.debug) {
			long flag = readLong();
			if (flag != (object.getClassId() * -1)) {
				throw new IOException(String.format(
						"Invalid flag at end of serializable object %s (class ID %d(0x%08X)).",
						object.getClass(), object.getClassId(), object.getClassId()));
			}
		}
	}

	/**
	 * Reads the protocol version written by {@link SerializableDataOutputStream#writeProtocolVersion()} and saves it
	 * internally. From this point on, it will use this version number to deserialize.
	 *
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void readProtocolVersion() throws IOException {
		protocolVersion = this.readInt();
	}

	/**
	 * Reads a {@link SerializableDet} from a stream and returns it. The instance will be created using the
	 * {@link ConstructableRegistry}. The instance must have previously been written using
	 * {@link SerializableDataOutputStream#writeSerializable(SelfSerializable, boolean)} (SerializableDet, boolean)}
	 * with {@code writeClassId} set to true, otherwise we
	 * cannot know what the class written is.
	 *
	 * @return An instance of the class previously written
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public <T extends SelfSerializable> T readSerializable() throws IOException {
		return (T) readSerializable(true, SerializableDataInputStream::registryConstructor);
	}

	/**
	 * Uses the provided {@code serializable} to read its data from the stream.
	 *
	 * @param serializableConstructor
	 * 		a constructor for the instance written in the stream
	 * @param readClassId
	 * 		set to true if the class ID was written to the stream
	 * @param <T>
	 * 		the implementation of {@link SelfSerializable} used
	 * @return the same object that was passed in, returned for convenience
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public <T extends SelfSerializable> T readSerializable(
			boolean readClassId,
			Supplier<T> serializableConstructor
	) throws IOException {
		CommonUtils.throwArgNull(serializableConstructor, "serializableConstructor");
		return readSerializable(readClassId, (id) -> serializableConstructor.get());
	}

	/**
	 * Throws an exception if the version is not supported.
	 */
	protected void validateVersion(SerializableDet object, int version) {
		if (version < object.getMinimumSupportedVersion() || version > object.getVersion()) {
			throw new InvalidVersionException(version, object);
		}
	}

	/**
	 * Same as {@link #readSerializable(boolean, Supplier)} except that the constructor takes a class ID
	 */
	private <T extends SelfSerializable> T readSerializable(
			boolean readClassId,
			Function<Long, T> serializableConstructor
	) throws IOException {
		Long classId = null;
		if (readClassId) {
			classId = readLong();
			if (classId == NULL_CLASS_ID) {
				return null;
			}
		}
		int version = readInt();
		if (version == NULL_VERSION) {
			return null;
		}
		T serializable = serializableConstructor.apply(classId);
		validateVersion(serializable, version);
		serializable.deserialize(this, version);
		validateFlag(serializable);
		return serializable;
	}

	public <T extends SelfSerializable> void readSerializableIterableWithSize(int maxSize, Consumer<T> callback)
			throws IOException {
		int size = this.readInt();
		checkLengthLimit(size, maxSize);
		readSerializableIterableWithSize(size, true,
				SerializableDataInputStream::registryConstructor, callback);
	}

	private <T extends SelfSerializable> void readSerializableIterableWithSize(
			int size,
			boolean readClassId,
			Function<Long, T> serializableConstructor,
			Consumer<T> callback) throws IOException {
		// return if size is zero while deserializing similar to serializing
		if (size == 0) {
			return;
		}

		boolean allSameClass = readBoolean();
		boolean classIdVersionRead = false;
		Integer version = null;
		Long classId = null;
		for (int i = 0; i < size; i++) {
			if (!allSameClass) {
				// if classes are different, we just write every class one by one
				callback.accept(readSerializable(readClassId, serializableConstructor));
				continue;
			}
			boolean isNull = this.readBoolean();
			if (isNull) {
				callback.accept(null);
				continue;
			}
			if (!classIdVersionRead) {
				// this is the first non-null member, so we read the ID and version
				if (readClassId) {
					classId = readLong();
				}
				version = readInt();
				classIdVersionRead = true;
			}
			T serializable = serializableConstructor.apply(classId);
			serializable.deserialize(this, version);
			callback.accept(serializable);
		}
	}

	public <T extends SelfSerializable> List<T> readSerializableList(int maxListSize) throws IOException {
		return readSerializableList(maxListSize, true, SerializableDataInputStream::registryConstructor);
	}

	public <T extends SelfSerializable> List<T> readSerializableList(
			int maxListSize,
			boolean readClassId,
			Supplier<T> serializableConstructor) throws IOException {
		CommonUtils.throwArgNull(serializableConstructor, "serializableConstructor");
		return readSerializableList(maxListSize, readClassId, (id) -> serializableConstructor.get());
	}

	public <T extends SelfSerializable> T[] readSerializableArray(
			Function<Integer, T[]> arrayConstructor,
			int maxListSize,
			boolean readClassId,
			Supplier<T> serializableConstructor) throws IOException {
		List<T> list = readSerializableList(maxListSize, readClassId, serializableConstructor);
		if (list == null) {
			return null;
		}
		return list.toArray(arrayConstructor.apply(list.size()));
	}

	private <T extends SelfSerializable> List<T> readSerializableList(
			int maxListSize,
			boolean readClassId,
			Function<Long, T> serializableConstructor) throws IOException {
		int length = this.readInt();
		if (length == NULL_LIST_ARRAY_LENGTH) {
			return null;
		}
		checkLengthLimit(length, maxListSize);

		// ArrayList is used by default, we can add support for different list types in the future
		List<T> list = new ArrayList<>(length);
		if (length == 0) {
			return list;
		}
		readSerializableIterableWithSize(length, readClassId, serializableConstructor, list::add);
		return list;
	}

	// ---------------------------------------------
	//                Helper methods
	// ---------------------------------------------

	private static <T extends SelfSerializable> T registryConstructor(long classId) throws NotSerializableException {
		T rc = ConstructableRegistry.createObject(classId);
		if (rc == null) {
			throw new ClassNotFoundException(classId);
		}
		return rc;
	}
}
