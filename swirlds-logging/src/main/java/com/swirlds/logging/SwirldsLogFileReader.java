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

package com.swirlds.logging;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * An object that is able to read a swirlds log file.
 */
public class SwirldsLogFileReader<T> extends SwirldsLogReader<T> {

	private final SwirldsLogParser<T> parser;
	private final BufferedReader fileReader;

	/**
	 * Create a new log file reader.
	 *
	 * @param logFile
	 * 		The log file to read.
	 * @param parser
	 * 		The parser that should be used to read the log file.
	 */
	public SwirldsLogFileReader(final File logFile, final SwirldsLogParser<T> parser) throws FileNotFoundException {
		this.parser = parser;
		this.fileReader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile)));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected T readNextEntry() throws IOException {
		while (true) {
			String line = fileReader.readLine();
			if (line == null) {
				return null;
			}

			//skip empty lines
			if (line.strip().isEmpty()) {
				continue;
			}

			T entry = parser.parse(line);
			if (entry != null) {
				return entry;
			}
		}
	}
}
