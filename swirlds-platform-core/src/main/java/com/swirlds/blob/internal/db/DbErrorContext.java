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

package com.swirlds.blob.internal.db;

import java.util.HashMap;
import java.util.Map;

public enum DbErrorContext {
	UNKNOWN(Integer.MIN_VALUE),
	NONE(1024),
	IDENTIFIER(1025),
	HASH_LIST(1026),
	REF_COUNT(1027);


	private static final Map<Integer, DbErrorContext> lookupTable = new HashMap<>();

	static {
		final DbErrorContext[] codes = values();

		for (DbErrorContext c : codes) {
			lookupTable.put(c.intValue(), c);
		}
	}

	private int code;

	DbErrorContext(final int code) {
		this.code = code;
	}

	public static DbErrorContext valueOf(final int code) {
		return lookupTable.getOrDefault(code, UNKNOWN);
	}

	public static boolean isKnown(final int code) {
		return lookupTable.containsKey(code) && code != UNKNOWN.intValue();
	}

	public int intValue() {
		return code;
	}

}
