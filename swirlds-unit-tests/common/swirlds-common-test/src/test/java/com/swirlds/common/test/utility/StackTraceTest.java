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

package com.swirlds.common.test.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.swirlds.common.utility.StackTrace.getStackTrace;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("StackTrace Test")
class StackTraceTest {

	@Test
	@DisplayName("Stack Trace Test")
	void stackTraceTest() {
		final String stackTrace = getStackTrace().toString();

		final String firstLine = stackTrace.split("\n")[0];
		assertEquals("com.swirlds.common.test.utility.StackTraceTest.stackTraceTest(StackTraceTest.java:32)",
				firstLine, "first line of stack trace doesn't match expected. This may fail if the line " +
						"number of 'getStackTrace()' changes, or if the package/name of this class changes.");
	}

}
