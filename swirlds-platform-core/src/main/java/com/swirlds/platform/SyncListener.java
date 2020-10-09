/*
 * (c) 2016-2020 Swirlds, Inc.
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

import com.swirlds.common.AddressBook;
import com.swirlds.common.AutoCloseableWrapper;
import com.swirlds.common.NodeId;
import com.swirlds.common.io.BadIOException;
import com.swirlds.logging.payloads.ReconnectFinishPayload;
import com.swirlds.logging.payloads.ReconnectStartPayload;
import com.swirlds.platform.reconnect.ReconnectSender;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.StateDumpSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.HEARTBEAT;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;
import static com.swirlds.logging.LogMarker.SYNC_ERROR;
import static com.swirlds.logging.LogMarker.SYNC_SGM;
import static com.swirlds.logging.LogMarker.SYNC_START;

/**
 * Listen for incoming sync requests from one particular member, and perform syncs with them. The
 * SyncListener will forever listen for incoming sync calls from a single, specific member. So there is a
 * separate SyncListener per each member that might call.
 */
class SyncListener implements Runnable {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	/** the Platform object that is using this to call other members */
	private final AbstractPlatform platform;
	/** the member ID for self */
	private final NodeId selfId;
	/** ID number for other member (the one self is listening for) */
	private final NodeId otherId;

	/**
	 * the platform instantiates the SyncListener, and gives it a reference to itself, plus other info that
	 * will be useful to it. The SyncListener will forever listen for incoming sync calls from a single,
	 * specific member. So there is a separate SyncLister per member that might call.
	 */
	public SyncListener(AbstractPlatform platform, AddressBook addressBook, NodeId id,
			NodeId otherId) {
		this.platform = platform;
		this.selfId = id;
		this.otherId = otherId;
	}

