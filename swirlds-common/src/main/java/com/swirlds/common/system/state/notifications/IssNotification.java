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

package com.swirlds.common.system.state.notifications;

import com.swirlds.common.notification.AbstractNotification;
import com.swirlds.common.system.DualState;
import com.swirlds.common.system.SwirldState;

import java.time.Instant;

/**
 * This {@link com.swirlds.common.notification.Notification Notification} is triggered when there is an ISS
 * (i.e. an Invalid State Signature). State is guaranteed to hold a weak reservation until the callback completes.
 */
public class IssNotification extends AbstractNotification {

	private final SwirldState swirldState;
	private final DualState dualState;
	private final long otherNodeId;
	private final long round;
	private final Instant consensusTimestamp;

	/**
	 * Create a new ISS notification.
	 *
	 * @param swirldState
	 * 		the swirld state for the round with the incorrect signature
	 * @param dualState
	 * 		the dualState for the round with the incorrect signature
	 * @param otherNodeId
	 * 		the node that provided the incorrect signature
	 * @param round
	 * 		the round when the ISS occurred
	 * @param consensusTimestamp
	 * 		the timestamp of hte round with the ISS
	 */
	public IssNotification(
			final SwirldState swirldState,
			final DualState dualState,
			final long otherNodeId,
			final long round,
			final Instant consensusTimestamp) {
		this.swirldState = swirldState;
		this.dualState = dualState;
		this.otherNodeId = otherNodeId;
		this.round = round;
		this.consensusTimestamp = consensusTimestamp;
	}

	/**
	 * Get the swirld state for the round that had the ISS.
	 * Guaranteed to have a weak reservation in the scope of the notification callback.
	 *
	 * @return the swirld state form an ISS round
	 */
	@SuppressWarnings("unchecked")
	public <T extends SwirldState> T getSwirldState() {
		return (T) swirldState;
	}

	/**
	 * Get the dual state for the round that had the ISS.
	 * Guaranteed to have a weak reservation in the scope of the notification callback.
	 *
	 * @return the dual state from an ISS round
	 */
	public DualState getDualState() {
		return dualState;
	}

	/**
	 * Get the ID of the node that produced the invalid signature.
	 *
	 * @return the other node's ID
	 */
	public long getOtherNodeId() {
		return otherNodeId;
	}

	/**
	 * Get the round of the ISS.
	 */
	public long getRound() {
		return round;
	}

	/**
	 * Get the timestamp of the round with the ISS.
	 */
	public Instant getConsensusTimestamp() {
		return consensusTimestamp;
	}
}
