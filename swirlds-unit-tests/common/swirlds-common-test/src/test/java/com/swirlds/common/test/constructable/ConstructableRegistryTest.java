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

package com.swirlds.common.test.constructable;

import com.swirlds.common.constructable.ClassIdFormatter;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.constructable.RuntimeConstructable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.constructable.subpackage.SubpackageConstructable;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructableRegistryTest {
	static final String PACKAGE_PREFIX = "com.swirlds";
	static final String SUBPACKAGE = "com.swirlds.common.test.constructable.subpackage";

	@Test
	void testRegisterClasses() throws ConstructableRegistryException {
		// when the test starts, this package has no reason to be loaded

		// Does not work in maven surefire when forkCount >= 1 with JaCoCo agent enabled
//		assertFalse(packageLoaded(SUBPACKAGE));
		//printPackages();

		long start = System.currentTimeMillis();
		// find all RuntimeConstructable classes and register their constructors
		ConstructableRegistry.registerConstructables(PACKAGE_PREFIX);
		System.out.printf("Time taken to register all RuntimeConstructables: %dms\n",
				System.currentTimeMillis() - start);

		// now the package should be loaded
		assertTrue(packageLoaded(SUBPACKAGE));
		//printPackages();

		// checks whether the object will be constructed and if the type is correct
		RuntimeConstructable r = ConstructableRegistry
				.getConstructor(ConstructableExample.CLASS_ID).get();
		assertTrue(r instanceof ConstructableExample);

		// checks the objects class ID
		assertEquals(ConstructableExample.CLASS_ID, r.getClassId());

		// calling this again should not cause problems
		ConstructableRegistry.registerConstructables(PACKAGE_PREFIX);

		// Test the scenario of a class ID clash
		long oldClassId = ConstructableExample.CLASS_ID;
		ConstructableExample.CLASS_ID = SubpackageConstructable.CLASS_ID;
		assertThrows(ConstructableRegistryException.class,
				() -> ConstructableRegistry.registerConstructables(PACKAGE_PREFIX));
		// return the old CLASS_ID
		ConstructableExample.CLASS_ID = oldClassId;
		// now it should be fine again
		ConstructableRegistry.registerConstructables(PACKAGE_PREFIX);

		// ask for a class ID that does not exist
		assertNull(ConstructableRegistry.getConstructor(0));
		assertNull(ConstructableRegistry.createObject(0));
	}

	static boolean packageLoaded(String pack) {
		return Stream.of(Package.getPackages())
				.map(Package::getName)
				.anyMatch((p) -> p.equals(pack));
	}

	static void printPackages() {
		Package[] packages = Package.getPackages();
		System.out.println("\n+++ PACKAGES:");
		for (Package aPackage : packages) {
			if (aPackage.getName().startsWith("com.swirlds")) {
				System.out.println(aPackage.getName());
			}
		}
		System.out.println("--- PACKAGES:\n");
	}

	@Test
	void testClassIdFormatting() {
		assertEquals("0(0x0)", ClassIdFormatter.classIdString(0),
				"generated class ID string should match expected");

		assertEquals("123456789(0x75BCD15)", ClassIdFormatter.classIdString(123456789),
				"generated class ID string should match expected");

		assertEquals("-123456789(0xFFFFFFFFF8A432EB)", ClassIdFormatter.classIdString(-123456789),
				"generated class ID string should match expected");

		assertEquals("com.swirlds.common.crypto.Hash:-854880720348154850(0xF422DA83A251741E)",
				ClassIdFormatter.classIdString(new Hash()),
				"generated class ID string should match expected");
	}
}
