/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.platform.stats;

import com.swirlds.common.StatEntry;
import com.swirlds.common.UniqueId;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Used to construct different types types of StatEntry instances
 */
public class StatConstructor {
	/** A reserved value used when none is set */
	private static final int NO_VALUE = 0;

	/**
	 * Used to create a StatEntry whose value is limited to a single enum. This stat displays the enums unique ID
	 * instead of the name so that it can be tracked on a graph.
	 *
	 * @param name
	 * 		the name of the stat
	 * @param category
	 * 		the category of the stat
	 * @param enumValues
	 * 		all possible values for the enum
	 * @param enumSupplier
	 * 		supplier of the current value
	 * @param <T>
	 * 		The Enum type to track
	 * @return a StatEntry that tracks an enum value
	 */
	public static <T extends Enum<T> & UniqueId> StatEntry createEnumStat(
			final String name,
			final String category,
			final T[] enumValues,
			final Supplier<T> enumSupplier) {
		// check if the reserved value is being used
		if (Arrays.stream(enumValues).anyMatch((v) -> v.getId() == NO_VALUE)) {
			throw new IllegalArgumentException("Unique ID must not be equal to " + NO_VALUE);
		}

		// add all enum values to the description
		final StringBuilder desc = new StringBuilder();
		desc.append(NO_VALUE).append('=').append("NO_VALUE").append(' ');
		Arrays.stream(enumValues).forEach(
				(v) -> desc.append(v.getId()).append('=').append(v.name()).append(' ')
		);

		final Supplier<Object> statValueSupplier = () -> {
			T t = enumSupplier.get();
			if (t == null) {
				return NO_VALUE;
			}
			return t.getId();
		};

		return new StatEntry(
				category,
				name,
				desc.toString(),
				"%d",
				null,
				null,
				null,
				statValueSupplier
		);
	}
}
