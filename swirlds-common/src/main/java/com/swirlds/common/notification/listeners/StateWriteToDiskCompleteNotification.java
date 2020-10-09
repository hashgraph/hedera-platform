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

package com.swirlds.common.notification.listeners;

import com.swirlds.common.SwirldState;
import com.swirlds.common.notification.AbstractNotification;

import java.io.File;
import java.time.Instant;

/**
 * Class that provides {@link com.swirlds.common.notification.Notification} when state is written to disk
 */
public class StateWriteToDiskCompleteNotification extends AbstractNotification {

	private final long roundNumber;
	private final Instant consensusTimestamp;
	private final SwirldState state;
	private final File folder;

	public StateWriteToDiskCompleteNotification(final long roundNumber, final Instant consensusTimestamp,
			final SwirldState state, final File folder) {
		this.roundNumber = roundNumber;
		this.consensusTimestamp = consensusTimestamp;
		this.state = state;
		this.folder = folder;
	}

	/**
	 * Gets round number from the state that is written to disk.
	 *
	 * @return the round number
	 */
	public long getRoundNumber() {
		return roundNumber;
	}

	/**
	 * Gets the consensus timestamp handled before the state is written to disk.
	 *
	 * @return the consensus timestamp
	 */
	public Instant getConsensusTimestamp() {
		return consensusTimestamp;
	}

	/**
	 * Gets the {@link SwirldState} instance that was sigend and saved to disk.
	 *
	 * @return the signed {@link SwirldState} instance
	 */
	public SwirldState getState() {
		return state;
	}

	/**
	 * Gets the path where the signed state was written to disk.
	 *
	 * @return the path containing the saved state
	 */
	public File getFolder() {
		return folder;
	}
}
