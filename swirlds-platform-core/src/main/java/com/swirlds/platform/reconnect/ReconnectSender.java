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

package com.swirlds.platform.reconnect;

import com.swirlds.common.merkle.io.MerkleDataInputStream;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import com.swirlds.common.merkle.synchronization.SendingSynchronizer;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.SyncConstants;
import com.swirlds.platform.state.SignedState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.ExecutionException;

import static com.swirlds.logging.LogMarker.RECONNECT;

/**
 * This class encapsulates reconnect logic for the up to date node which is
 * helping an out of date node obtain a recent state.
 */
public class ReconnectSender {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	private SyncConnection connection;
	private SignedState signedState;

	/**
	 * After reconnect is finished, restore the socket timeout to the original value.
	 */
	private int originalSocketTimeout;

	/**
	 * Increase the socket timeout dramatically during reconnect.
	 */
	protected static final int SOCKET_TIMEOUT_MILLISECONDS = 1000 * 10;

	public ReconnectSender(SyncConnection connection, SignedState signedState) {
		this.connection = connection;
		this.signedState = signedState;
	}

	private void increaseSocketTimeout() throws ReconnectException {
		try {
			originalSocketTimeout = connection.getSocket().getSoTimeout();
			connection.getSocket().setSoTimeout(SOCKET_TIMEOUT_MILLISECONDS);
		} catch (SocketException e) {
			throw new ReconnectException(e);
		}
	}

	private void resetSocketTimeout() throws ReconnectException {
		try {
			connection.getSocket().setSoTimeout(originalSocketTimeout);
		} catch (SocketException e) {
			throw new ReconnectException(e);
		}
	}

	/**
	 * Perform the reconnect operation.
	 */
	public void execute() throws ReconnectException {
		increaseSocketTimeout();
		try {
			confirmReadinessForReconnect();
			reconnect();
			sendSignatures();
		} catch (IOException | ExecutionException | InterruptedException e) {
			throw new ReconnectException(e);
		} finally {
			resetSocketTimeout();
		}
	}

	/**
	 * Write a flag to the stream. Useful sanity check to ensure that streams are aligned.
	 */
	private void confirmReadinessForReconnect() throws IOException {
		log.debug(RECONNECT.getMarker(), "Got commStateRequest from {}, preparing for synchronization.",
				connection.getOtherId());
		connection.getDos().write(SyncConstants.commStateAck);
	}

	/**
	 * Copy the signed state from this node to the other node.
	 */
	private void reconnect() throws ExecutionException, InterruptedException {
		log.debug(RECONNECT.getMarker(), "Starting synchronization in the role of the sender.");

		SendingSynchronizer synchronizer = new SendingSynchronizer(
				new MerkleDataInputStream(connection.getDis(), false),
				new MerkleDataOutputStream(connection.getDos(), false),
				signedState, log, RECONNECT.getMarker());

		synchronizer.synchronize();

		log.debug(RECONNECT.getMarker(), "Finished synchronization in the role of the sender.");
	}

	/**
	 * Copy the signatures on the signed state from this node to the other node.
	 */
	private void sendSignatures() throws IOException {
		log.debug(RECONNECT.getMarker(), "Sending state signatures.");
		signedState.getSigSet().serialize(connection.getDos());
		connection.getDos().flush();
	}
}
