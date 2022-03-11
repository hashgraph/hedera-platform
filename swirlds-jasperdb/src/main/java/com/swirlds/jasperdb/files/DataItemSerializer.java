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

package com.swirlds.jasperdb.files;

import java.nio.ByteBuffer;

/**
 * Interface for serializers of DataItems, a data item consists of a key and a value.
 *
 * @param <T>
 * 		The type for the data item, expected to contain the key/value pair
 */
public interface DataItemSerializer<T> extends BaseSerializer<T> {

	/**
	 * Get the number of bytes used for data item header
	 *
	 * @return size of header in bytes
	 */
	int getHeaderSize();

	/**
	 * Deserialize data item header from the given byte buffer
	 *
	 * @param buffer
	 * 		Buffer to read from
	 * @return The read header
	 */
	DataItemHeader deserializeHeader(ByteBuffer buffer);
}
