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

package com.swirlds.platform;

import com.swirlds.common.NodeId;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;

import java.io.IOException;
import java.net.SocketException;

/**
 * A connection between two nodes for the purposes of syncing.
 */
public interface SyncConnection {

	void disconnect(final boolean caller, final int debug);

	/**
	 * Returns the {@link NodeId} of this node.
	 *
	 * @return this node's ID
	 */
	NodeId getSelfId();

	/**
	 * Returns the {@link NodeId} of the node this connection is connected to.
	 *
	 * @return the connected node's ID
	 */
	NodeId getOtherId();

	/**
	 * The input stream used to receive data from the connected node.
	 *
	 * @return the input stream
	 */
	SyncInputStream getDis();

	/**
	 * The output stream used to send data to the connected node.
	 *
	 * @return the output stream
	 */
	SyncOutputStream getDos();

	/**
	 * Is there currently a valid connection from me to the member at the given index in the address book?
	 *
	 * If this method returns {@code true}, the underlying socket is guaranteed to be non-null.
	 *
	 * @return true is connected, false otherwise.
	 */
	boolean connected();

	/**
	 * Sets the timeout of this connection.
	 *
	 * @param timeoutMillis
	 * 		The timeout value to set in milliseconds. A value of zero is treated as an infinite timeout.
	 * @throws SocketException
	 * 		if there is an error in the underlying protocol, such as a TCP error.
	 */
	void setTimeout(final int timeoutMillis) throws SocketException;

	/**
	 * Returns the current timeout value of this connection.
	 *
	 * @return the current timeout value in milliseconds
	 * @throws SocketException
	 * 		if there is an error in the underlying protocol, such as a TCP error.
	 */
	int getTimeout() throws SocketException;

	/**
	 * Initialize {@code this} instance for a gossip session.
	 *
	 * @throws IOException
	 * 		if the connection is broken
	 */
	void initForSync() throws IOException;

	/**
	 * Called on the connection at the end of a sync
	 */
	default void syncDone(){
		// do nothing by default
	}

	/**
	 * Is this an outbound or an inbound connection. For outbound connections, we initiate the creation of the
	 * connection, as well as all communication (sync, heartbeat, reconnect). The reverse is true for inbound
	 * connections.
	 *
	 * @return true if this is an outbound connection, false otherwise
	 */
	boolean isOutbound();

	/**
	 * @return a string description of this connection
	 */
	String getDescription();

	/**
	 * Generate a default connection description
	 *
	 * @return the description of the connection
	 */
	default String generateDescription() {
		return String.format("%s %s %s",
				getSelfId(),
				isOutbound() ? "->" : "<-",
				getOtherId());
	}
}
