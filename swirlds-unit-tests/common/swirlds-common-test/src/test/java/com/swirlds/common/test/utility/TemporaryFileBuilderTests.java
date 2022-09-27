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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static com.swirlds.common.io.utility.FileUtils.deleteDirectory;
import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.io.utility.TemporaryFileBuilder.buildTemporaryDirectory;
import static com.swirlds.common.io.utility.TemporaryFileBuilder.buildTemporaryFile;
import static com.swirlds.common.io.utility.TemporaryFileBuilder.getTemporaryFileLocation;
import static com.swirlds.common.io.utility.TemporaryFileBuilder.overrideTemporaryFileLocation;
import static java.nio.file.Files.exists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TemporaryFileBuilder Tests")
public class TemporaryFileBuilderTests {


	@Test
	@DisplayName("buildTemporaryDirectory() Test")
	@SuppressWarnings("ConstantConditions")
	void buildTemporaryDirectoryTest() throws IOException {
		final Path tmp = buildTemporaryDirectory();
		assertTrue(exists(tmp), "directory should exist");
		assertTrue(tmp.toFile().canRead(), "invalid permissions, should be able to read");
		assertTrue(tmp.toFile().canWrite(), "invalid permissions, should be able to write");
		assertTrue(Files.isDirectory(tmp), "invalid file type");
		assertEquals(0, tmp.toFile().listFiles().length, "should have no children");
		assertTrue(tmp.toFile().delete(), "unable to delete temporary directory");
	}

	@Test
	@DisplayName("Unique Files Test")
	void uniqueFilesTest() throws IOException {
		final Set<Path> files = new HashSet<>();

		for (int i = 0; i < 100; i++) {
			if (i % 2 == 0) {
				assertTrue(files.add(buildTemporaryFile()), "file should not yet exist");
			} else {
				assertTrue(files.add(buildTemporaryDirectory()), "file should not yet exist");
			}
		}

		// Cleanup
		deleteDirectory(getTemporaryFileLocation());
	}

	@Test
	@DisplayName("Postfix Test")
	void postfixTest() throws IOException {
		final Path file = buildTemporaryFile("foo");
		final Path directory = buildTemporaryFile("bar");

		final String fileName = file.getFileName().toString();
		final String[] fileNameElements = fileName.split("-");
		assertEquals(2, fileNameElements.length, "invalid file name format");
		assertEquals("foo", fileNameElements[1], "invalid postfix");

		final String directoryName = directory.getFileName().toString();
		final String[] directoryNameElements = directoryName.split("-");
		assertEquals(2, directoryNameElements.length, "invalid directory name format");
		assertEquals("bar", directoryNameElements[1], "invalid postfix");

		// Cleanup
		deleteDirectory(getTemporaryFileLocation());
	}

	@Test
	@DisplayName("Auto Cleanup Test")
	void autoCleanupTest() throws IOException {
		final List<Path> files = new LinkedList<>();

		for (int i = 0; i < 100; i++) {
			assertTrue(files.add(buildTemporaryDirectory()), "file should not yet exist");
		}

		for (final Path file : files) {
			assertTrue(exists(file), "file should still exist");
		}

		// This should cause all files to be deleted
		overrideTemporaryFileLocation(getTemporaryFileLocation());

		for (final Path file : files) {
			assertFalse(exists(file), "file should have been deleted");
		}
	}

	@Test
	@DisplayName("Set Temporary File Directory Test")
	void setTemporaryFileDirectoryTest() throws IOException {

		final Path originalTemporaryFileLocation = getTemporaryFileLocation();

		overrideTemporaryFileLocation(getAbsolutePath("foobar"));
		final Path file = buildTemporaryFile();
		assertEquals("foobar", file.getParent().getFileName().toString(), "invalid location");
		deleteDirectory(getTemporaryFileLocation());

		// Reset location for other tests
		overrideTemporaryFileLocation(originalTemporaryFileLocation);
	}
}
