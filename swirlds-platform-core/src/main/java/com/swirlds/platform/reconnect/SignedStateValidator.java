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

import com.swirlds.common.system.AddressBook;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.state.SigInfo;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.StateSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.PublicKey;
import java.util.concurrent.Future;

import static com.swirlds.common.merkle.hash.MerkleHashChecker.generateHashDebugString;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.platform.state.PlatformState.getInfoString;

/**
 * Validates a signed state by summing the amount of stake held by the valid signatures on the state.
 */
public class SignedStateValidator {

	private final Crypto crypto;

	public SignedStateValidator(final Crypto crypto) {
		this.crypto = crypto;
	}

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger(SignedStateValidator.class);

	/**
	 * Determines if a signed state is valid. If the total stake among valid signatures is equal to or greater than a
	 * strong minority, then the signed state is valid. If the state is not valid, an exception is thrown.
	 *
	 * @param signedState
	 * 		the signed state to validate
	 * @param addressBook
	 * 		the address book to use to lookup public keys and stake values
	 * @throws ReconnectException
	 * 		if the state is not valid
	 */
	public void validate(final SignedState signedState, final AddressBook addressBook) throws ReconnectException {
		LOG.info(RECONNECT.getMarker(), "Validating signatures of the received state");

		final Future<Boolean>[] validFutures = getValidFutures(signedState, addressBook);

		int validCount = 0; //how many valid signatures
		long validStake = 0; //how much stake owned by members with valid signatures

		final int numSigs = signedState.getSigSet().getNumMembers();
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

		final boolean isStrongMinority = Utilities.isStrongMinority(validStake, addressBook.getTotalStake());

		LOG.info(RECONNECT.getMarker(), "Signed State valid Stake :{} ", validStake);
		LOG.info(RECONNECT.getMarker(), "AddressBook Stake :{} ", addressBook.getTotalStake());
		LOG.info(RECONNECT.getMarker(), "StrongMinority status: {}", isStrongMinority);
		if (!isStrongMinority) {
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
	 * Calculates the validity of each signature on the {@code signedState} and returns them as a list of {@link Future}
	 * objects.
	 *
	 * @param signedState
	 * 		the signed state to evaluate
	 * @param addressBook
	 * 		the address book to use for signature verification
	 * @return a list of {@link Future}s indicating the validity of each signature
	 */
	private Future<Boolean>[] getValidFutures(final SignedState signedState, final AddressBook addressBook) {
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
		return validFutures;
	}

}
