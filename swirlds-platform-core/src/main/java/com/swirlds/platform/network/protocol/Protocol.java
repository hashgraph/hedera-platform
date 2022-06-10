/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
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
