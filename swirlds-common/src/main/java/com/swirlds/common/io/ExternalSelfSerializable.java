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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;

import java.io.File;
import java.io.IOException;

/**
 * <p>
 * An object that can serialize itself to a stream and a directory on a file system.
 */
public interface ExternalSelfSerializable extends SerializableDet {

	/**
	 * Serialize data both to the stream and to a directory. The data serialized to the stream
	 * must be sufficient for {@link #deserialize(SerializableDataInputStream, File, int)}
	 * to fully reconstruct the object. Any data written to disk MUST be written to a file that
	 * is contained by the provided directory.
	 *
	 * @param out
	 * 		the stream to write to
	 * @param outputDirectory
	 * 		a location on disk where data can be written. When saving state, this is
	 * 		the same directory that holds the signed state file.
	 * @throws IOException
	 * 		thrown in case of an IO exception
	 */
	void serialize(
			SerializableDataOutputStream out,
			File outputDirectory) throws IOException;

	/**
	 * Reconstruct this object using the data serialized by
	 * {@link #serialize(SerializableDataOutputStream, File)}.
	 * This method may load additional data from the provided directory.
	 *
	 * @param in
	 * 		The input stream.
	 * @param inputDirectory
	 * 		a location on disk where data can be read. Corresponds to the directory passed to
	 *        {@link #serialize(SerializableDataOutputStream, File)}. When reading a saved state,
	 * 		this is the same directory that holds the signed state file.
	 * @param version
	 * 		The version at which this object was serialized. Guaranteed to be greater or equal to the
	 * 		minimum version and less than or equal to the current version.
	 * @throws IOException
	 * 		thrown in case of an IO exception
	 */
	void deserialize(
			SerializableDataInputStream in,
			File inputDirectory,
			int version) throws IOException;

}
