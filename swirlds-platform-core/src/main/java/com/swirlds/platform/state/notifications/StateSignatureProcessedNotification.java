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

import com.swirlds.common.notification.AbstractNotification;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.state.signed.SignedState;

/**
 * A {@link com.swirlds.common.notification.Notification Notification} that a new
 * {@link com.swirlds.common.crypto.Signature Signature} on a {@link SignedState}
 * has been processed. State is guaranteed to hold a weak reservation until
 * the notification is completed.
 */
public class StateSignatureProcessedNotification extends AbstractNotification {

	private final SignedState signedState;
	private final long nodeId;
	private final boolean valid;

	// FUTURE WORK:
	//  this field can be removed once PlatformContext maintains a single notification engine per platform instance
	private final NodeId selfId;

	/**
	 * Create a notification for a signature that has been processed.
	 *
	 * @param signedState
	 * 		the state that just had a signature processed
	 * @param nodeId
	 * 		the ID of the node that provided the signature
	 * @param valid
	 * 		is the signature valid?
	 * @param selfId
	 * 		the ID of this node
	 */
	public StateSignatureProcessedNotification(
			final SignedState signedState,
			final long nodeId,
			final boolean valid,
			final NodeId selfId) {

		this.signedState = signedState;
		this.nodeId = nodeId;
		this.valid = valid;
		this.selfId = selfId;
	}

	/**
	 * Get the signed state that just had a signature processed.
	 */
	public SignedState getSignedState() {
		return signedState;
	}

	/**
	 * The ID of the node that provided the signature.
	 */
	public long getNodeId() {
		return nodeId;
	}

	/**
	 * Is the signature valid?
	 */
	public boolean isValid() {
		return valid;
	}

	/**
	 * The ID of this node.
	 */
	public NodeId getSelfId() {
		return selfId;
	}
}
