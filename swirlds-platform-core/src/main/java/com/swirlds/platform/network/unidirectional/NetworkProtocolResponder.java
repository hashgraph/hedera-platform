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

package com.swirlds.platform.network.unidirectional;

import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.network.NetworkProtocolException;

import java.io.IOException;

/**
 * Used to transfer control of the communication channel to the handler of the initiated protocol
 */
public interface NetworkProtocolResponder {
	/**
	 * Called when a network protocol is initiated by the peer
	 *
	 * @param initialByte
	 * 		the initial byte signifying the protocol type
	 * @param connection
	 * 		the connection over which the protocol was initiated
	 * @throws IOException
	 * 		if any connection issues occur
	 * @throws NetworkProtocolException
	 * 		if any protocol execution issues occur
	 * @throws InterruptedException
	 * 		if the thread is interrupted
	 */
	void protocolInitiated(final byte initialByte, final SyncConnection connection)
			throws IOException, NetworkProtocolException, InterruptedException;
}
