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
package com.swirlds.config.impl.sources;

import com.swirlds.common.utility.CommonUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * A {@link com.swirlds.config.api.source.ConfigSource} implementation that can be used to provide
 * values from files based on the old syntax of swirlds settings.txt files.
 *
 * @deprecated should be removed once the old fileformat is not used anymore
 */
@Deprecated(forRemoval = true)
public class LegacyFileConfigSource extends AbstractConfigSource {

    private final Properties internalProperties;

    private final Path filePath;

    /**
     * Creates a new instance based on a file
     *
     * @param filePath the file that contains the config properties
     * @throws IOException if the file can not be loaded or parsed
     */
    public LegacyFileConfigSource(final Path filePath) throws IOException {
        this.filePath = CommonUtils.throwArgNull(filePath, "filePath");
        this.internalProperties = loadSettings(filePath.toFile());
    }

    private Properties loadSettings(final File settingsFile) throws IOException {
        final Properties properties = new Properties();
        try (Stream<String> stream = Files.lines(settingsFile.toPath())) {
            stream.map(
                            line -> {
                                final int pos = line.indexOf("#");
                                if (pos > -1) {
                                    return line.substring(0, pos).trim();
                                }
                                return line.trim();
                            })
                    .filter(line -> !line.isEmpty())
                    .filter(line -> splitLine(line).length > 0) // ignore empty lines
                    .forEach(
                            line -> {
                                final String[] pars = splitLine(line);
                                try {
                                    if (pars.length > 1) {
                                        properties.put(pars[0], pars[1]);
                                    } else {
                                        properties.put(pars[0], "");
                                    }
                                } catch (final Exception e) {
                                    throw new IllegalStateException(
                                            "syntax error in settings file", e);
                                }
                            });
        }
        return properties;
    }

    private static String[] splitLine(final String line) {
        return Arrays.stream(line.split(",")).map(e -> e.trim()).toArray(i -> new String[i]);
    }

    /** {@inheritDoc} */
    @Override
    protected Properties getInternalProperties() {
        return internalProperties;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "Swirlds Legacy Settings loader for " + filePath;
    }

    /** {@inheritDoc} */
    @Override
    public int getOrdinal() {
        return ConfigSourceOrdinalConstants.LEGACY_PROPERTY_FILE_ORDINAL;
    }
}