	/**
	 * listen forever, handling any incoming syncs
	 */
	@Override
	public void run() {
		try {
			Thread.sleep(Settings.sleepSyncListenerStart);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return;
		}

		platform.getStats().resetAllSpeedometers(); // start the timing AFTER the initial pause
		long lastSuccess = System.nanoTime();
		while (true) { // loop forever, accepting syncs/heartbeats, until the user quits the browser
			try {
				SyncConnection conn = platform.getSyncServer()
						.getListenerConn(otherId);

				if (conn == null || !conn.connected()) {
					// not connected, so sleep and reset lastSuccess
					Thread.sleep(Settings.sleepListenerDisconnected);
					platform.getStats().sleep2perSecond.cycle();
					lastSuccess = System.nanoTime(); // don't count the time while disconnected
				} else if (System.nanoTime()
						- lastSuccess > Settings.timeoutSyncClientSocket
						* 1_000_000L) {
					// we have been "connected" but not reading anything for very long, so disconnect
					log.error(SOCKET_EXCEPTIONS.getMarker(),
							"{} didn't receive anything from {} for {} ms. Disconnecting...",
							selfId, otherId, (System.nanoTime() - lastSuccess) / 1_000_000L);
					conn.disconnect(false, 9);
				} else if (handleOneMsg()) {

					// we successfully read one heartbeat or sync request and handled it
					lastSuccess = System.nanoTime();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception e) {
				log.error(EXCEPTION.getMarker(), "SyncListener.run error 2", e);
				try {
					Thread.sleep(Settings.sleepListenerDisconnected);
					platform.getStats().sleep2perSecond.cycle();
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					return;
				} catch (Exception ex) {
					// Suppress any exception that may have been raised by the stats update
				}
			}
		}

	}

	/**
	 * Wait to receive one sync request or heartbeat, and handle it. If it is a sync request and it is
	 * appropriate to do a sync, then perform a sync with the other member. If it is a heartbeat, then reply
	 * with a heartbeat ACK. If this receives the heartbeat or sync request and handles it, this returns
	 * true. Otherwise, it returns false, which means there isn't a valid connection yet, or an exception
	 * occurred, or nothing was received during a (fairly short) timeout period,
	 *
	 * @return true if successfully read and handled one heartbeat or sync request
	 */
	private boolean handleOneMsg() {
		SyncConnection conn = null;
		try {
			log.debug(SYNC_START.getMarker(),
					"listener {} about to listen for {}", selfId, otherId);
			conn = platform.getSyncServer().getListenerConn(otherId);
			if (conn == null) {
				log.trace(SYNC_ERROR.getMarker(), "Not connected");
				return false; // otherId doesn't have a valid connection, so give up, for now
			}
			log.debug(SYNC_START.getMarker(),
					"listener {} about to listen for {} (connection is good)",
					selfId, otherId);
			// wait for a sync to start, replying to any heartbeats that are received,
			// and when a sync request is finally received, do the sync.
			return handleOneMsgOrException(otherId, platform.getSyncServer());
		} catch (BadIOException e) {
			log.error(SOCKET_EXCEPTIONS.getMarker(),
					"BadIOException.sync IOException (but not incrementing interruptedRecSyncsPerSecond) while {} " +
							"listening for {}:", selfId, otherId, e);
			if (conn != null) {
				conn.disconnect(false, 4);// close the connection, don't reconnect until needed.
			}
		} catch (SocketException e) {
			log.error(SOCKET_EXCEPTIONS.getMarker(),
					"SyncListener.sync SocketException (so incrementing interruptedRecSyncsPerSecond) while {} " +
							"listening for {}:", selfId, otherId, e);
			if (conn != null) {
				conn.disconnect(false, 5);// close the connection, don't reconnect until needed.
			}
		} catch (IOException e) {
			// IOException covers both SocketTimeoutException and EOFException, plus more
			log.error(SOCKET_EXCEPTIONS.getMarker(),
					"SyncListener.sync IOException (so incrementing interruptedRecSyncsPerSecond) while {} listening " +
							"for {}:", selfId, otherId, e);
			if (conn != null) {
				conn.disconnect(false, 6);// close the connection, don't reconnect until needed.
			}
		} catch (Exception e) {
			log.error(EXCEPTION.getMarker(),
					"! SyncListener.sync Exception (so incrementing interruptedRecSyncsPerSecond) while {} listening " +
							"for {}:", selfId, otherId, e);
			if (conn != null) {
				conn.disconnect(false, 7);// close the connection, don't reconnect until needed.
			}
		}
		return false;
	}

	/**
	 * Wait for a sync request from the member with the given ID. It will wait a long time
	 * (Settings.timeoutFirstSyncClientSocket milliseconds) for that byte to be received. If it isn't
	 * received during this period, it throws a timeout exception. During this period, it wakes up
	 * frequently (every Platform.timeoutFirstSyncClientSocketPoll milliseconds) and checks whether the
	 * given SyncServer has a new connection. It wakes up frequently so that if the connection changes, it
	 * will use the new one. This method should be called by the listener who receives heartbeats and sync
	 * invitations, not the caller who sends heartbeats and initiates syncs.
	 * <p>
	 * In addition, if it ever receives a heartbeat byte, it will immediately reply with a heartbeatAck
	 * byte, then go back to waiting.
	 * <p>
	 * If there is currently no connection to otherId, then it returns false immediately.
	 *
	 * @param otherId
	 * 		the ID of the member to sync with
	 * @param syncServer
	 * 		the SyncServer that stores the current connection to every member
	 * @return true if a heartbeat or sync request was read and handled
	 * @throws Exception
	 * 		if there is any exception during sync, or it times out
	 */
	boolean handleOneMsgOrException(NodeId otherId, SyncServer syncServer)
			throws Exception {
		SyncConnection conn;
		NodeId selfId;
		LoggingReentrantLock lockCallListen = platform.getSyncServer().lockCallListen
				.get(otherId.getIdAsInt());// otherId assumed to be main
		Socket socket = null;
		DataInputStream dis;

		log.debug(SYNC_START.getMarker(),
				"about to read sync request or heartbeat (willing to wait for it)");

		byte b;
		conn = syncServer.getListenerConn(otherId);
		if (conn == null || !conn.connected()) {
			return false; // there is no connection to otherId, so return immediately
		}
		otherId = conn.getOtherId();
		selfId = conn.getSelfId();
		socket = conn.getSocket();
		dis = conn.getDis();
		if (dis == null) {
			return false; // there is no connection to otherId, so return immediately
		}

		// a short timeout so that we will wake up frequently and check whether the connection is still
		// valid.There is no harm in this timeout being short, because this method will be called
		// repeatedly until something is received (or until a much longer timeout happens).
		socket.setSoTimeout(Settings.waitListenerRead);
		try {
			log.debug(HEARTBEAT.getMarker(), "{} lsntr wait for {}", selfId, otherId);
			b = dis.readByte();
		} catch (SocketTimeoutException e) {
			return false; // this short timeout isn't bad, but return false because nothing was read
		}
		DataOutputStream dos = conn.getDos();
		if (dos == null) {
			return false; // there is no connection to otherId, so return immediately
		}
		if (b == SyncConstants.heartbeat) {
			log.debug(HEARTBEAT.getMarker(), "received heartbeat");
			dos.writeByte(SyncConstants.heartbeatAck);
			dos.flush();
			log.debug(HEARTBEAT.getMarker(), "sent heartbeatACK");
			return true;
		} else if (b == SyncConstants.commSyncRequest) {
			log.debug(HEARTBEAT.getMarker(), "received commSyncRequest");
			syncServer.numListenerSyncs.incrementAndGet(); // matching decr in finally
			syncServer.numSyncs.incrementAndGet(); // matching decr in finally
			try {
				if (platform.getSyncManager().hasFallenBehind()) {
					// if we have fallen behind, dont accept any syncs
					SyncUtils.sync(conn, false, false);
				} else if (!lockCallListen
						.tryLock("SyncListener.handleOneMsgOrException 1")) {
					// caller is already syncing with otherId, so reply NACK
					SyncUtils.sync(conn, false, false);
				} else {
					log.debug(HEARTBEAT.getMarker(),
							"SyncListener locked platform[{}].syncServer.lockCallListen[{}]",
							platform.getSelfId(), otherId);
					try {
						boolean acceptIncoming = (platform.getSyncManager().shouldAcceptSync());
						log.debug(SYNC_SGM.getMarker(), " `SyncListener.handleOneMsgOrException`: entering `SyncUtils.sync`");
						SyncUtils.sync(conn, false, acceptIncoming);
					} finally {
						lockCallListen.unlock(
								"SyncListener.handleOneMsgOrException 2");
						log.debug(HEARTBEAT.getMarker(),
								"SyncListener unlocked platform[{}].syncServer.lockCallListen[{}]",
								platform.getSelfId(), otherId);
					}
				}
			} finally {
				syncServer.numListenerSyncs.decrementAndGet();
				syncServer.numSyncs.decrementAndGet();
			}
			return true;
		} else if (b == SyncConstants.commStateRequest) {
			log.debug(RECONNECT.getMarker(), "{} got commStateRequest from {}", platform.getSelfId(), otherId);

			try (AutoCloseableWrapper<SignedState> stateWrapper =
						 platform.getSignedStateManager().getLastCompleteSignedState()) {

				// This was added to support writing signed state JSON files after every reconnect
				// This is enabled/disabled via settings and is disabled by default
				platform.getSignedStateManager().jsonifySignedState(stateWrapper.get(), StateDumpSource.RECONNECT);

				final int oid = otherId.getIdAsInt();
				log.debug(RECONNECT.getMarker(), () -> new ReconnectStartPayload(
						"Starting reconnect in the role of the sender",
						false,
						platform.getSelfId().getIdAsInt(),
						oid,
						stateWrapper.get().getLastRoundReceived()).toString());

				ReconnectSender sender = new ReconnectSender(conn, stateWrapper.get());
				sender.execute();

				log.debug(RECONNECT.getMarker(), () -> new ReconnectFinishPayload(
						"Finished reconnect in the role of the sender.",
						false,
						platform.getSelfId().getIdAsInt(),
						oid,
						stateWrapper.get().getLastRoundReceived()));
			}

			return true;
		} else { // b is neither a heartbeat, a commStateRequest nor a commSyncRequest, so it's an error
			log.debug(SYNC_START.getMarker(),
					"listener {} received sync byte {} (should be {} or {}) from {}",
					selfId, b, SyncConstants.commSyncRequest, SyncConstants.heartbeat, otherId);
			conn.disconnect(false, 8);
			return false;
		}
	}
}
