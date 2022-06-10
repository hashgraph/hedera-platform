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

package com.swirlds.platform.network.topology;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.InboundConnectionManager;
import com.swirlds.platform.network.OutboundConnectionManager;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.NETWORK;

/**
 * Pre-builds connection managers for the supplied topology, does not allow changes at runtime
 */
public class StaticConnectionManagers {
	private static final Logger LOG = LogManager.getLogger();
	private final NetworkTopology topology;
	private final Map<ConnectionMapping, ConnectionManager> connectionManagers;

	public StaticConnectionManagers(
			final NetworkTopology topology,
			final OutboundConnectionCreator connectionCreator) {
		this.topology = topology;
		// is thread safe because it never changes
		connectionManagers = new HashMap<>();
		for (final NodeId neighbor : topology.getNeighbors()) {
			if (topology.shouldConnectToMe(neighbor)) {
				connectionManagers.put(
						new ConnectionMapping(neighbor, false),
						new InboundConnectionManager()
				);
			}
			if (topology.shouldConnectTo(neighbor)) {
				connectionManagers.put(
						new ConnectionMapping(neighbor, true),
						new OutboundConnectionManager(neighbor, connectionCreator)
				);
			}
		}
	}

	public ConnectionManager getManager(final NodeId id, final boolean outbound) {
		final ConnectionMapping key = new ConnectionMapping(id, outbound);
		return connectionManagers.get(key);
	}

	/**
	 * Called when a new connection is established by a peer. After startup, we don't expect this to be called unless
	 * there are networking issues. The connection is passed on to the appropriate connection manager if valid.
	 *
	 * @param newConn
	 * 		a new connection that has been established
	 */
	public void newConnection(final SyncConnection newConn) throws InterruptedException {
		if (!topology.shouldConnectToMe(newConn.getOtherId())) {
			LOG.error(EXCEPTION.getMarker(), "Unexpected new connection {}", newConn.getDescription());
			newConn.disconnect();
		}

		final ConnectionMapping key = new ConnectionMapping(newConn.getOtherId(), false);
		final ConnectionManager cs = connectionManagers.get(key);
		if (cs == null) {
			LOG.error(EXCEPTION.getMarker(), "Unexpected new connection {}", newConn.getDescription());
			newConn.disconnect();
			return;
		}
		LOG.debug(NETWORK.getMarker(), "{} accepted connection from {}", newConn.getSelfId(), newConn.getOtherId());
		try {
			cs.newConnection(newConn);
		} catch (final InterruptedException e) {
			LOG.error(EXCEPTION.getMarker(),
					"Interrupted while handling over new connection {}", newConn.getDescription(), e);
			newConn.disconnect();
			throw e;
		}
	}

	private record ConnectionMapping(NodeId id, boolean outbound) {
	}
}
