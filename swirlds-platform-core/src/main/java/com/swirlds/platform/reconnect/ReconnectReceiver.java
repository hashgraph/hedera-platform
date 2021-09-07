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

import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.io.BadIOException;
import com.swirlds.common.io.extendable.CountingStreamExtension;
import com.swirlds.common.io.extendable.ExtendableInputStream;
import com.swirlds.common.merkle.io.MerkleDataInputStream;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import com.swirlds.common.merkle.synchronization.ReceivingSynchronizer;
import com.swirlds.logging.payloads.ReconnectDataUsagePayload;
import com.swirlds.logging.payloads.ReconnectFailurePayload;
import com.swirlds.platform.AbstractPlatform;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.SyncConstants;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.state.SigInfo;
import com.swirlds.platform.state.SigSet;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.SocketException;
import java.security.PublicKey;
import java.util.concurrent.Future;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;

/**
 * This class encapsulates reconnect logic for the out of date node which is
 * requesting a recent state from another node.
 */
public class ReconnectReceiver {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	private final SyncConnection connection;
	private final AddressBook addressBook;
	private final Crypto crypto;

	private State currentState;
	private final int reconnectSocketTimeout;

	private SignedState signedState;

	/**
	 * After reconnect is finished, restore the socket timeout to the original value.
	 */
	private int originalSocketTimeout;

	public ReconnectReceiver(
			final SyncConnection connection,
			final AddressBook addressBook,
			final State currentState,
			final Crypto crypto,
			final int reconnectSocketTimeout) {

		currentState.throwIfImmutable("Can not perform reconnect with immutable state");
		currentState.throwIfReleased("Can not perform reconnect with released state");

		this.connection = connection;
		this.addressBook = addressBook;
		this.currentState = currentState;
		this.crypto = crypto;
		this.reconnectSocketTimeout = reconnectSocketTimeout;
	}

	/**
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
	 * @return true if the other node is willing to assist with reconnect, otherwise false
	 * @throws ReconnectException
	 * 		thrown if I/O related errors occur, or when there is an error in the underlying protocol
	 */
	public boolean execute() throws ReconnectException {
		// If the connection object to be used here has been disconnected on another thread, we can
		// not reconnect with this connection.
		if (!connection.connected()) {
			log.debug(RECONNECT.getMarker(),
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

		} catch (IOException e) {
			throw new ReconnectException(e);
		} catch (InterruptedException e) {
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
		NodeId otherId = connection.getOtherId();
		SyncInputStream dis = connection.getDis();
		SyncOutputStream dos = connection.getDos();
		AbstractPlatform platform = connection.getPlatform();

		// send the request
		dos.write(SyncConstants.COMM_STATE_REQUEST);
		dos.flush();
		log.info(RECONNECT.getMarker(), "Requesting to reconnect with node {}.", otherId);

		// read the response
		byte stateResponse = dis.readByte();
		if (stateResponse == SyncConstants.COMM_STATE_ACK) {
			log.info(RECONNECT.getMarker(),
					"Node {} is willing to help this node to reconnect.", otherId);
			return true;
		} else if (stateResponse == SyncConstants.COMM_STATE_NACK) {
			log.info(RECONNECT.getMarker(),
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
		ExtendableInputStream<CountingStreamExtension> countingStream =
				new ExtendableInputStream<>(connection.getDis(), new CountingStreamExtension());
		MerkleDataInputStream in = new MerkleDataInputStream(countingStream, false);
		MerkleDataOutputStream out = new MerkleDataOutputStream(connection.getDos(), false);

		ReceivingSynchronizer synchronizer = new ReceivingSynchronizer(in, out,
				currentState, log, RECONNECT.getMarker());

		final State state = (State) synchronizer.synchronize();
		signedState = new SignedState(state);

		double mbReceived = countingStream.getExtension().getCount() / 1024.0 / 1024.0;
		log.info(RECONNECT.getMarker(), () -> new ReconnectDataUsagePayload(
				"Reconnect data usage report",
				mbReceived).toString());
	}

	/**
	 * Validate that the state matches the signatures.
	 *
	 * @throws ReconnectException
	 * 		thrown when received signed state does not have enough valid signatures
	 */
	private void validate() throws ReconnectException {

		// validate the signatures from the received state
		log.info(RECONNECT.getMarker(), "Validating signatures of the received state");
		int numSigs = signedState.getSigSet().getNumMembers();
		Future<Boolean>[] validFutures = new Future[numSigs];
		for (int i = 0; i < numSigs; i++) {
			PublicKey key = addressBook.getAddress(i).getSigPublicKey();
			SigInfo sigInfo = signedState.getSigSet().getSigInfo(i);
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
			} catch (Exception e) {
				log.info(EXCEPTION.getMarker(), "Error while validating signature from received state: ", e);
			}
		}

		log.info(RECONNECT.getMarker(), "Signed State valid Stake :{} ", validStake);
		log.info(RECONNECT.getMarker(), "AddressBook Stake :{} ", addressBook.getTotalStake());
		log.info(RECONNECT.getMarker(), "StrongMinority status: {}",
				Utilities.isStrongMinority(validStake, addressBook.getTotalStake()));
		if (!Utilities.isStrongMinority(validStake, addressBook.getTotalStake())) {
			throw new ReconnectException(String.format(
					"Error: Received signed state does not have enough valid signatures! validCount:%d, addressBook " +
							"size:%d, validStake:%d, addressBook total stake:%d",
					validCount, addressBook.getSize(), validStake, addressBook.getTotalStake()));
		}

		log.info(RECONNECT.getMarker(), "State is valid");
	}

	/**
	 * Copy the signatures for the state from the other node.
	 *
	 * @throws IOException
	 * 		if any I/O related errors occur
	 */
	private void receiveSignatures() throws IOException {
		log.info(RECONNECT.getMarker(), "Receiving signed state signatures");
		SigSet sigSet = new SigSet(addressBook);
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
