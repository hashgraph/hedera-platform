/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.platform;

import java.io.IOException;

/**
 * Defines the public entry point from which the actual gossip exchange protocol is initiated once a mutual TLS
 * connection is established. Does not handle the heartbeat/keepalive communication between gossip exchanges.
 */
interface NodeSynchronizer {

	/**
	 * Execute exactly one gossip session between two computers.
	 * <p>
	 * Calling an implementor of this interface initiates a single gossip session. A gossip session is a sequence
	 * of steps executed on two computers, in order to exchange event data that the other does not have before this
	 * function is called, for those two computers.
	 * <p>
	 * This function symbol is the only public entry point into the hashgraph gossip protocol.
	 * <p>
	 * The implementor of this interface is invoked only from a {@link SyncCaller} or a {@link SyncListener} thread.
	 * In general there are multiple, simultaneous gossip sessions running on a given node.
	 * A gossip session is isolated from any other previous or concurrent gossip session. Concurrent executions of this
	 * function on a given node will execute a gossip session between this node and
	 * distinct other members in the hashgraph network. Neighbor-selection is not implemented by the implementor of this
	 * function.
	 * <p>
	 * This exchange of events uses a {@link SyncConnection} instance, which holds the IO streams and a local
	 * {@link java.net.Socket} used by this node to send/receive data with its peer for this gossip session.
	 *
	 * @param canAcceptSync
	 * 		If this function is executed from a {@link SyncListener} thread, this value is true iff a
	 * 		listener thread can accept a sync request from a caller.
	 *
	 * 		If executed from a {@link SyncCaller} thread, this value is ignored.
	 * @param reconnected
	 * 		true iff this call is the first call after a reconnect on this thread, used for logging
	 * @return true iff a sync was (a) accepted, and (b) completed, including exchange of event data
	 * @throws IOException
	 * 		iff:
	 * 		<p>	(a) an underlying stream instance throws an exception (timeouts and other stream errors)
	 * 		<p> (b) the gossip implementor itself throws an exception
	 */
	boolean synchronize(final boolean canAcceptSync, final boolean reconnected)
			throws Exception;


}
