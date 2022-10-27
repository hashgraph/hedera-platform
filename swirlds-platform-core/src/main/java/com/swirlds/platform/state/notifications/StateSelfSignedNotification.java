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

package com.swirlds.platform.state.notifications;

import com.swirlds.common.crypto.Signature;
import com.swirlds.common.notification.AbstractNotification;
import com.swirlds.common.system.NodeId;

/**
 * A {@link com.swirlds.common.notification.Notification Notification} that a signed state been self signed.
 * State is guaranteed to hold a weak reservation until callback is finished.
 */
public class StateSelfSignedNotification extends AbstractNotification {

	private final long round;
	private final Signature selfSignature;

	// FUTURE WORK:
	//  this field can be removed once PlatformContext maintains a single notification engine per platform instance
	private final NodeId selfId;

	/**
	 * Create a notification for state that was signed by this node.
	 *
	 * @param round
	 * 		the round of the state that was signed
	 * @param selfSignature
	 * 		this node's signature on the state
	 * @param selfId
	 * 		the ID of this node
	 */
	public StateSelfSignedNotification(
			final long round,
			final Signature selfSignature,
			final NodeId selfId) {
		this.round = round;
		this.selfSignature = selfSignature;
		this.selfId = selfId;
	}

	/**
	 * The round of the state that was signed.
	 */
	public long getRound() {
		return round;
	}

	/**
	 * Get this node's signature on the state.
	 */
	public Signature getSelfSignature() {
		return selfSignature;
	}

	/**
	 * The ID of this node.
	 */
	public NodeId getSelfId() {
		return selfId;
	}
}
