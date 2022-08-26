/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.test.stream;

import org.junit.jupiter.api.Test;

import java.io.File;

import static com.swirlds.common.stream.EventStreamType.EVENT;
import static com.swirlds.common.test.stream.TestStreamType.TEST_STREAM;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamTypeTest {
	public static final File NULL_FILE = null;

	public static final String RECORD_FILE_NAME = "test.rcd";
	public static final File RECORD_FILE = new File(RECORD_FILE_NAME);
	public static final String RECORD_SIG_FILE_NAME = "test.rcd_sig";
	public static final File RECORD_SIG_FILE = new File(RECORD_SIG_FILE_NAME);

	public static final String EVENT_FILE_NAME = "test.evts";
	public static final File EVENT_FILE = new File(EVENT_FILE_NAME);
	public static final String EVENT_SIG_FILE_NAME = "test.evts_sig";
	public static final File EVENT_SIG_FILE = new File(EVENT_SIG_FILE_NAME);

	public static final String TEST_FILE_NAME = "name.test";
	public static final File TEST_FILE = new File(TEST_FILE_NAME);
	public static final String TEST_SIG_FILE_NAME = "name.test_sig";
	public static final File TEST_SIG_FILE = new File(TEST_SIG_FILE_NAME);

	public static final String NON_STREAM_FILE_NAME = "test.soc";
	public static final File NON_STREAM_FILE = new File(NON_STREAM_FILE_NAME);

	private static final String IS_STREAM_FILE_ERROR_MSG = "isStreamFile() returns unexpected result";
	private static final String IS_STREAM_SIG_FILE_ERROR_MSG = "isStreamSigFile() returns unexpected result";

	@Test
	void isStreamFileTest() {
		assertTrue(EVENT.isStreamFile(EVENT_FILE_NAME), IS_STREAM_FILE_ERROR_MSG);
		assertTrue(EVENT.isStreamFile(EVENT_FILE), IS_STREAM_FILE_ERROR_MSG);

		assertFalse(EVENT.isStreamFile(EVENT_SIG_FILE_NAME), IS_STREAM_FILE_ERROR_MSG);
		assertFalse(EVENT.isStreamFile(EVENT_SIG_FILE), IS_STREAM_FILE_ERROR_MSG);

		assertFalse(EVENT.isStreamFile(RECORD_FILE_NAME), IS_STREAM_FILE_ERROR_MSG);
		assertFalse(EVENT.isStreamFile(RECORD_FILE), IS_STREAM_FILE_ERROR_MSG);

		assertFalse(EVENT.isStreamFile(RECORD_SIG_FILE_NAME), IS_STREAM_FILE_ERROR_MSG);
		assertFalse(EVENT.isStreamFile(RECORD_SIG_FILE), IS_STREAM_FILE_ERROR_MSG);

		assertFalse(EVENT.isStreamFile(NON_STREAM_FILE_NAME), IS_STREAM_FILE_ERROR_MSG);
		assertFalse(EVENT.isStreamFile(NON_STREAM_FILE), IS_STREAM_FILE_ERROR_MSG);

		assertTrue(TEST_STREAM.isStreamFile(TEST_FILE_NAME), IS_STREAM_FILE_ERROR_MSG);
		assertTrue(TEST_STREAM.isStreamFile(TEST_FILE), IS_STREAM_FILE_ERROR_MSG);
	}

	@Test
	void isStreamSigFileTest() {
		assertTrue(EVENT.isStreamSigFile(EVENT_SIG_FILE_NAME), IS_STREAM_SIG_FILE_ERROR_MSG);
		assertTrue(EVENT.isStreamSigFile(EVENT_SIG_FILE), IS_STREAM_SIG_FILE_ERROR_MSG);

		assertFalse(EVENT.isStreamSigFile(EVENT_FILE_NAME), IS_STREAM_SIG_FILE_ERROR_MSG);
		assertFalse(EVENT.isStreamSigFile(EVENT_FILE), IS_STREAM_SIG_FILE_ERROR_MSG);

		assertFalse(EVENT.isStreamSigFile(RECORD_FILE_NAME), IS_STREAM_SIG_FILE_ERROR_MSG);
		assertFalse(EVENT.isStreamSigFile(RECORD_FILE), IS_STREAM_SIG_FILE_ERROR_MSG);

		assertFalse(EVENT.isStreamSigFile(RECORD_SIG_FILE_NAME), IS_STREAM_SIG_FILE_ERROR_MSG);
		assertFalse(EVENT.isStreamSigFile(RECORD_SIG_FILE), IS_STREAM_SIG_FILE_ERROR_MSG);

		assertFalse(EVENT.isStreamSigFile(NON_STREAM_FILE_NAME), IS_STREAM_SIG_FILE_ERROR_MSG);
		assertFalse(EVENT.isStreamSigFile(NON_STREAM_FILE), IS_STREAM_SIG_FILE_ERROR_MSG);

		assertTrue(TEST_STREAM.isStreamSigFile(TEST_SIG_FILE_NAME), IS_STREAM_FILE_ERROR_MSG);
		assertTrue(TEST_STREAM.isStreamSigFile(TEST_SIG_FILE), IS_STREAM_FILE_ERROR_MSG);
	}
}
