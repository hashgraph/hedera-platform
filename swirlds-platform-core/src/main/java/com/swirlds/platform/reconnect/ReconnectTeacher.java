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

package com.swirlds.platform.reconnect;

import com.swirlds.common.merkle.io.MerkleDataInputStream;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import com.swirlds.common.merkle.synchronization.TeachingSynchronizer;
import com.swirlds.logging.payloads.ReconnectFinishPayload;
import com.swirlds.logging.payloads.ReconnectStartPayload;
import com.swirlds.platform.ReconnectStatistics;
import com.swirlds.platform.SocketSyncConnection;
import com.swirlds.platform.sync.SyncConstants;
import com.swirlds.platform.state.SignedState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.SocketException;

import static com.swirlds.logging.LogMarker.RECONNECT;

/**
 * This class encapsulates reconnect logic for the up to date node which is
 * helping an out of date node obtain a recent state.
 */
public class ReconnectTeacher {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger(ReconnectTeacher.class);

	private final SocketSyncConnection connection;
	private final SignedState signedState;
	private final int reconnectSocketTimeout;

	private final long selfId;
	private final long otherId;
	private final long lastRoundReceived;

	private final ReconnectThrottle reconnectThrottle;

	private final ReconnectStatistics statistics;

	/**
	 * After reconnect is finished, restore the socket timeout to the original value.
	 */
	private int originalSocketTimeout;

	private boolean stateIsReleased;

	public ReconnectTeacher(
			final SocketSyncConnection connection,
			final SignedState signedState,
			final int reconnectSocketTimeout,
			final ReconnectThrottle reconnectThrottle,
			final long selfId,
			final long otherId,
			final long lastRoundReceived,
			final ReconnectStatistics statistics) {

		this.connection = connection;
		this.signedState = signedState;
		this.reconnectSocketTimeout = reconnectSocketTimeout;
		this.reconnectThrottle = reconnectThrottle;

		this.selfId = selfId;
		this.otherId = otherId;
		this.lastRoundReceived = lastRoundReceived;
		this.statistics = statistics;
	}

	/**
	 * increase socketTimout before performing reconnect
	 *
	 * @throws ReconnectException
	 * 		thrown when there is an error in the underlying protocol
	 */
	private void increaseSocketTimeout() throws ReconnectException {
		try {
			originalSocketTimeout = connection.getSocket().getSoTimeout();
			connection.getSocket().setSoTimeout(reconnectSocketTimeout);
		} catch (final SocketException e) {
			throw new ReconnectException(e);
		}
	}

	/**
	 * Reset socketTimeout to original value
	 *
	 * @throws ReconnectException
	 * 		thrown when there is an error in the underlying protocol
	 */
	private void resetSocketTimeout() throws ReconnectException {
		if (!connection.connected()) {
			LOG.debug(RECONNECT.getMarker(),
					"{} connection to {} is no longer connected. Returning.",
					connection.getSelfId(), connection.getOtherId());
			return;
		}

		try {
			connection.getSocket().setSoTimeout(originalSocketTimeout);
		} catch (final SocketException e) {
			throw new ReconnectException(e);
		}
	}

	/**
	 * Perform the reconnect operation.
	 *
	 * @throws ReconnectException
	 * 		thrown when current thread is interrupted, or when any I/O related errors occur,
	 * 		or when there is an error in the underlying protocol
	 */
	public void execute() throws ReconnectException {
		try {
			executeInternal();
		} finally {
			if (!stateIsReleased) {
				// If execution aborted without finishing the reconnect, ensure the state is properly released
				signedState.weakReleaseState();
			}
		}
	}

	private void executeInternal() {
		// If the connection object to be used here has been disconnected on another thread, we can
		// not reconnect with this connection.
		if (!connection.connected()) {
			LOG.debug(RECONNECT.getMarker(),
					"{} connection to {} is no longer connected. Returning.",
					connection.getSelfId(), connection.getOtherId());
			return;
		}
		if (reconnectThrottle.initiateReconnect(otherId)) {
			logReconnectStart();
			increaseSocketTimeout();

			try {
				confirmReconnect();
				reconnect();
				sendSignatures();
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new ReconnectException(e);
			} catch (final IOException e) {
				throw new ReconnectException(e);
			} finally {
				resetSocketTimeout();
				reconnectThrottle.markReconnectFinished();
			}
			logReconnectFinish();
		} else {
			try {
				denyReconnect();
			} catch (final IOException e) {
				throw new ReconnectException(e);
			}
		}
	}

	/**
	 * Write a flag to the stream. Informs the receiver that reconnect will proceed.
	 *
	 * @throws IOException
	 * 		thrown when any I/O related errors occur
	 */
	private void confirmReconnect() throws IOException {
		connection.getDos().write(SyncConstants.COMM_STATE_ACK);
		connection.getDos().flush();
	}

	/**
	 * Write a flag to the stream. Informs the receiver that reconnect will not proceed.
	 *
	 * @throws IOException
	 * 		thrown when any I/O related errors occur
	 */
	private void denyReconnect() throws IOException {
		connection.getDos().write(SyncConstants.COMM_STATE_NACK);
		connection.getDos().flush();
	}

	private void logReconnectStart() {
		LOG.info(RECONNECT.getMarker(), () -> new ReconnectStartPayload(
				"Starting reconnect in the role of the sender",
				false,
				selfId,
				otherId,
				lastRoundReceived));
	}

	private void logReconnectFinish() {
		LOG.info(RECONNECT.getMarker(), () -> new ReconnectFinishPayload(
				"Finished reconnect in the role of the sender.",
				false,
				selfId,
				otherId,
				lastRoundReceived));
	}

	/**
	 * Copy the signed state from this node to the other node.
	 *
	 * @throws InterruptedException
	 * 		thrown if the current thread is interrupted
	 */
	private void reconnect() throws InterruptedException {
		LOG.info(RECONNECT.getMarker(), "Starting synchronization in the role of the sender.");
		statistics.incrementSenderStartTimes();

		final TeachingSynchronizer synchronizer = new TeachingSynchronizer(
				new MerkleDataInputStream(connection.getDis()),
				new MerkleDataOutputStream(connection.getDos()).setExternal(false),
				signedState.getState(),
				() -> connection.disconnect(false, 0));

		// State is acquired via SignedStateManager.getLastCompleteSignedState(), which acquires
		// a weak reservation. The synchronizer manually acquires references to merkle nodes in the
		// state within its constructor. After that has completed, the lock on the signed state itself
		// is no longer required to be held.
		stateIsReleased = true;
		signedState.weakReleaseState();

		synchronizer.synchronize();

		statistics.incrementSenderEndTimes();
		LOG.info(RECONNECT.getMarker(), "Finished synchronization in the role of the sender.");
	}

	/**
	 * Copy the signatures on the signed state from this node to the other node.
	 *
	 * @throws IOException
	 * 		thrown when any I/O related errors occur
	 */
	private void sendSignatures() throws IOException {
		LOG.info(RECONNECT.getMarker(), "Sending state signatures.");
		signedState.getSigSet().serialize(connection.getDos());
		connection.getDos().flush();
	}
}
