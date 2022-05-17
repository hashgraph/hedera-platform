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

package com.swirlds.common.io.extendable;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An object that extends the functionality of an {@link OutputStream}.
 */
public interface OutputStreamExtension extends Closeable {

	/**
	 * Initialize the stream extension.
	 *
	 * @param baseStream
	 * 		the base stream that is being extended
	 */
	void init(final OutputStream baseStream);

	/**
	 * This method is called when the {@link OutputStream#write(int)} is invoked on the underlying stream.
	 * This method is required to eventually call {@link OutputStream#write(int)} on the base stream.
	 *
	 * @param b
	 * 		a byte to be written
	 * @throws IOException
	 * 		if there is a problem while writing
	 */
	void write(int b) throws IOException;

	/**
	 * This method is called when the {@link OutputStream#write(byte[], int, int)} is invoked on the underlying stream.
	 * This method is required to eventually call {@link OutputStream#write(byte[], int, int)} on the base stream.
	 *
	 * @param bytes
	 * 		a byte array to be written
	 * @param offset
	 * 		the offset of the first byte to be written
	 * @param length
	 * 		the number of bytes to be written
	 * @throws IOException
	 * 		if there is a problem while writing
	 */
	void write(byte[] bytes, int offset, int length) throws IOException;

}
