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

import com.swirlds.logging.payloads.IssPayload;
import com.swirlds.logging.payloads.IssResolvedPayload;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.stats.IssStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static com.swirlds.common.merkle.hash.MerkleHashChecker.generateHashDebugString;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;

/**
 * This class is responsible for producing log output about ISS events.
 */
public class IssLogger {

	private static final Logger LOG = LogManager.getLogger(IssLogger.class);

	/**
	 * For each other node, the highest round number that has been signed.
	 */
	private final Map<Long, Long> highestRoundSigned = new HashMap<>();

	/**
	 * For each other node, this map holds true if that node's recent signatures are valid.
	 * If false then there is considered to be an ISS between this node and the other node.
	 */
	private final Map<Long, Boolean> signatureValidity = new HashMap<>();

	/**
	 * The number of nodes currently in an ISS state with this node.
	 */
	private int issCount;

	/**
	 * The ID of this node.
	 */
	private final long selfId;

	/**
	 * Allows extra debug info to be logged the first time a node encounters an ISS.
	 */
	private boolean firstISSLogged = false;

	/**
	 * Statistics for ISS events.
	 */
	private final IssStats stats;

	/**
	 * Create an object that manages ISS logging.
	 *
	 * @param selfId
	 * 		the ID of this node
	 */
	public IssLogger(final long selfId, final IssStats stats) {
		this.selfId = selfId;
		this.stats = stats;
	}

	/**
	 * This method should be called for every signature received (both valid and invalid)
	 *
	 * @param signedState
	 * 		the state that was signed
	 * @param signerId
	 * 		the ID of the signer
	 * @param isValid
	 * 		true if the signature is valid, otherwise false
	 */
	public void reportSignature(final SignedState signedState, final long signerId, final boolean isValid) {
		final long previousRound = highestRoundSigned.getOrDefault(signerId, -1L);
		final long signedRound = signedState.getState().getPlatformState().getRound();

		if (signedRound <= previousRound) {
			// Don't log information for a round that was initially skipped
			return;
		}

		highestRoundSigned.put(signerId, signedRound);

		final boolean previouslyValid = signatureValidity.getOrDefault(signerId, true);
		if (isValid != previouslyValid) {
			signatureValidity.put(signerId, isValid);
			if (isValid) {
				issCount--;
				logIssResolution(signedState, signerId);
			} else {
				issCount++;
				logIss(signedState, signerId);
			}
			stats.setIssCount(issCount);
		}
	}

	/**
	 * Called when a node first enters into an ISS state with another node.
	 *
	 * @param signedState
	 * 		the signed state that received an invalid signature
	 * @param signerId
	 * 		the ID of the node that sent the invalid signature
	 */
	private void logIss(final SignedState signedState, final long signerId) {
		final String message = "Received an invalid state signature! (┛ಠ_ಠ)┛彡┻━┻\n" +
				generateHashDebugString(signedState.getState(), StateSettings.getDebugHashDepth());

		LOG.error(EXCEPTION.getMarker(), new IssPayload(
				message, signedState.getState().getPlatformState().getRound(), selfId, signerId));

		if (!firstISSLogged) {
			firstISSLogged = true;

			LOG.error(EXCEPTION.getMarker(), "{}", () -> {
				StringBuilder sb = new StringBuilder();
				for (EventImpl event : signedState.getEvents()) {
					sb.append(event.toMediumString());
					sb.append('\n');
				}
				return sb;
			});
		}
	}

	/**
	 * Called when a node exits from an ISS state with another node.
	 *
	 * @param signedState
	 * 		the signed state that received a valid signature
	 * @param signerId
	 * 		the ID of the node that is no longer in an ISS state
	 */
	private void logIssResolution(final SignedState signedState, final long signerId) {
		final String message = "Now receiving valid state signatures. ┳━┳ ヽ(ಠل͜ಠ)ﾉ";

		LOG.info(STARTUP.getMarker(), new IssResolvedPayload(
				message, signedState.getState().getPlatformState().getRound(), selfId, signerId));
	}
}
