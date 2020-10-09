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

import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.io.BadIOException;
import com.swirlds.common.io.extendable.CountingStreamExtension;
import com.swirlds.common.io.extendable.ExtendableInputStream;
import com.swirlds.common.merkle.MerkleUtils;
import com.swirlds.common.merkle.io.MerkleDataInputStream;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import com.swirlds.common.merkle.synchronization.ReceivingSynchronizer;
import com.swirlds.logging.payloads.ReconnectDataUsagePayload;
import com.swirlds.platform.AbstractPlatform;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.SyncConstants;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.state.SigInfo;
import com.swirlds.platform.state.SigSet;
import com.swirlds.platform.state.SignedState;
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
import static com.swirlds.platform.reconnect.ReconnectSender.SOCKET_TIMEOUT_MILLISECONDS;

/**
 * This class encapsulates reconnect logic for the out of date node which is
 * requesting a recent state from another node.
 */
public class ReconnectReceiver {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	private SyncConnection connection;
	private AddressBook addressBook;
	private Crypto crypto;

	private SignedState currentState;
	private SignedState signedState;

	/**
	 * After reconnect is finished, restore the socket timeout to the original value.
	 */
	private int originalSocketTimeout;

	public ReconnectReceiver(SyncConnection connection, AddressBook addressBook, SignedState currentState,
			Crypto crypto) {
		this.connection = connection;
		this.addressBook = addressBook;
		this.currentState = currentState;
		this.crypto = crypto;
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
			assertNodeIsReadyForReconnect();
			reconnect();

			MerkleUtils.rehashTree(signedState);

			receiveSignatures();
			validate();
		} catch (IOException e) {
			throw new ReconnectException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			resetSocketTimeout();
		}
	}

	/**
	 * Validate that the other node is willing to reconnect with us.
	 */
	private void assertNodeIsReadyForReconnect() throws IOException, ReconnectException {
		NodeId otherId = connection.getOtherId();
		NodeId selfId = connection.getSelfId();
		SyncInputStream dis = connection.getDis();
		SyncOutputStream dos = connection.getDos();
		AbstractPlatform platform = connection.getPlatform();

		// send the request
		dos.write(SyncConstants.commStateRequest);
		dos.flush();
		log.debug(RECONNECT.getMarker(), "{} sent commStateRequest to {}", selfId, otherId);

		// read the response
		byte stateResponse = dis.readByte();
		if (stateResponse == SyncConstants.commStateAck) {
			log.debug(RECONNECT.getMarker(), "{} got commStateAck from {}", platform.getSelfId(), otherId);
		} else if (stateResponse == SyncConstants.commStateNack) {
			throw new ReconnectException("Node is unwilling to reconnect right now.");
		} else {
			throw new BadIOException("commStateRequest was sent but reply was " + stateResponse +
					" instead of commStateAck or commStateNack");
		}
	}

	/**
	 * Get a copy of the state from the other node.
	 */
	private void reconnect() throws InterruptedException {
		ExtendableInputStream<CountingStreamExtension> countingStream =
				new ExtendableInputStream<>(connection.getDis(), new CountingStreamExtension());
		MerkleDataInputStream in = new MerkleDataInputStream(countingStream, false);
		MerkleDataOutputStream out = new MerkleDataOutputStream(connection.getDos(), false);

		ReceivingSynchronizer synchronizer = new ReceivingSynchronizer(in, out,
				currentState, log, RECONNECT.getMarker());

		signedState = (SignedState) synchronizer.synchronize();

		double mbReceived = countingStream.getExtension().getCount() / 1024.0 / 1024.0;
		log.info(RECONNECT.getMarker(), () -> new ReconnectDataUsagePayload(
				"Reconnect data usage report",
				mbReceived).toString());
	}

	/**
	 * Validate that the state matches the signatures.
	 */
	private void validate() throws ReconnectException {

		// validate the signatures from the received state
		log.debug(RECONNECT.getMarker(), "Validating signatures of the received state");
		int numSigs = signedState.getSigSet().getNumMembers();
		Future<Boolean>[] validFutures = new Future[numSigs];
		for (int i = 0; i < numSigs; i++) {
			PublicKey key = addressBook.getAddress(i).getSigPublicKey();
			SigInfo sigInfo = signedState.getSigSet().getSigInfo(i);
			if (sigInfo == null) {
				continue;
			}
			validFutures[i] = crypto.verifySignatureParallel(
					signedState.getHashBytes(),
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

		log.debug(RECONNECT.getMarker(), "Signed State valid Stake :{} ", validStake);
		log.debug(RECONNECT.getMarker(), "AddressBook Stake :{} ", addressBook.getTotalStake());
		log.debug(RECONNECT.getMarker(), "StrongMinority status: {}",
				Utilities.isStrongMinority(validStake, addressBook.getTotalStake()));
		if (!Utilities.isStrongMinority(validStake, addressBook.getTotalStake())) {
			throw new ReconnectException(String.format(
					"Error: Received signed state does not have enough valid signatures! validCount:%d, addressBook " +
							"size:%d, validStake:%d, addressBook total stake:%d",
					validCount, addressBook.getSize(), validStake, addressBook.getTotalStake()));
		}

		log.debug(RECONNECT.getMarker(), "State is valid");
	}

	/**
	 * Copy the signatures for the state from the other node.
	 */
	private void receiveSignatures() throws IOException {
		log.debug(RECONNECT.getMarker(), "Receiving signed state signatures");
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
