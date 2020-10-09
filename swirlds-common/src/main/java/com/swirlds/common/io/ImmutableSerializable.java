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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SerializableHashable;

import java.io.IOException;

/**
 * Serializable objects that must be preserved indefinitely in the state while
 * maintaining a constant hash should implement this interface.
 */
public interface ImmutableSerializable extends SerializableHashable {

	/**
	 * Return the hash that was set via the setHash method or null if
	 * no hash has yet been set.
	 */
	@Override
	Hash getHash();

	/**
	 * {@inheritDoc}
	 */
	@Override
	void setHash(Hash hash);

	/**
	 * {@inheritDoc}
	 *
	 * If this object was deserialized, this method must emit the exact bytes that
	 * it was previously serialized as. If code no longer exists to serialize
	 * this object in the received format then this object is responsible for
	 * storing the exact bytes that were streamed into it in the serialize method.
	 */
	@Override
	void serialize(SerializableDataOutputStream out) throws IOException;

	/**
	 * {@inheritDoc}
	 *
	 * This method must be able to deserialize historical serialization protocols
	 * of this object back to the beginning of its version history.
	 */
	@Override
	void deserialize(SerializableDataInputStream in, int version) throws IOException;

	/**
	 * {@inheritDoc}
	 *
	 * If this object was deserialized, this method must return the version at
	 * which it was deserialized, not the current version of the object in the code.
	 */
	@Override
	int getVersion();
}
