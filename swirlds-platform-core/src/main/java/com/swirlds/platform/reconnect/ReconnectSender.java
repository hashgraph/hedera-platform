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

package com.swirlds.platform.reconnect;

import com.swirlds.common.merkle.io.MerkleDataInputStream;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import com.swirlds.common.merkle.synchronization.SendingSynchronizer;
import com.swirlds.logging.payloads.ReconnectFinishPayload;
import com.swirlds.logging.payloads.ReconnectStartPayload;
import com.swirlds.platform.ReconnectStatistics;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.SyncConstants;
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
public class ReconnectSender {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	private final SyncConnection connection;
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


	public ReconnectSender(
			final SyncConnection connection,
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
		} catch (SocketException e) {
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
			log.debug(RECONNECT.getMarker(),
					"{} connection to {} is no longer connected. Returning.",
					connection.getSelfId(), connection.getOtherId());
			return;
		}

		try {
			connection.getSocket().setSoTimeout(originalSocketTimeout);
		} catch (SocketException e) {
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
		// If the connection object to be used here has been disconnected on another thread, we can
		// not reconnect with this connection.
		if (!connection.connected()) {
			log.debug(RECONNECT.getMarker(),
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
		log.info(RECONNECT.getMarker(), () -> new ReconnectStartPayload(
				"Starting reconnect in the role of the sender",
				false,
				selfId,
				otherId,
				lastRoundReceived));
	}

	private void logReconnectFinish() {
		log.info(RECONNECT.getMarker(), () -> new ReconnectFinishPayload(
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
		log.info(RECONNECT.getMarker(), "Starting synchronization in the role of the sender.");
		statistics.incrementSenderStartTimes();
		SendingSynchronizer synchronizer = new SendingSynchronizer(
				new MerkleDataInputStream(connection.getDis(), false),
				new MerkleDataOutputStream(connection.getDos(), false),
				signedState.getState(), log, RECONNECT.getMarker());

		synchronizer.synchronize();

		statistics.incrementSenderEndTimes();
		log.info(RECONNECT.getMarker(), "Finished synchronization in the role of the sender.");
	}

	/**
	 * Copy the signatures on the signed state from this node to the other node.
	 *
	 * @throws IOException
	 * 		thrown when any I/O related errors occur
	 */
	private void sendSignatures() throws IOException {
		log.info(RECONNECT.getMarker(), "Sending state signatures.");
		signedState.getSigSet().serialize(connection.getDos());
		connection.getDos().flush();
	}
}
