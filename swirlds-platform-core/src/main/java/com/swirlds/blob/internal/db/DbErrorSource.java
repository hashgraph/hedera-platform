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

public enum DbErrorSource {

	UNKNOWN(Integer.MIN_VALUE),
	NONE(4096),
	SELF(4097),
	BLOB_RETRIEVE(4098),
	BLOB_STORE(4099),
	BLOB_APPEND(4100),
	BLOB_UPDATE(4101),
	BLOB_DELETE(4102),
	BLOB_RESTORE(4103);


	private static final Map<Integer, DbErrorSource> lookupTable = new HashMap<>();

	static {
		final DbErrorSource[] codes = values();

		for (DbErrorSource c : codes) {
			lookupTable.put(c.intValue(), c);
		}
	}

	private int code;

	DbErrorSource(final int code) {
		this.code = code;
	}

	public static DbErrorSource valueOf(final int code) {
		return lookupTable.getOrDefault(code, UNKNOWN);
	}

	public static boolean isKnown(final int code) {
		return lookupTable.containsKey(code) && code != UNKNOWN.intValue();
	}

	public int intValue() {
		return code;
	}

}
