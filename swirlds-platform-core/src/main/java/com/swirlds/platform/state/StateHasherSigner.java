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

package com.swirlds.platform.state;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.stats.EventFlowStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.swirlds.common.Units.NANOSECONDS_TO_SECONDS;
import static com.swirlds.logging.LogMarker.SIGNED_STATE;
import static com.swirlds.platform.system.Fatal.fatalError;

/**
 * Hashes a signed state and passes it on for signature collection.
 */
public final class StateHasherSigner {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/** An observer of signed states */
	private final SignedStateSignatureCollector signedStateSigCollector;

	/** Used for hashing the signed state */
	private final Cryptography cryptography;

	private final EventFlowStats stats;

	public StateHasherSigner(final SignedStateSignatureCollector signedStateSigCollector,
			final EventFlowStats stats) {
		this.signedStateSigCollector = signedStateSigCollector;
		this.stats = stats;
		cryptography = CryptoFactory.getInstance();
	}

	/**
	 * Hash a new signed state, signed only by self so far, create a transaction with self signature (and gossip to
	 * other members), and start collecting signatures on it from other members.
	 *
	 * @param signedState
	 * 		the state to hash and gather signatures for
	 * @throws InterruptedException
	 * 		if this thread is interrupted while waiting for state hashing to complete
	 */
	public void hashAndCollectSignatures(final SignedState signedState) throws InterruptedException {
		log.info(SIGNED_STATE.getMarker(), "stateHashSign:: about to hash and sign the state");

		final long startTime = System.nanoTime();
		log.info(LogMarker.MERKLE_HASHING.getMarker(), "Starting hashing of SignedState");

		// Use digestTreeAsync because it is significantly (10x+) faster for large trees
		final Future<Hash> hashFuture = cryptography.digestTreeAsync(signedState.getState());
		// wait for the hash to be computed
		try {
			hashFuture.get();
		} catch (final ExecutionException ex) {
			fatalError("Exception occurred during SignedState hashing", ex);
		}

		log.info(LogMarker.MERKLE_HASHING.getMarker(), "Done hashing SignedState, starting newSelfSigned");
		signedStateSigCollector.collectSignatures(signedState);
		log.info(LogMarker.MERKLE_HASHING.getMarker(), "Done newSelfSigned");

		stats.recordNewSignedStateTime((System.nanoTime() - startTime) * NANOSECONDS_TO_SECONDS);
	}
}
