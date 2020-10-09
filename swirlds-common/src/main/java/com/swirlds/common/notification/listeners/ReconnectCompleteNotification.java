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

import java.time.Instant;

/**
 * Class that provides {@link com.swirlds.common.notification.Notification} when reconnect completes
 */
public class ReconnectCompleteNotification extends AbstractNotification {

	private long roundNumber;
	private Instant consensusTimestamp;
	private SwirldState state;

	public ReconnectCompleteNotification(final long roundNumber, final Instant consensusTimestamp,
			final SwirldState state) {
		this.roundNumber = roundNumber;
		this.consensusTimestamp = consensusTimestamp;
		this.state = state;
	}

	/**
	 * get round number from the {@link SwirldState}
	 *
	 * @return round number
	 */
	public long getRoundNumber() {
		return roundNumber;
	}

	/**
	 * The last consensus timestamp handled before the state was signed to the callback
	 *
	 * @return last consensus timestamp
	 */
	public Instant getConsensusTimestamp() {
		return consensusTimestamp;
	}

	/**
	 * get the {@link SwirldState} instance
	 *
	 * @return SwirldState
	 */
	public SwirldState getState() {
		return state;
	}
}
