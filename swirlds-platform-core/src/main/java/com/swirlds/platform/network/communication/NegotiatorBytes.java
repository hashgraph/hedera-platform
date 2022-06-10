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

package com.swirlds.platform.network.communication;

/**
 * Constants use by the {@link Negotiator}
 */
public final class NegotiatorBytes {
	/** sent to keep the connection alive */
	public static final int KEEPALIVE = 255;
	/** accept an initiated protocol */
	public static final int ACCEPT = 254;
	/** reject an initiated protocol */
	public static final int REJECT = 253;
	/** maximum number of protocols supported */
	public static final int MAX_NUMBER_OF_PROTOCOLS = 252;
	/** value used when an int has no initialized value */
	public static final int UNINITIALIZED = Integer.MIN_VALUE;
	/** max value for a byte when converted to an int by an input or output stream */
	public static final int MAX_BYTE = 255;
	/** min value for a byte when converted to an int by an input or output stream */
	public static final int MIN_BYTE = 0;

	private NegotiatorBytes() {
	}

	/**
	 * Checks if the supplied byte is a valid value in the protocol negotiation
	 *
	 * @param b
	 * 		the byte to check
	 * @throws NegotiationException
	 * 		if the byte is not valid
	 */
	public static void checkByte(final int b) throws NegotiationException {
		if (b < MIN_BYTE || b > MAX_BYTE) {
			throw new NegotiationException("not a valid byte: " + b);
		}
	}
}
