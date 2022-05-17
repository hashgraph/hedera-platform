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

package com.swirlds.logging.json;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A filter that operates on log4j markers.
 */
public class HasMarkerFilter implements Predicate<JsonLogEntry> {

	private final Set<String> markers;

	public static HasMarkerFilter hasMarker(final String... markers) {
		Set<String> set = new HashSet<>();
		Collections.addAll(set, markers);
		return new HasMarkerFilter(set);
	}

	public static HasMarkerFilter hasMarker(List<String> markerNames) {
		return new HasMarkerFilter(new HashSet<>(markerNames));
	}

	public static HasMarkerFilter hasMarker(Set<String> markerNames) {
		return new HasMarkerFilter(markerNames);
	}

	/**
	 * Create a filter that allows only certain markers to pass.
	 *
	 * @param markers
	 * 		markers to be allowed by this filter
	 */
	public HasMarkerFilter(final Set<String> markers) {
		this.markers = markers;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean test(final JsonLogEntry entry) {
		if (markers == null) {
			return false;
		}
		return markers.contains(entry.getMarker());
	}
}
