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

package com.swirlds.platform.chatter.protocol;

import com.swirlds.common.io.SelfSerializable;

/**
 * Handles chatter messages received from a peer
 */
public interface PeerMessageHandler {
	/**
	 * Handle a message received from a peer
	 *
	 * @param message
	 * 		the message received
	 * @throws PeerMessageException
	 * 		if the message is invalid in any way
	 */
	void handleMessage(SelfSerializable message) throws PeerMessageException;
}
