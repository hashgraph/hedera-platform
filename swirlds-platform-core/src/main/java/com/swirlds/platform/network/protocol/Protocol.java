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

package com.swirlds.platform.network.protocol;

/**
 * A network protocol that run over a provided connection. The decision to run the protocol is made outside it, it can
 * only communicate its willingness to run through the provided interface. An instance of this class must be created per
 * peer.
 */
public interface Protocol extends ProtocolRunnable {
	/**
	 * @return true iff we should we try and initiate this protocol with our peer
	 */
	boolean shouldInitiate();

	/**
	 * Our peer initiated this protocol, should we accept?
	 *
	 * @return true if we should accept, false if we should reject
	 */
	boolean shouldAccept();

	/**
	 * <p>
	 * If both sides initiated this protocol simultaneously, should we proceed with running the protocol?
	 * </p>
	 * <p>
	 * IMPORTANT: the value returned should remain consistent for a protocol, it should never change depending on the
	 * state of the instance.
	 * </p>
	 *
	 * @return true if we should run, false otherwise
	 */
	boolean acceptOnSimultaneousInitiate();
}
