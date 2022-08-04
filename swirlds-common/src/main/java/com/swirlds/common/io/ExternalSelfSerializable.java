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

package com.swirlds.common.io;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;

import java.io.File;
import java.io.IOException;

/**
 * <p>
 * An object that can serialize itself to an external location.
 * </p>
 *
 * <p>
 * When a ExternalSelfSerializable object is serialized, a portion of the data may be
 * written to locations other than the stream (e.g. a file on a disk or a blob in a database).
 * The data that is written to the stream while in abbreviated form must contain all information required
 * locate the data required to reconstruct the object.
 * </p>
 *
 * <p>
 * A ExternalSelfSerializable may optionally support standard serialization where all data is written to the output
 * stream.
 * </p>
 *
 * <p>
 * The hash of the ExternalSelfSerializable object is automatically serialized and deserialized when it is
 * sent over a stream in abbreviated form. If external data is large and expensive to hash, this allows
 * for a ExternalSelfSerializable instance to verify that the expected hash matches a pre-computed hash
 * of the external data (as opposed to recomputing it every time). Caching of hash values should be done
 * if and only if the authenticity of the external data is beyond reproach. This may be appropriate when
 * the data is stored on a local disk with sufficient physical and digital security. This is definitely not
 * appropriate if the external data is loaded over a network.
 * </p>
 */
public interface ExternalSelfSerializable extends SerializableDet {

	/**
	 * Serialize data both to the stream and to an external source. The data serialized to the stream
	 * must be sufficient for {@link #deserializeExternal(SerializableDataInputStream, File, Hash, int)}
	 * to fully reconstruct the object.
	 *
	 * @param out
	 * 		the stream to write to
	 * @param outputDirectory
	 * 		a location on disk where data can be written. When saving state, this is
	 * 		the same directory that holds the signed state file.
	 * @throws IOException
	 * 		thrown in case of an IO exception
	 */
	void serializeExternal(
			SerializableDataOutputStream out,
			File outputDirectory) throws IOException;

	/**
	 * Reconstruct this object using the data serialized by
	 * {@link #serializeExternal(SerializableDataOutputStream, File)}.
	 * This method may load additional data from external sources.
	 *
	 * @param in
	 * 		The input stream.
	 * @param inputDirectory
	 * 		a location on disk where data can be read. Corresponds to the directory passed to
	 *        {@link #serializeExternal(SerializableDataOutputStream, File)}. When reading a saved state,
	 * 		this is the same directory that holds the signed state file.
	 * @param hash
	 * 		The hash of the object. If the hash is cached with the external data, this can be used as
	 * 		verification.
	 * @param version
	 * 		The version at which this object was serialized. Guaranteed to be greater or equal to the
	 * 		minimum version and less than or equal to the current version.
	 * @throws IOException
	 * 		thrown in case of an IO exception
	 */
	void deserializeExternal(
			SerializableDataInputStream in,
			File inputDirectory,
			Hash hash,
			int version) throws IOException;

}
