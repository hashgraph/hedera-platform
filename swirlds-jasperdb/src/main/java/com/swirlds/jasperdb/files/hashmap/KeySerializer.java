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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.jasperdb.files.BaseSerializer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Interface for serializers of hash map keys. This is very similar to a DataItemSerializer but only serializes a key.
 * The key can serialize to fixed number or variable number of bytes.
 * <p>
 * The reason that the VirtualKey's own serialization is not used here is because it is very important that the data
 * written for each key is as small as possible. For that reason serialization is version once per file not once per
 * key written.
 * </p>
 *
 * @param <K>
 * 		the class for a key
 */
public interface KeySerializer<K> extends BaseSerializer<K>, SelfSerializable {

	/**
	 * Get the current data item serialization version. Key serializers can only use the lower 32 bits of the version
	 * long as the upper 32 are used by the BucketSerializer.
	 */
	long getCurrentDataVersion();

	/**
	 * Deserialize key size from the given byte buffer
	 *
	 * @param buffer
	 * 		Buffer to read from
	 * @return The number of bytes used to store the key, including for storing the key size if needed.
	 */
	int deserializeKeySize(ByteBuffer buffer);

	/**
	 * Compare keyToCompare's data to that contained in the given ByteBuffer. The data in the buffer is assumed to be
	 * starting at the current buffer position and in the format written by this class's serialize() method. The reason
	 * for this rather than just deserializing then doing an object equals is performance. By doing the comparison here
	 * you can fail fast on the first byte that does not match. As this is used in a tight loop in searching a hash map
	 * bucket for a match performance is critical.
	 *
	 * @param buffer
	 * 		The buffer to read from and compare to
	 * @param dataVersion
	 * 		The serialization version of the data in the buffer
	 * @param keyToCompare
	 * 		The key to compare with the data in the file.
	 * @return true if the content of the buffer matches this class's data
	 * @throws IOException
	 * 		If there was a problem reading from the buffer
	 */
	boolean equals(ByteBuffer buffer, int dataVersion, K keyToCompare) throws IOException;
}
