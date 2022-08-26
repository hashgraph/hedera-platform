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

package com.swirlds.common.test.system;

import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.test.framework.TestTypeTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Basic SoftwareVersion Tests")
public class BasicSoftwareVersionTest {

	public final SoftwareVersion NO_VERSION = SoftwareVersion.NO_VERSION;
	public final SoftwareVersion VERSION_ONE = new BasicSoftwareVersion(1);
	public final SoftwareVersion VERSION_TWO = new BasicSoftwareVersion(2);

	@Test
	@Tag(TestTypeTags.FUNCTIONAL)
	@DisplayName("Verify compareTo functionality")
	public void testCompareTo() {
		final int comparedToNoVersion = 1;
		final int comparedToSelf = 0;

		assertEquals(comparedToNoVersion, VERSION_ONE.compareTo(NO_VERSION),
				"Should always get back 1 when comparing to NO_VERSION.");
		assertEquals(comparedToNoVersion, VERSION_TWO.compareTo(NO_VERSION),
				"Should always get back 1 when comparing to NO_VERSION.");
		assertEquals(comparedToSelf, VERSION_ONE.compareTo(VERSION_ONE),
				"Should always get back 0 when comparing to self.");
		assertEquals(comparedToSelf, VERSION_TWO.compareTo(VERSION_TWO),
				"Should always get back 0 when comparing to self.");
		assertTrue(VERSION_ONE.compareTo(VERSION_TWO) < 0, "VERSION_ONE should be older than VERSION_TWO.");
		assertTrue(VERSION_TWO.compareTo(VERSION_ONE) > 0, "VERSION_TWO should be newer than VERSION_ONE.");
	}

	@Test
	@Tag(TestTypeTags.FUNCTIONAL)
	@DisplayName("Verify toString functionality")
	public void testToString() {
		assertEquals("1", VERSION_ONE.toString(), "VERSION_ONE not reporting its version as 1.");
		assertEquals("2", VERSION_TWO.toString(), "VERSION_TWO not reporting its version as 2.");
	}

}
