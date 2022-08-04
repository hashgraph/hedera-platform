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

package com.swirlds.common;

import com.swirlds.common.system.AddressBook;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.events.Event;

import java.time.Instant;

/**
 * Provides a standard listener to be notified when the platform receives an invalid signed state signature.
 */
@FunctionalInterface
public interface InvalidSignedStateListener {

	/**
	 * Called each time the platform receives a signed state signature from a remote node that does not match the
	 * signature of the local signed state instance for a given round.
	 *
	 * @param platform
	 * 		the platform instance that received the invalid signed state from a peer
	 * @param addressBook
	 * 		the address book from the local signed state instance
	 * @param state
	 * 		the Swirlds application state object from the local signed state instance. This state may be deleted
	 * 		after this function returns, so it is imperative that the state not be saved or used after this
	 * 		function returns.
	 * @param events
	 * 		the array of events contained in the local signed state instance
	 * @param selfId
	 * 		the node id of the local node
	 * @param otherId
	 * 		the node id of the remote node that provided the state signature not matching the local signed state
	 * @param round
	 * 		the round number of the signed state
	 * @param consensusTimestamp
	 * 		the consensus timestamp of the local signed state
	 * @param numEventsCons
	 * 		the total events in consensus of the local signed state
	 * @param signature
	 * 		the signature of the local signed state instance
	 * @param stateHash
	 * 		the hash of the Swirlds application state object in the local signed state instance
	 */
	void notifyError(final Platform platform, final AddressBook addressBook, final SwirldState state,
			final Event[] events, final NodeId selfId, final NodeId otherId, final long round,
			final Instant consensusTimestamp, final long numEventsCons, final byte[] signature, final byte[] stateHash);

}
