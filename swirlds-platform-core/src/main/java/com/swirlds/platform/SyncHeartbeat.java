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
import com.swirlds.platform.sync.SyncConnectionState;
import com.swirlds.platform.sync.SyncException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.HEARTBEAT;
import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;

/**
 * Periodically send a heartbeat through the same channel the SyncCaller uses, to keep it alive.
 */
class SyncHeartbeat implements Runnable {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	AbstractPlatform platform;
	// ID number for member to send heartbeats to
	NodeId otherId;

	/**
	 * the platform instantiates the SyncHeartbeat, and gives it a reference to itself, plus other info that
	 * will be useful to it. The SyncHeartbeat will periodically send a heartbeat byte to a single, specific
	 * member, and receive their ACK each time, using the same channel that the SyncCaller uses. So there is
	 * a separate SyncHeartbeat per member.
	 */
	public SyncHeartbeat(final AbstractPlatform platform, final NodeId otherId) {
		this.platform = platform;
		this.otherId = otherId;
	}

	/**
	 * run forever, periodically sending out heartbeats, and receiving ACKs
	 */
	public void run() {
		while (true) { // loop forever until interrupted or user quits the browser
			try {
				log.debug(HEARTBEAT.getMarker(), "about to check connection");

				// get the existing connection. If it's not yet connected, try to establish a connection.
				// We only try once per time through this loop.
				final SocketSyncConnection conn = platform.getSyncClient()
						.getCallerConnOrConnectOnce(otherId);

				if (conn == null || !conn.connected()) {
					// if not connected, try once to connect
					platform.getSyncClient().getCallerConnOrConnectOnce(otherId);
				}

				if (conn != null && conn.connected()) {
					// if connected now, send one heartbeat and receive one heartbeat ACK
					doHeartbeat(conn);
				}

				log.debug(HEARTBEAT.getMarker(), "heartbeat about to sleep");
				Thread.sleep(Settings.sleepHeartbeat); // Slow down heartbeats to match the configured interval
				log.debug(HEARTBEAT.getMarker(), "heartbeat awoke");
			} catch (final InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			} catch (final Exception e) {
				log.error(EXCEPTION.getMarker(), "Exception while sending/receiving heartbeat/ACK:", e);
			}
		}
	}

	/**
	 * Send out a heartbeat and wait to read the ACK response. The connection conn must be non-null, and
	 * must have a valid connection. If the wait is too long, it will time out, disconnect, and try once to
	 * reconnect. The send and read (but not disconnect and connect) happen while holding the lock
	 * lockCallHeartbeat.
	 * <p>
	 * If conn is non-null, but the connection isn't valid, so conn.getDis() or conn.getDos() return null,
	 * then this returns immediately.
	 *
	 * @param conn
	 * 		the connection (which must already be connected)
	 */
	void doHeartbeat(final SocketSyncConnection conn) {
		log.debug(HEARTBEAT.getMarker(),
				"about to lock platform[{}].syncServer.lockCallHeartbeat[{}]",
				platform.getSelfId(), otherId);
		final ReentrantLock lock = platform.getSyncServer().getOutboundConnLock(otherId);
		if (!conn.connected()) {
			return;
		}
		// Ensure SyncCaller and heartbeat takes turns on this socket.
		// If the SyncCaller is currently syncing, block forever until it's done.
		lock.lock(); // block forever until available
		try {
			log.debug(HEARTBEAT.getMarker(),
					"locked platform[{}].syncServer.lockCallHeartbeat[{}]",
					platform.getSelfId(), otherId);
			log.debug(HEARTBEAT.getMarker(), "about to send heartbeat");

			final SyncConnectionState prevState = conn.getCurrentState();
			final long startTime = System.nanoTime();
			conn.heartbeat();
			// we don't record ping times for every heartbeat, explained in #4533
			if (prevState == SyncConnectionState.HEARTBEAT_ACKNOWLEDGED) {
				platform.getStats().avgPingMilliseconds[otherId.getIdAsInt()].recordValue(
						(System.nanoTime() - startTime) / 1_000_000.0);
			}
		} catch (SyncException e) {
			log.error(EXCEPTION.getMarker(), "error while doing heartbeat", e);
			conn.disconnect(true, 12);
			platform.getSyncClient().getCallerConnOrConnectOnce(otherId); // try once to reconnect
		} catch (final IOException e) {
			// this is either a timeout because nothing was received for a long time (e.g., 15 seconds),
			// or an error that happened during the read or write (e.g., because the socket closed)
			log.error(SOCKET_EXCEPTIONS.getMarker(), "error while sending or receiving the heartbeat:", e);
			conn.disconnect(true, 13);
			platform.getSyncClient().getCallerConnOrConnectOnce(otherId); // try once to reconnect
		} finally { // release the lock, because done sending/receiving heartbeats/ACKs
			log.debug(HEARTBEAT.getMarker(),
					"about to unlock platform[{}].syncServer.lockCallHeartbeat[{}]",
					platform.getSelfId(), otherId);
			log.debug(HEARTBEAT.getMarker(),
					"unlocked platform[{}].syncServer.lockCallHeartbeat[{}]",
					platform.getSelfId(), otherId);
			lock.unlock();
		}
	}
}
