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

package com.swirlds.common.crypto.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Provides a list of known GPU Device Name patterns.
 */
public enum DeviceName {

	NVIDIA_TESLA, OTHER;

	private static final Pattern NAME_NVIDIA_TESLA_PATTERN = Pattern.compile(".*(Tesla)+.*", Pattern.CASE_INSENSITIVE);
	private static final Map<DeviceName, Pattern> nameLookup = new HashMap<>();

	static {
		nameLookup.put(NVIDIA_TESLA, NAME_NVIDIA_TESLA_PATTERN);
	}

	public static DeviceName resolve(final String vendor) {

		for (Map.Entry<DeviceName, Pattern> entry : nameLookup.entrySet()) {
			final Pattern pattern = entry.getValue();

			if (pattern.matcher(vendor).matches()) {
				return entry.getKey();
			}
		}

		return OTHER;
	}
}
