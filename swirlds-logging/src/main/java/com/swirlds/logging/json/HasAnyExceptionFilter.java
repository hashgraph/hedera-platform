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

import java.util.function.Predicate;

/**
 * A filter that checks if any exception is present.
 */
public class HasAnyExceptionFilter implements Predicate<JsonLogEntry> {

	public static HasAnyExceptionFilter hasAnyException() {
		return new HasAnyExceptionFilter();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean test(JsonLogEntry jsonLogEntry) {
		return jsonLogEntry.hasException();
	}
}
