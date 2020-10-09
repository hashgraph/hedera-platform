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

package com.swirlds.common;

import com.swirlds.common.events.Event;

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
