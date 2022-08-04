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
