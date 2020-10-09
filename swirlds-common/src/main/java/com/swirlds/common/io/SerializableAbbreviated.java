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
 * A SelfSerializable object that can also be serialized in an abbreviated form.
 *
 * When a SerializableAbbreviated object is serialized in its complete form (using the serialize/deserialize methods)
 * all data required to reconstruct the object is stored within the data stream.
 *
 * When a SerializableAbbreviated object is serialized in its abbreviated form, a portion of the data may be
 * propagated over other caller controlled channels (i.e. a file on a disk or a blob in a database).
 * The data that is written to the stream while in abbreviated form must contain all information required
 * locate the data required to reconstruct the object.
 *
 * The hash of the SerializableAbbreviated object is automatically serialized and deserialized when it is
 * sent over a stream in abbreviated form. If external data is large and expensive to hash, this allows
 * for a SerializableAbbreviated instance to verify that the expected hash matches a pre-computed hash
 * of the external data (as opposed to recomputing it every time). Caching of hash values should be done
 * if and only if the authenticity of the external data is beyond reproach. This may be appropriate when
 * the data is stored on a local disk with sufficient physical and digital security. This is definitely not
 * appropriate if the external data is loaded over a network.
 */
public interface SerializableAbbreviated extends SerializableHashable {

	/**
	 * Serialize the bare minimum amount of data required to reconstruct this object.
	 * This data may be references to data stored in an external form.
	 *
	 * @param out
	 * 		the stream to write to
	 * @throws IOException
	 * 		thrown in case of an IO exception
	 */
	void serializeAbbreviated(SerializableDataOutputStream out) throws IOException;

	/**
	 * Reconstruct this object using the data serialized by serializeAbbreviated.
	 * This method may load additional data from external sources.
	 *
	 * @param in
	 * 		The input stream.
	 * @param hash
	 * 		The hash of the leaf. If the hash is cached with the external data, this can be used as
	 * 		verification.
	 * @param version
	 * 		The version at which this object was serialized. Guaranteed to be greater or equal to the
	 * 		minimum version and less than or equal to the current version.
	 * @throws IOException
	 * 		thrown in case of an IO exception
	 */
	void deserializeAbbreviated(SerializableDataInputStream in, Hash hash, int version) throws IOException;

}
