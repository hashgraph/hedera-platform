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

package com.swirlds.platform.reconnect;

import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.io.BadIOException;
import com.swirlds.common.merkle.io.MerkleDataInputStream;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import com.swirlds.common.merkle.synchronization.LearningSynchronizer;
import com.swirlds.logging.payloads.ReconnectDataUsagePayload;
import com.swirlds.logging.payloads.ReconnectFailurePayload;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.ReconnectStatistics;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.network.ByteConstants;
import com.swirlds.platform.state.SigInfo;
import com.swirlds.platform.state.SigSet;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.SocketException;
import java.security.PublicKey;
import java.util.concurrent.Future;

import static com.swirlds.common.merkle.hash.MerkleHashChecker.generateHashDebugString;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.platform.state.PlatformState.getInfoString;

/**
 * This class encapsulates reconnect logic for the out of date node which is
 * requesting a recent state from another node.
 */
public class ReconnectLearner {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger(ReconnectLearner.class);

	private final SyncConnection connection;
	private final AddressBook addressBook;
	private final Crypto crypto;

	private final State currentState;
	private final int reconnectSocketTimeout;

	private SignedState signedState;

	private final ReconnectStatistics statistics;

	/**
	 * After reconnect is finished, restore the socket timeout to the original value.
	 */
	private int originalSocketTimeout;

	public ReconnectLearner(
			final SyncConnection connection,
			final AddressBook addressBook,
			final State currentState,
			final Crypto crypto,
			final int reconnectSocketTimeout,
			final ReconnectStatistics statistics) {

		currentState.throwIfImmutable("Can not perform reconnect with immutable state");
		currentState.throwIfReleased("Can not perform reconnect with released state");

		this.connection = connection;
		this.addressBook = addressBook;
		this.currentState = currentState;
		this.crypto = crypto;
		this.reconnectSocketTimeout = reconnectSocketTimeout;
		this.statistics = statistics;
	}

	/**
	 * @throws ReconnectException
	 * 		thrown when there is an error in the underlying protocol
	 */
	private void increaseSocketTimeout() throws ReconnectException {
		try {
			originalSocketTimeout = connection.getTimeout();
			connection.setTimeout(reconnectSocketTimeout);
		} catch (final SocketException e) {
			throw new ReconnectException(e);
		}
	}

	/**
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
			connection.setTimeout(originalSocketTimeout);
		} catch (final SocketException e) {
			throw new ReconnectException(e);
		}
	}

	/**
	 * Perform the reconnect operation.
	 *
	 * @return true if the other node is willing to assist with reconnect, otherwise false
	 * @throws ReconnectException
	 * 		thrown if I/O related errors occur, or when there is an error in the underlying protocol
	 */
	public boolean execute() throws ReconnectException {
		// If the connection object to be used here has been disconnected on another thread, we can
		// not reconnect with this connection.
		if (!connection.connected()) {
			LOG.debug(RECONNECT.getMarker(),
					"{} connection to {} is no longer connected. Returning.",
					connection.getSelfId(), connection.getOtherId());
			return false;
		}

		increaseSocketTimeout();
		try {
			if (!isNodeReadyForReconnect()) {
				return false;
			}

			reconnect();
			receiveSignatures();
			validate();

		} catch (final IOException e) {
			throw new ReconnectException(e);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			resetSocketTimeout();
		}
		return true;
	}

	/**
	 * Validate that the other node is willing to reconnect with us.
	 *
	 * @return true if the other node is willing to assist with reconnect
	 * @throws IOException
	 * 		thrown when any I/O related errors occur
	 * @throws ReconnectException
	 * 		thrown when the other node is unwilling to reconnect right now
	 */
	private boolean isNodeReadyForReconnect() throws IOException, ReconnectException {
		final NodeId otherId = connection.getOtherId();
		final SyncInputStream dis = connection.getDis();
		final SyncOutputStream dos = connection.getDos();

		// send the request
		dos.write(ByteConstants.COMM_STATE_REQUEST);
		dos.flush();
		LOG.info(RECONNECT.getMarker(), "Requesting to reconnect with node {}.", otherId);

		// read the response
		final byte stateResponse = dis.readByte();
		if (stateResponse == ByteConstants.COMM_STATE_ACK) {
			LOG.info(RECONNECT.getMarker(),
					"Node {} is willing to help this node to reconnect.", otherId);
			return true;
		} else if (stateResponse == ByteConstants.COMM_STATE_NACK) {
			LOG.info(RECONNECT.getMarker(),
					new ReconnectFailurePayload("Node " + otherId
							+ " is unwilling to help this node to reconnect.",
							ReconnectFailurePayload.CauseOfFailure.REJECTION));
			return false;
		} else {
			throw new BadIOException("COMM_STATE_REQUEST was sent but reply was " + stateResponse +
					" instead of COMM_STATE_ACK or COMM_STATE_NACK");
		}
	}

