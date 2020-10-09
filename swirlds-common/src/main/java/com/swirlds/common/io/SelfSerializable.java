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

import java.io.IOException;

/**
 * A SerializableDet that knows how to serialize and deserialize itself.
 */
public interface SelfSerializable extends SerializableDet, FunctionalSerialize {

	/**
	 * Deserializes an instance that has been previously serialized by {@link #serialize(SerializableDataOutputStream)}.
	 * This method should support all versions of the serialized data.
	 *
	 * @param in
	 * 		The stream to read from.
	 * @param version
	 * 		The version of the serialized instance. Guaranteed to be greater or equal to the minimum version
	 * 		and less than or equal to the current version.
	 * @throws IOException
	 * 		Thrown in case of an IO exception.
	 */
	void deserialize(SerializableDataInputStream in, int version) throws IOException;
}
