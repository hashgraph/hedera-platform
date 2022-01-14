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

package com.swirlds.platform.components;

import com.swirlds.platform.state.SigSet;

/**
 * Responsible for recording the state signature
 */
public interface StateSignatureRecorder {
	/**
	 * Record a signature for the signed state for the given round with the given hash. The caller must not
	 * change the array elements after passing this in. Each time the caller calls this method with
	 * memberId==selfId, the lastRoundReceived parameter must be greater than on the previous such call.
	 *
	 * @param lastRoundReceived
	 * 		the signed state reflects all events with received round less than or equal to this
	 * @param memberId
	 * 		the member ID of the signer
	 * @param hash
	 * 		the hash of the state to be signed. The signature algorithm may internally hash this
	 * 		hash.
	 * @param sig
	 * 		the signature
	 * @return the SigSet that the new SigInfo is added to
	 */
	SigSet recordStateSig(long lastRoundReceived, long memberId, byte[] hash, byte[] sig);
}
