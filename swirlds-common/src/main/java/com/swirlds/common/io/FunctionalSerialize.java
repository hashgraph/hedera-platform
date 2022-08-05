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

import com.swirlds.common.io.streams.SerializableDataOutputStream;

import java.io.IOException;

@FunctionalInterface
public interface FunctionalSerialize {
	/**
	 * Serializes the data in the object in a deterministic manner. The class ID and version number should not be
	 * written by this method, it should only include internal data.
	 *
	 * @param out
	 * 		The stream to write to.
	 * @throws IOException
	 * 		Thrown in case of an IO exception.
	 */
	void serialize(SerializableDataOutputStream out) throws IOException;
}
