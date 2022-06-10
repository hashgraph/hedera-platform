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


import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.locks.LockedResource;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.network.ByteConstants;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.NetworkStats;
import com.swirlds.platform.network.NetworkUtils;

import java.io.IOException;

/**
 * Periodically send a heartbeat through the connection to keep it alive and measure response time
 */
public class HeartbeatSender implements InterruptableRunnable {
	// ID number for member to send heartbeats to
	private final NodeId otherId;
	private final SharedConnectionLocks sharedConnectionLocks;
	private final NetworkStats stats;
	private final SettingsProvider settings;

	public HeartbeatSender(
			final NodeId otherId,
			final SharedConnectionLocks sharedConnectionLocks,
			final NetworkStats stats,
			final SettingsProvider settings) {
		this.otherId = otherId;
		this.sharedConnectionLocks = sharedConnectionLocks;
		this.stats = stats;
		this.settings = settings;
	}

	/**
	 * if connected, send a heartbeat, wait for a response, then sleep
	 */
	public void run() throws InterruptedException {
		SyncConnection conn = null;
		try (LockedResource<ConnectionManager> lc = sharedConnectionLocks.lockConnectIfNeeded(otherId)) {
			conn = lc.getResource().waitForConnection();
			if (conn != null && conn.connected()) {
				doHeartbeat(conn);
			}
		} catch (final RuntimeException | IOException | NetworkProtocolException e) {
			NetworkUtils.handleNetworkException(e, conn);
		}

		Thread.sleep(settings.sleepHeartbeatMillis()); // Slow down heartbeats to match the configured interval
	}

	/**
	 * Send out a heartbeat and wait to read the ACK response. The connection conn must be non-null, and
	 * must have a valid connection. If the wait is too long, it will time out and disconnect.
	 *
	 * @param conn
	 * 		the connection (which must already be connected)
	 */
	void doHeartbeat(final SyncConnection conn) throws IOException, NetworkProtocolException {
		final long startTime = System.nanoTime();
		conn.getDos().write(UnidirectionalProtocols.HEARTBEAT.getInitialByte());
		conn.getDos().flush();
		conn.setTimeout(settings.getTimeoutSyncClientSocket());
		final byte b = conn.getDis().readByte();
		if (b != ByteConstants.HEARTBEAT_ACK) {
			throw new NetworkProtocolException(String.format(
					"received %02x but expected %02x (heartbeatACK)",
					b, ByteConstants.HEARTBEAT_ACK));
		}
		stats.recordPingTime(otherId, System.nanoTime() - startTime);
	}
}
