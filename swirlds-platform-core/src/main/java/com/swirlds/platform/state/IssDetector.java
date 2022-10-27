/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.state;

import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.system.state.notifications.IssListener;
import com.swirlds.common.system.state.notifications.IssNotification;
import com.swirlds.logging.payloads.IssPayload;
import com.swirlds.logging.payloads.IssResolvedPayload;
import com.swirlds.platform.Settings;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.IssMetrics;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.swirlds.common.merkle.hash.MerkleHashChecker.generateHashDebugString;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;

/**
 * This class is responsible for producing log output about ISS events and for writing ISS states to disk.
 */
public class IssDetector {

	private static final Logger LOG = LogManager.getLogger(IssDetector.class);

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
	 * the last wall clock time that the a signed state was dumped to disk as a result of an ISS.
	 */
	private Instant lastISSDumpTimestamp;

	/**
	 * Statistics for ISS events.
	 */
	private final IssMetrics issMetrics;

	private final SignedStateFileManager signedStateFileManager;

	/**
	 * Create an object that manages ISS logging.
	 *
	 * @param selfId
	 * 		the ID of this node
	 * @param signedStateFileManager
	 * 		responsible for writing states to the disk
	 */
	public IssDetector(final long selfId, final IssMetrics issMetrics, final SignedStateFileManager signedStateFileManager) {
		this.selfId = selfId;
		this.issMetrics = issMetrics;
		this.signedStateFileManager = signedStateFileManager;
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
		final long signedRound = signedState.getState().getPlatformState().getPlatformData().getRound();

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
			issMetrics.setIssCount(issCount);
		}

		if (!isValid) {
			dumpIssState(signedState);

			final NotificationEngine notificationEngine = NotificationFactory.getEngine();

			final IssNotification notification = new IssNotification(
					signedState.getSwirldState(),
					signedState.getState().getPlatformDualState(),
					signerId,
					signedState.getRound(),
					signedState.getConsensusTimestamp());

			signedState.weakReserveState();
			notificationEngine.dispatch(IssListener.class, notification, result -> signedState.weakReleaseState());
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
				signedState.getState().getPlatformState().getInfoString() + "\n" +
				generateHashDebugString(signedState.getState(), StateSettings.getDebugHashDepth());

		LOG.error(EXCEPTION.getMarker(), new IssPayload(
				message, signedState.getState().getPlatformState().getPlatformData().getRound(), selfId, signerId));

		if (!firstISSLogged) {
			firstISSLogged = true;

			LOG.error(EXCEPTION.getMarker(), "{}", () -> {
				StringBuilder sb = new StringBuilder();
				for (EventImpl event : signedState.getEvents()) {
					sb.append(event.toMediumString());
					sb.append(" ct: ").append(event.getConsensusTimestamp());
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
				message, signedState.getState().getPlatformState().getPlatformData().getRound(), selfId, signerId));
	}

	/**
	 * <p>
	 * Writes a {@link SignedState} to disk if enabled via the {@link StateSettings#dumpStateOnISS} setting. This method
	 * will only write a {@link SignedState} to disk once every {@link StateSettings#secondsBetweenISSDumps} seconds
	 * based on previous executions.
	 * </p>
	 *
	 * <p>
	 * This method uses wall clock time on the local machine to control how frequently it writes {@link SignedState} to
	 * disk.
	 * </p>
	 *
	 * @param state
	 * 		the {@link SignedState} to be written to disk
	 */
	private void dumpIssState(final SignedState state) {
		if (!Settings.getInstance().getState().dumpStateOnISS) {
			return;
		}
		final Instant currentTime = Instant.now();

		if (lastISSDumpTimestamp != null) {
			final Duration timeElapsed = Duration.between(lastISSDumpTimestamp, currentTime);
			if (timeElapsed.toSeconds() < Settings.getInstance().getState().secondsBetweenISSDumps) {
				return;
			}
		}

		lastISSDumpTimestamp = currentTime;

		signedStateFileManager.saveIssStateToDisk(state);
	}
}
