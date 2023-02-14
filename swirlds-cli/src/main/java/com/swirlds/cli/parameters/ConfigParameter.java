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
package com.swirlds.cli.parameters;

import com.swirlds.cli.utility.ParameterizedClass;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine;

/** A mixin that adds a parameter for configuration files. */
public final class ConfigParameter extends ParameterizedClass {

    /** Load configuration from these files. */
    private List<Path> configurationPaths = List.of();

    private ConfigParameter() {}

    /** Set the configuration paths. */
    @CommandLine.Option(
            names = {"-c", "--config"},
            description =
                    "A path to where a configuration file can be found. If not provided then"
                            + " defaults are used.")
    private void setConfigurationPath(final List<Path> configurationPaths) {
        configurationPaths.forEach(this::pathMustExist);
        this.configurationPaths = configurationPaths;
    }

    /** Get a list of configuration paths. */
    public List<Path> getConfigurationPaths() {
        return configurationPaths;
    }
}
