/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.platform.state;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.RandomUtils;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class EmergencyRecoveryFileTests {

    private static final String FILENAME = "emergencyRecovery.csv";

    @TempDir Path tmpDir;

    @Test
    void testReadWrite() throws IOException {
        final Random r = RandomUtils.getRandomPrintSeed();
        final EmergencyRecoveryFile toWrite = createRecoveryFile(r);
        toWrite.write(tmpDir);

        final EmergencyRecoveryFile readIn = EmergencyRecoveryFile.read(tmpDir);

        assertNotNull(readIn, "emergency round data should not be null");
        assertEquals(toWrite.round(), readIn.round(), "round does not match");
        assertEquals(toWrite.hash(), readIn.hash(), "hash does not match");
    }

    @Test
    void testReadFileWithTooManyValuesThrows() {
        final Random r = RandomUtils.getRandomPrintSeed();
        assertDoesNotThrow(() -> writeFileWithTooManyFields(r), "error writing test file");
        assertThrows(
                IOException.class,
                () -> EmergencyRecoveryFile.read(tmpDir),
                "Reading an invalid file should throw");
    }

    @Test
    void testReadFileWithTooFewValuesThrows() {
        final Random r = RandomUtils.getRandomPrintSeed();
        assertDoesNotThrow(() -> writeFileWithTooFewFields(r), "error writing test file");
        assertThrows(
                IOException.class,
                () -> EmergencyRecoveryFile.read(tmpDir),
                "Reading an invalid file should throw");
    }

    @Test
    void testFileDoesNotExist() throws IOException {
        assertNull(
                EmergencyRecoveryFile.read(tmpDir),
                "Reading from a file that does not exist should return null");
    }

    private EmergencyRecoveryFile createRecoveryFile(final Random r) {
        return new EmergencyRecoveryFile(r.nextLong(), randomHash(r));
    }

    private void writeFileWithTooManyFields(final Random r) throws IOException {
        try (final BufferedWriter file =
                new BufferedWriter(new FileWriter(tmpDir.resolve(FILENAME).toFile()))) {
            file.write(r.nextLong() + "");
            file.write(",");
            file.write(randomHash(r).toString());
            file.write(",");
            file.write(r.nextLong() + "");
        }
    }

    private void writeFileWithTooFewFields(final Random r) throws IOException {
        try (final BufferedWriter file =
                new BufferedWriter(new FileWriter(tmpDir.resolve(FILENAME).toFile()))) {
            file.write(r.nextLong() + "");
        }
    }

    private static Hash randomHash(final Random r) {
        final byte[] bytes = new byte[48];
        r.nextBytes(bytes);
        return new Hash(bytes);
    }
}
