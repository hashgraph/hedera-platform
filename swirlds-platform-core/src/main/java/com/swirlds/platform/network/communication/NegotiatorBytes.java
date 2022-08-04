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
