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
