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
import picocli.CommandLine;

/** A mixin that adds a parameter for the event stream location. */
public final class EventStreamParameter extends ParameterizedClass {

    private Path eventStreamDirectory;

    private EventStreamParameter() {}

    @CommandLine.Option(
            names = {"-e", "--event-stream"},
            required = true,
            description = "The path to a directory tree containing event stream files.")
    private void setEventStreamDirectory(final Path eventStreamDirectory) {
        this.eventStreamDirectory = pathMustExist(eventStreamDirectory.toAbsolutePath());
    }

    /** Get the event stream directory. */
    public Path getEventStreamDirectory() {
        return eventStreamDirectory;
    }
}