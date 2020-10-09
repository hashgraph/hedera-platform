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

package com.swirlds.logging.json;

import com.swirlds.logging.LogMarker;
import com.swirlds.logging.LogMarkerType;

import java.util.function.Predicate;

/**
 * Check if the log marker signifies an error. Allows all entries with a {@link LogMarkerType#ERROR} type to pass.
 */
public class HasErrorMarkerFilter implements Predicate<JsonLogEntry> {

	public static HasErrorMarkerFilter hasErrorMarker() {
		return new HasErrorMarkerFilter();
	}

	public HasErrorMarkerFilter() {

	}

	@Override
	public boolean test(JsonLogEntry jsonLogEntry) {
		String markerName = jsonLogEntry.getMarker();
		try {
			return LogMarker.valueOf(markerName).getType().equals(LogMarkerType.ERROR);
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

}
