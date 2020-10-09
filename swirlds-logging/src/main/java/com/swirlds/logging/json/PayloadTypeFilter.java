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

import com.swirlds.logging.payloads.LogPayload;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static com.swirlds.logging.payloads.AbstractLogPayload.extractPayloadType;

/**
 * A filter that acts on payload types.
 */
public class PayloadTypeFilter implements Predicate<JsonLogEntry> {

	private final Set<String> types;

	public static PayloadTypeFilter payloadType(String... types) {
		Set<String> set = new HashSet<>();
		Collections.addAll(set, types);
		return new PayloadTypeFilter(set);
	}

	public static PayloadTypeFilter payloadType(List<String> types) {
		return new PayloadTypeFilter(new HashSet<>(types));
	}

	public static PayloadTypeFilter payloadType(Set<String> types) {
		return new PayloadTypeFilter(types);
	}

	/**
	 * Create a filter that allows entries with certain types of payloads pass.
	 *
	 * @param types
	 * 		a set of fully qualified payload type names
	 */
	public <T extends LogPayload> PayloadTypeFilter(Set<String> types) {
		this.types = types;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean test(final JsonLogEntry entry) {
		if (types == null) {
			return false;
		}
		final String type = extractPayloadType(entry.getRawPayload());
		return types.contains(type);
	}
}
