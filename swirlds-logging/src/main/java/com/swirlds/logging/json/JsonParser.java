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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swirlds.logging.SwirldsLogParser;

/**
 * A parser that reads logs in json format.
 */
public class JsonParser implements SwirldsLogParser<JsonLogEntry> {

	private static final JsonFactory factory = new JsonFactory();

	@Override
	public JsonLogEntry parse(String line) {
		ObjectMapper mapper = new ObjectMapper(factory);
		JsonNode rootNode;
		try {
			rootNode = mapper.readTree(line);
		} catch (JsonProcessingException e) {
			return null;
		}

		if (rootNode == null) {
			return null;
		}

		return new JsonLogEntry(rootNode);
	}

}