	/**
	 * Get a copy of the state from the other node.
	 *
	 * @throws InterruptedException
	 * 		if the current thread is interrupted
	 */
	private void reconnect() throws InterruptedException {
		statistics.incrementReceiverStartTimes();

		MerkleDataInputStream in = new MerkleDataInputStream(connection.getDis());
		MerkleDataOutputStream out = new MerkleDataOutputStream(connection.getDos());

		connection.getDis().getSyncByteCounter().resetCount();
		connection.getDos().getSyncByteCounter().resetCount();

		final LearningSynchronizer synchronizer = new LearningSynchronizer(in, out, currentState,
				connection::disconnect);
		synchronizer.synchronize();

		final State state = (State) synchronizer.getRoot();
		signedState = new SignedState(state);

		final double mbReceived = connection.getDis().getSyncByteCounter().getMebiBytes();
		LOG.info(RECONNECT.getMarker(), () -> new ReconnectDataUsagePayload(
				"Reconnect data usage report",
				mbReceived).toString());

		statistics.incrementReceiverEndTimes();
	}

	/**
	 * Validate that the state matches the signatures.
	 *
	 * @throws ReconnectException
	 * 		thrown when received signed state does not have enough valid signatures
	 */
	private void validate() throws ReconnectException {

		// validate the signatures from the received state
		LOG.info(RECONNECT.getMarker(), "Validating signatures of the received state");
		final int numSigs = signedState.getSigSet().getNumMembers();
		final Future<Boolean>[] validFutures = new Future[numSigs];
		for (int i = 0; i < numSigs; i++) {
			final PublicKey key = addressBook.getAddress(i).getSigPublicKey();
			final SigInfo sigInfo = signedState.getSigSet().getSigInfo(i);
			if (sigInfo == null) {
				continue;
			}
			validFutures[i] = crypto.verifySignatureParallel(
					signedState.getStateHashBytes(),
					sigInfo.getSig(),
					key,
					(Boolean b) -> {
					});
		}
		int validCount = 0; //how many valid signatures
		long validStake = 0; //how much stake owned by members with valid signatures
		for (int i = 0; i < numSigs; i++) {
			if (validFutures[i] == null) {
				continue;
			}
			try {
				if (validFutures[i].get()) {
					validCount++;
					validStake += signedState.getAddressBook().getStake(i);
				}
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				LOG.warn(RECONNECT.getMarker(), "interrupted while validating signatures");
			} catch (final Exception e) {
				LOG.info(EXCEPTION.getMarker(), "Error while validating signature from received state: ", e);
			}
		}

		LOG.info(RECONNECT.getMarker(), "Signed State valid Stake :{} ", validStake);
		LOG.info(RECONNECT.getMarker(), "AddressBook Stake :{} ", addressBook.getTotalStake());
		LOG.info(RECONNECT.getMarker(), "StrongMinority status: {}",
				Utilities.isStrongMinority(validStake, addressBook.getTotalStake()));
		if (!Utilities.isStrongMinority(validStake, addressBook.getTotalStake())) {
			LOG.error(RECONNECT.getMarker(), "Information for failed reconnect state:\n{}\n{}",
					() -> getInfoString(signedState.getState().getPlatformState()),
					() -> generateHashDebugString(signedState.getState(), StateSettings.getDebugHashDepth()));
			throw new ReconnectException(String.format(
					"Error: Received signed state does not have enough valid signatures! validCount:%d, addressBook " +
							"size:%d, validStake:%d, addressBook total stake:%d",
					validCount, addressBook.getSize(), validStake, addressBook.getTotalStake()));
		}

		LOG.info(RECONNECT.getMarker(), "State is valid");
	}

	/**
	 * Copy the signatures for the state from the other node.
	 *
	 * @throws IOException
	 * 		if any I/O related errors occur
	 */
	private void receiveSignatures() throws IOException {
		LOG.info(RECONNECT.getMarker(), "Receiving signed state signatures");
		final SigSet sigSet = new SigSet(addressBook);
		sigSet.deserialize(connection.getDis(), sigSet.getVersion());
		signedState.setSigSet(sigSet);
	}

	/**
	 * Get the signed state that was copied from the other node.
	 */
	public SignedState getSignedState() {
		return signedState;
	}
}
