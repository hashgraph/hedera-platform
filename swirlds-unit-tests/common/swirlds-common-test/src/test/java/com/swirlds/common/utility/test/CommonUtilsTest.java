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

package com.swirlds.common.utility.test;

import com.swirlds.common.utility.CommonUtils;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import static com.swirlds.common.utility.CommonUtils.canonicalFile;
import static com.swirlds.common.utility.CommonUtils.hex;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommonUtilsTest {
	private static final byte[] HEX_BYTES = { 0x12, 0x34, 0x56, 0x78, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f };
	private static final String HEX_STRING = "123456780a0b0c0d0e0f";
	private static final String NEW_LINE = "new line\n";

	// file pointers (and contents) common to the void hardLinkTreeTest_happyPath tests.
	// only the file contents may be created at this point; none of the files have yet been created.
	private static final String ONE = "one" + System.lineSeparator();
	private static final String TWO = "two" + System.lineSeparator();
	// content created/verified during the link-checking steps
	private String new1;
	private String new2;

	// these variables get assigned inside hardLinkTreeTest_happyPath_setFiles().
	// directories on source tree
	private File dir;
	private File subDir;
	private File subSubDir;

	// Leaves on top level of source tree
	private File top1;
	private File top2;

	// Leaves on subDir level of source tree
	private File sub1;
	private File sub2;

	// Leaves on subSubDir level of source tree
	private File subsub1;
	private File subsub2;

	// corresponding Files on destination tree
	private File dir2;

	// Leaves on top level of destination tree
	private File copy1;
	private File copy2;
	private File hardlink1;
	private File softlink1;

	// Leaves on subDir level of destination tree
	private File subdir1;
	private File subdir2;
	private File hardlink2;
	private File softlink2;

	// Leaves on subSubDir level of destination tree
	private File subsubdir1;
	private File subsubdir2;
	private File hardlink3;
	private File softlink3;

	@Test
	void hexTest() {
		assertTrue(hex(null).contains("null"),
				"the output of a null input should indicate its null");
		assertEquals("", hex(new byte[0]),
				"for an empty array we should get an empty string");

		assertEquals(HEX_STRING, hex(HEX_BYTES), "hex value should match");
		final int length = 2;
		assertEquals(HEX_STRING.substring(0, length * 2), hex(HEX_BYTES, length),
				"hex value should match");

		assertThrows(IllegalArgumentException.class, () -> hex(HEX_BYTES, HEX_BYTES.length + 1),
				"should throw if illegal length");
	}

	@Test
	void unhexTest() {
		assertNull(unhex(null), "null input should provide null output");
		assertThrows(IllegalArgumentException.class, () -> unhex("123"),
				"a hex string can never have a odd number of characters");
		assertArrayEquals(HEX_BYTES, unhex(HEX_STRING), "hex value should match");
		assertArrayEquals(HEX_BYTES, unhex(HEX_STRING.toUpperCase()), "hex value should match");

		assertThrows(IllegalArgumentException.class, () -> unhex("a random string"),
				"hex characters should be in the range: [A-Fa-f0-9]");
	}

	@Test
	void deleteNonExistingDirTest() {
		File file = new File("notExist/nonExistingPath");
		assertFalse(CommonUtils.deleteDirectory(file));
	}

	@Test
	void deleteDirectoryTest() {
		File dir = new File("src/test/resources/dirForTestingDelete");
		File subDir = canonicalFile(dir, "subDir");
		File subSubDir = canonicalFile(subDir, "subSubDir");
		subSubDir.mkdirs();
		writeAFile(subSubDir);
		assertTrue(CommonUtils.deleteDirectory(dir));
	}

	/**
	 * routine to populate the variables common to all the hardLinkTreeTest_happyPath tests
	 */
	void hardLinkTreeTest_happyPath_setFiles() {
		// Files on source tree
		dir = new File("src/test/resources/dirForTestingHardLinkTree");
		subDir = canonicalFile(dir, "subDir");
		subSubDir = canonicalFile(subDir, "subSubDir");

		// Leaves on top level of source tree
		top1 = canonicalFile(dir, "one.txt");
		top2 = canonicalFile(dir, "two.txt");

		// Leaves on subDir level of source tree
		sub1 = canonicalFile(subDir, "one.txt");
		sub2 = canonicalFile(subDir, "two.txt");

		// Leaves on subSubDir level of source tree
		subsub1 = canonicalFile(subSubDir, "one.txt");
		subsub2 = canonicalFile(subSubDir, "two.txt");

		// corresponding Files on destination tree (in addition to the hard links and soft links)
		dir2 = canonicalFile(dir, "..", "dirForDestinationHardLinkedTree");
		copy1 = canonicalFile(dir2, "one.txt");
		copy2 = canonicalFile(dir2, "two.txt");
		hardlink1 = canonicalFile(dir2, "hl1.txt");
		softlink1 = canonicalFile(dir2, "sl2.txt");

		subdir1 = canonicalFile(dir2, "subDir", "one.txt");
		subdir2 = canonicalFile(dir2, "subDir", "two.txt");
		hardlink2 = canonicalFile(dir2, "subDir", "hl1.txt");
		softlink2 = canonicalFile(dir2, "subDir", "sl2.txt");

		subsubdir1 = canonicalFile(dir2, "subDir", "subSubDir", "one.txt");
		subsubdir2 = canonicalFile(dir2, "subDir", "subSubDir", "two.txt");
		hardlink3 = canonicalFile(dir2, "subDir", "subSubDir", "hl1.txt");
		softlink3 = canonicalFile(dir2, "subDir", "subSubDir", "sl2.txt");
	}

	/**
	 * Create a few files, including a hardlink and a soft link on each level, in a small tree, as follows:
	 *
	 * PART ONE -- set up -- create the files
	 * 1.txt: "one\n"
	 * 2.txt: "two\n"
	 * hl1.txt: --> hard link to 1.txt
	 * sl1.txt: --> soft link to 2.txt
	 * subDir: a subdirectory containing its own "subSubDir" directory, and a duplicate copy of the 4 files.
	 * subDir/subSubDir: a directory containing a third copy of the 1.txt and 2.txt files, with the links both
	 * 		pointing at the 1.txt and 2.txt files from the top-level directory.
	 *
	 * PART TWO -- duplicate the tree
	 * We call CommonUtils.txt to duplicate the tree.  We verify that the links are links and not simple copies
	 * of the files, by appending to the files and by verifying that both files contain the appended text.
	 *
	 * PART THREE -- pruning each tree (separately)
	 * Next, we confirm that deleting files (from either tree) is not reflected in the other tree,
	 *
	 * PART FOUR -- adding to each tree (separately)
	 * Finally, we confirm that adding new files (to either tree) are not reflected in the other tree.
	 */
	@Test
	@Order(1)
	void hardLinkTreeTest_happyPath_setup() throws IOException {
		hardLinkTreeTest_happyPath_setFiles();
		if (dir.exists()) {
			assertTrue(CommonUtils.deleteDirectory(dir), "Could not delete directory #1");
		}
		subSubDir.mkdirs();

		// populate leaves
		writeAFile(dir, "one.txt", ONE, false);
		writeAFile(dir, "two.txt", TWO, false);
		writeAFile(subDir, "one.txt", ONE, false);
		writeAFile(subDir, "two.txt", TWO, false);
		writeAFile(subSubDir, "one.txt", ONE, false);
		writeAFile(subSubDir, "two.txt", TWO, false);

		// create hard links and symbolic links
		Files.createLink(canonicalFile(dir, "hl1.txt").toPath(), top1.toPath());
		Files.createLink(canonicalFile(subDir, "hl1.txt").toPath(), sub1.toPath());
		Files.createLink(canonicalFile(subSubDir, "hl1.txt").toPath(), top1.toPath());

		Files.createSymbolicLink(canonicalFile(dir, "sl2.txt").toPath(), top2.toPath());
		Files.createSymbolicLink(canonicalFile(subDir, "sl2.txt").toPath(), sub2.toPath());
		Files.createSymbolicLink(canonicalFile(subSubDir, "sl2.txt").toPath(), top2.toPath());

		if (dir2.exists()) {
			assertTrue(CommonUtils.deleteDirectory(dir2), "Could not delete directory #2");
		}
		assertTrue(dir.exists(), "Directory " + dir + " should exist.");
		assertFalse(dir2.exists(), "Directory " + dir2 + " should not exist.");
	}

	@Test
	@Order(2)
	void hardLinkTreeTest_happyPath_hardLinkTree() throws IOException {
		hardLinkTreeTest_happyPath_setFiles();
		CommonUtils.hardLinkTree(dir, dir2);

		// assert that the files got created, and have the expected content
		assertTrue(copy1.exists(), "top-level one.txt should have been created.");
		assertEquals(ONE, readAFile(copy1), "unexpected file contents for copied one.txt");
		assertTrue(copy2.exists(), "top-level two.txt should have been created.");
		assertEquals(TWO, readAFile(copy2), "unexpected file contents for copied two.txt");
		assertTrue(hardlink1.exists(), "top-level hl1.txt should have been created.");
		assertEquals(ONE, readAFile(hardlink1), "unexpected file contents for copied hl1.txt");
		assertTrue(hardlink1.exists(), "top-level sl2.txt should have been created.");
		assertEquals(TWO, readAFile(softlink1), "unexpected file contents for copied sl2.txt");

		assertTrue(subdir1.exists(), "subDir-level one.txt should have been created.");
		assertEquals(ONE, readAFile(subdir1), "unexpected file contents for copied subDir/one.txt");
		assertTrue(subdir2.exists(), "subDir-level two.txt should have been created.");
		assertEquals(TWO, readAFile(subdir2), "unexpected file contents for copied subDir/two.txt");
		assertTrue(hardlink2.exists(), "subDir-level hl1.txt should have been created.");
		assertEquals(ONE, readAFile(hardlink2), "unexpected file contents for copied subDir/hl1.txt");
		assertTrue(hardlink2.exists(), "subDir-level sl2.txt should have been created.");
		assertEquals(TWO, readAFile(softlink2), "unexpected file contents for copied sl2.txt");

		assertTrue(subsubdir1.exists(), "subSubDir-level one.txt should have been created.");
		assertEquals(ONE, readAFile(subsubdir1), "unexpected file contents for copied subDir/subSubDir/one.txt");
		assertTrue(subsubdir2.exists(), "subSubDir-level two.txt should have been created.");
		assertEquals(TWO, readAFile(subsubdir2), "unexpected file contents for copied subDir/subSubDir/two.txt");
		assertTrue(hardlink3.exists(), "subSubDir-level hl1.txt should have been created.");
		assertEquals(ONE, readAFile(hardlink3), "unexpected file contents for copied subDir/subSubDir/hl1.txt");
		assertTrue(softlink3.exists(), "subSubDir-level hl1.txt should have been created.");
		assertEquals(TWO, readAFile(softlink3), "unexpected file contents for copied subDir/subSubDir/sl2.txt");
	}

	@Test
	@Order(3)
	void hardLinkTreeTest_happyPath_verifyTopLevelLinks() throws IOException {
		hardLinkTreeTest_happyPath_setFiles();
		// assert all linked files are correctly linked (by appending to one file and verifying that the
		// linked-to file contains that new content)
		new1 = verifyLinkedFiles(top1, copy1, ONE, "1\n");
		new2 = verifyLinkedFiles(top2, copy2, TWO, "2\n");
		new1 = verifyLinkedFiles(top1, hardlink1, new1, "3\n");
		new2 = verifyLinkedFiles(top2, softlink1, new2, "4\n");
	}

	@Test
	@Order(4)
	void hardLinkTreeTest_happyPath_verifyMiddleLevelLinks() throws IOException {
		hardLinkTreeTest_happyPath_setFiles();
		// assert all linked files are correctly linked (by appending to one file and verifying that the
		// linked-to file contains that new content)

		String middle1 = verifyLinkedFiles(sub1, subdir1, ONE, "5\n");
		String middle2 = verifyLinkedFiles(sub2, subdir2, TWO, "6\n");
		verifyLinkedFiles(canonicalFile(subDir, "hl1.txt"), hardlink2, middle1, "7\n");
		verifyLinkedFiles(canonicalFile(subDir, "sl2.txt"), softlink2, middle2, "8\n");
	}

	@Test
	@Order(5)
	void hardLinkTreeTest_happyPath_verifyBottomLevelLinks() throws IOException {
		hardLinkTreeTest_happyPath_setFiles();
		// assert all linked files are correctly linked (by appending to one file and verifying that the
		// linked-to file contains that new content)

		// reassign new1 and new2 by rereading in the files where they were set
		new1 = readAFile(hardlink1);
		new2 = readAFile(softlink1);

		verifyLinkedFiles(subsub1, subsubdir1, ONE, "9\n");
		verifyLinkedFiles(subsub2, subsubdir2, TWO, "10\n");
		verifyLinkedFiles(canonicalFile(subSubDir, "hl1.txt"), hardlink3, new1, "11\n");
		verifyLinkedFiles(canonicalFile(subSubDir, "sl2.txt"), softlink3, new2, "12\n");
	}

	@Test
	@Order(6)
	void hardLinkTreeTest_happyPath_newFilesOnlyAppearOnce() throws IOException {
		hardLinkTreeTest_happyPath_setFiles();
		// create more files under dir; confirm that they don't appear in dir2.  (And vice versa)
		File top3 = writeAFile(dir, "three.txt", "dummy file #1 contents", false);
		File sub3 = writeAFile(subDir, "three.txt", "dummy file #2 contents", false);
		File subsub3 = writeAFile(subSubDir, "three.txt", "dummy file #3 contents", false);

		File unexpectedFile = canonicalFile(dir2, "three.txt");
		assertFalse(unexpectedFile.exists(), "File #1 should not exist");
		unexpectedFile = canonicalFile(dir2, "subDir", "three.txt");
		assertFalse(unexpectedFile.exists(), "File #2 should not exist");
		unexpectedFile = canonicalFile(dir2, "subDir", "subSubDir", "three.txt");
		assertFalse(unexpectedFile.exists(), "File #3 should not exist");

		File copy4 = writeAFile(dir2, "four.txt", "dummy file #4 contents", false);
		File subdir4 = writeAFile(dir2, "subDir" + File.separator + "three.txt", "dummy file #5 contents", false);
		File subsubdir4 = writeAFile(dir2, "subDir" + File.separator + "subSubDir" + File.separator + "three.txt",
				"dummy file #6 contents", false);
		unexpectedFile = canonicalFile(dir, "four.txt");
		assertFalse(unexpectedFile.exists(), "File #4 should not exist");

		unexpectedFile = canonicalFile(dir, "subDir", "four.txt");
		assertFalse(unexpectedFile.exists(), "File #5 should not exist");
		unexpectedFile = canonicalFile(dir, "subDir", "subSubDir", "four.txt");
		assertFalse(unexpectedFile.exists(), "File #6 should not exist");
	}

	@Test
	@Order(7)
	void hardLinkTreeTest_happyPath_deletedFilesDontPropagate() throws IOException {
		hardLinkTreeTest_happyPath_setFiles();
		// delete all 4 files under subSubDir - verify that dir2 is unaffected.
		CommonUtils.deleteDirectory(subSubDir);
		assertTrue(subsubdir1.exists(), "File " + subsubdir1 + " should still exist.");
		assertTrue(subsubdir2.exists(), "File " + subsubdir2 + " should still exist.");
		assertTrue(hardlink3.exists(), "File " + hardlink3 + " should still exist.");
		assertTrue(softlink3.exists(), "File " + softlink3 + " should still exist.");

		// delete all files under dir2/subDir, and verify that dir is unaffected.
		CommonUtils.deleteDirectory(canonicalFile(dir2, "subDir"));
		assertTrue(sub1.exists(), "File " + sub1 + " should still exist.");
		assertTrue(sub2.exists(), "File " + sub2 + " should still exist.");
		File expectedFile = canonicalFile(subDir, "hl1.txt");
		assertTrue(expectedFile.exists(), "File " + expectedFile + " should still exist.");
		expectedFile = canonicalFile(subDir, "sl2.txt");
		assertTrue(expectedFile.exists(), "File " + expectedFile + " should still exist.");
	}

	@Test
	@Order(8)
	void hardLinkTreeTest_happyPath_cleanUp() {
		hardLinkTreeTest_happyPath_setFiles();
		// clean up everything
		assertTrue(CommonUtils.deleteDirectory(dir), "Could not delete directory " + dir);
		assertTrue(CommonUtils.deleteDirectory(dir2), "Could not delete directory " + dir2);
	}

	/**
	 * Try calling hardLinkTree() when either the source directory does not exist, or the destination does exist.
	 */
	@Test
	void hardLinkTreeTest_unhappyPaths() {
		File dir = new File("src/test/resources/dirForTestingHardLinkTree1");
		File dir2 = new File("src/test/resources/dirForTestingHardLinkTree2");
		Exception exception = assertThrows(UncheckedIOException.class, () -> CommonUtils.hardLinkTree(dir, dir2),
				"expected method to throw UncheckedIOException for non-existing source");
		assertEquals("java.io.IOException: " + dir.toString() + " does not exist or can not be accessed",
				exception.getMessage(), "expected error message not found");

		dir.mkdirs();
		dir2.mkdirs();
		exception = assertThrows(UncheckedIOException.class, () -> CommonUtils.hardLinkTree(dir, dir2),
				"expected method to throw UncheckedIOException for existing destination");
		assertEquals("java.io.IOException: " + dir2.toString() + " already exists", exception.getMessage(),
				"expected error message not found");
		assertTrue(CommonUtils.deleteDirectory(dir), "Could not delete directory " + dir);
		assertTrue(CommonUtils.deleteDirectory(dir2), "Could not delete directory " + dir2);
	}

	/**
	 * write a file into given dir
	 *
	 * @param dir
	 */
	void writeAFile(File dir) {
		try {
			canonicalFile(dir, "testFile").createNewFile();
		} catch (IOException ex) {
			System.out.println(ex.getMessage());
		}
	}

	/**
	 * write a file, with contents, into given dir and filename
	 *
	 * @param dir
	 *	directory in which to write the file
	 * @param filename
	 *	filename to create in that directory
	 * @param contents
	 *	contents to create in the file
	 * @param append
	 *	set to true if contents are to be appended to existing file
	 * @return the File that was created.
	 */
	File writeAFile(File dir, String filename, String contents, boolean append) throws IOException {
		File f = canonicalFile(dir, filename);
		if (!append) {
			f.createNewFile();
		}
		FileWriter fw = new FileWriter(f, append);
		fw.write(contents);
		fw.close();
		return f;
	}

	/**
	 * read the contents of a file
	 *
	 * @param file
	 *	File to read in
	 * @return
	 *	contents of the file
	 */
	String readAFile(File file) throws IOException {
		return String.join(System.lineSeparator(), Files.readAllLines(file.toPath())) + System.lineSeparator();
	}

	/**
	 * verify that two files are linked to each other.  Either hard or soft links are ok; test behavior doesn't really
	 * matter what kind of link it is: Verify that each file has the same content, then append "new line\n" to the
	 * first file, and confirm that the second file has it, and finally append the passed-in new String to the end
	 * of the second file, and confirm that the first line reflects that content.
	 *
	 * param file1
	 *	first file to be compared
	 * param file2
	 *	second file to be compared
	 * param expectedContent
	 *	expected content of both files (initially)
	 * param outputText
	 *	what to append(after "new line\n") to each file
	 * @return expected content of each file when all is done
	 * @throws IllegalStateException if the file content isn't what was specified.
	 */
	String verifyLinkedFiles(final File file1, final File file2, String expectedContent, final String outputText)
			throws IOException {
		assertEquals(expectedContent, readAFile(file1), "unexpected file contents for " + file1.toString());
		assertEquals(expectedContent, readAFile(file2), "unexpected file contents for " + file2.toString());
		expectedContent += NEW_LINE;
		writeAFile(new File("/"), file1.toString(), NEW_LINE, true);
		assertEquals(expectedContent, readAFile(file1), "unexpected file contents for " + file1.toString() + " #2");
		assertEquals(expectedContent, readAFile(file2), "unexpected file contents for " + file2.toString() + " #2");
		expectedContent += outputText;
		writeAFile(new File("/"), file2.toString(), outputText, true);
		assertEquals(expectedContent, readAFile(file1), "unexpected file contents for " + file1.toString() + " #3");
		assertEquals(expectedContent, readAFile(file2), "unexpected file contents for " + file2.toString() + " #3");
		return expectedContent;
	}
}
