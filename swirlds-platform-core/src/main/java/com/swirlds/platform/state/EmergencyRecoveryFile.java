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

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.Settings;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bouncycastle.util.encoders.Hex;

/**
 * Defines all data related to the emergency recovery file and how it is formatted.
 *
 * @param round the round number of the state this file is for
 * @param hash the hash of the state this file is for
 */
public record EmergencyRecoveryFile(long round, Hash hash) {

    private static final String OUTPUT_FILENAME = "emergencyRecovery.csv";
    private static final String INPUT_FILENAME =
            Settings.getInstance().getEmergencyRecoveryStateFileName();
    private static final String COMMA = ",";

    /**
     * Write the data in this record to a csv file at the specified directory.
     *
     * @param directory the directory to write to. Must exist and be writable.
     * @throws IOException if an exception occurs creating or writing to the file
     */
    public void write(final Path directory) throws IOException {
        final Path csvToWrite = directory.resolve(OUTPUT_FILENAME);

        try (final BufferedWriter file = new BufferedWriter(new FileWriter(csvToWrite.toFile()))) {
            file.write(String.valueOf(round));
            file.write(COMMA);
            file.write(hash.toString());
            file.flush();
        }
    }

    /**
     * Creates a record with the data contained in the emergency recovery file in the directory
     * specified, or null if the file does not exist.
     *
     * @param directory the directory containing the emergency recovery file. Must exist and be
     *     readable.
     * @return a new record containing the emergency recovery data in the file, or null if no
     *     emergency recovery file exists
     * @throws IOException if an exception occurs reading from the file, or the file content is not
     *     properly formatted
     * @throws NumberFormatException if an exception occurs parsing the round number from the file
     */
    public static EmergencyRecoveryFile read(final Path directory) throws IOException {
        final Path csvToRead = directory.resolve(INPUT_FILENAME);
        if (!Files.exists(csvToRead)) {
            return null;
        }

        try (final BufferedReader file = new BufferedReader(new FileReader(csvToRead.toFile()))) {
            final String line = file.readLine();
            final String[] values = line.split(COMMA);
            if (values.length != 2) {
                throw new IOException(
                        "Invalid emergency recovery file contents. Expected 2 values in CSV format"
                                + " but was {}"
                                + line);
            }
            final long round = Long.parseLong(values[0]);
            final Hash hash = new Hash(Hex.decode(values[1]));
            return new EmergencyRecoveryFile(round, hash);
        }
    }
}
