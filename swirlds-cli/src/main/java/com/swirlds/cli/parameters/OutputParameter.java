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
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine;

/** A mixin that adds a parameter for the output location. */
public final class OutputParameter extends ParameterizedClass {

    private Path outputPath = Path.of("./out");

    private OutputParameter() {}

    /** Set the output path. */
    @CommandLine.Option(
            names = {"-o", "--out"},
            description =
                    "The location where output is written. Default = './out'. "
                            + "Must not exist prior to invocation.")
    private void setOutputPath(final Path outputPath) {
        this.outputPath = outputPath;
    }

    /** Get the output path. */
    public Path getOutputPath() {
        final Path absoluteOut = outputPath.toAbsolutePath();
        if (Files.exists(absoluteOut)) {
            throw this.buildParameterException("Output path " + absoluteOut + " already exists!");
        }
        return absoluteOut;
    }
}
