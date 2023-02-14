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
package com.swirlds.platform.cli;

import static com.swirlds.platform.util.BootstrapUtils.setupConstructableRegistry;

import com.swirlds.cli.commands.EventStreamCommand;
import com.swirlds.cli.parameters.EventStreamParameter;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.event.report.EventStreamReportingTool;
import picocli.CommandLine;

@CommandLine.Command(
        name = "info",
        mixinStandardHelpOptions = true,
        description = "Read event stream files and print an informational report.")
@SubcommandOf(EventStreamCommand.class)
public final class EventStreamInfoCommand extends AbstractCommand {

    @CommandLine.Mixin private EventStreamParameter eventStreamParameter;

    private long firstRound = -1;

    @CommandLine.Option(
            names = {"-f", "--first-round"},
            description = "The first to be considered.")
    private void setFirstRound(final long firstRound) {
        this.firstRound = firstRound;
    }

    private EventStreamInfoCommand() {}

    @Override
    public Integer call() throws Exception {
        setupConstructableRegistry();
        System.out.println(
                EventStreamReportingTool.createReport(
                        eventStreamParameter.getEventStreamDirectory(), firstRound, true));
        return 0;
    }
}
