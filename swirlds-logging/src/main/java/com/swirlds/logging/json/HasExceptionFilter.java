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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A filter that operates on exceptions.
 */
public class HasExceptionFilter implements Predicate<JsonLogEntry> {

	private final Set<String> exceptionTypes;

	public static HasExceptionFilter hasException(final String... exceptionTypes) {
		Set<String> set = new HashSet<>();
		Collections.addAll(set, exceptionTypes);
		return new HasExceptionFilter(set);
	}

	public static HasExceptionFilter hasException(final List<String> exceptionTypes) {
		return new HasExceptionFilter(new HashSet<>(exceptionTypes));
	}

	public static HasExceptionFilter hasException(final Set<String> exceptionTypes) {
		return new HasExceptionFilter(exceptionTypes);
	}

	/**
	 * Create a filter that catches only specific exception types.
	 *
	 * @param exceptionTypes
	 * 		a list of exception type names. Exact matches only, does not consider inheritance.
	 */
	public HasExceptionFilter(final Set<String> exceptionTypes) {
		this.exceptionTypes = exceptionTypes;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean test(final JsonLogEntry entry) {
		if (!entry.hasException() || exceptionTypes == null) {
			return false;
		}
		return exceptionTypes.contains(entry.getExceptionType());
	}
}
