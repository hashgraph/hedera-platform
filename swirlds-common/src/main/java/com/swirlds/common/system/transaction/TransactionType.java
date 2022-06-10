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

package com.swirlds.common.system.transaction;

import java.util.HashMap;
import java.util.Map;

/**
 * Define currently supported transaction types
 */
public enum TransactionType {
	/** Transaction created by Swirld Application */
	APPLICATION((byte) 0),
	/** first byte of a system transaction giving a signed state's (round number, signature) */
	SYS_TRANS_STATE_SIG((byte) 1),
	/** same as SYS_TRANS_STATE_SIG but freeze event creation after this system transaction is put in an event */
	SYS_TRANS_STATE_SIG_FREEZE((byte) 2),
	/** first byte of a system transaction giving all avgPingMilliseconds stats (sent as ping time in microseconds) */
	SYS_TRANS_PING_MICROSECONDS((byte) 3),
	/** first byte of a system transaction giving all avgBytePerSecSent stats (sent as bits per second) */
	SYS_TRANS_BITS_PER_SECOND((byte) 4);

	private byte value;
	private static Map<Byte, TransactionType> map = new HashMap<>();

	/**
	 * Create a TransactionType enum instance based on byte value
	 *
	 * @param value
	 * 		byte value used to created TransactionType enum instance
	 */
	TransactionType(final byte value) {
		this.value = value;
	}

	static {
		for (TransactionType type : TransactionType.values()) {
			map.put(type.value, type);
		}
	}

	/**
	 * @return the value of an instance of TransactionType enum
	 */
	public byte getValue() {
		return value;
	}

	/**
	 * @return TransactionType enum converted from an integer
	 */
	public static TransactionType valueOf(final byte type) {
		return map.get(type);
	}

}
