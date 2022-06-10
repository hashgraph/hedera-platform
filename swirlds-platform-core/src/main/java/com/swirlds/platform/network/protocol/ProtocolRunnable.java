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

package com.swirlds.platform.network.protocol;

import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.network.NetworkProtocolException;

import java.io.IOException;

/**
 * Represents a method for running a network protocol
 */
@FunctionalInterface
public interface ProtocolRunnable {
	/**
	 * Run the protocol over the provided connection. Once the protocol is done running, it should not leave any unread
	 * bytes in the input stream unless an exception is thrown. This is important since the connection will be reused.
	 *
	 * @param connection
	 * 		the connection to run the protocol on
	 * @throws NetworkProtocolException
	 * 		if a protocol specific issue occurs
	 * @throws IOException
	 * 		if an I/O issue occurs
	 * @throws InterruptedException
	 * 		if the calling thread is interrupted while running the protocol
	 */
	void runProtocol(SyncConnection connection)
			throws NetworkProtocolException, IOException, InterruptedException;
}
