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

package com.swirlds.common.settings;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.Duration;

import static com.swirlds.common.settings.ParsingUtils.parseDuration;

/**
 * This object is capable of parsing durations, e.g. "3 seconds", "1 day", "2.5 hours", "32ms".
 * <p>
 * This deserializer currently utilizes a regex for parsing, which may have superlinear time complexity
 * for arbitrary input. Until that is addressed, do not use this parser on untrusted strings.
 */
public class DurationDeserializer extends StdDeserializer<Duration> {

	private static final long serialVersionUID = 1;

	public DurationDeserializer() {
		super(Duration.class);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Duration deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		return parseDuration(p.readValueAs(String.class));
	}
}
