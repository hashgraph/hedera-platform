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

package com.swirlds.platform.sync;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.EventStrings;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility routines to generate formatted log string for sync-related variables.
 */
public final class SyncLogging {
	public static final int BRIEF_HASH_LENGTH = 4;

	/**
	 * This type is not constructable
	 */
	private SyncLogging() {
	}

	public static String toShortShadows(Collection<ShadowEvent> shadows) {
		if (shadows == null) {
			return "null";
		}
		return shadows.stream().map(s -> EventStrings.toShortString(s.getEvent())).collect(Collectors.joining(","));
	}

	public static String toShortHashes(List<Hash> hashes) {
		if (hashes == null) {
			return "null";
		}
		return hashes.stream().map(h -> CommonUtils.hex(h.getValue(), BRIEF_HASH_LENGTH)).collect(
				Collectors.joining(","));
	}

	public static String toShortBooleans(List<Boolean> booleans) {
		if (booleans == null) {
			return "null";
		}
		return booleans.stream().map(b -> Boolean.TRUE.equals(b) ? "T" : "F").collect(Collectors.joining(","));
	}


}
