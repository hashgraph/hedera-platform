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

import static com.swirlds.platform.recovery.EventRecoveryWorkflow.recoverState;

import com.swirlds.cli.commands.EventStreamCommand;
import com.swirlds.cli.parameters.ConfigParameter;
import com.swirlds.cli.parameters.EventStreamParameter;
import com.swirlds.cli.parameters.OutputParameter;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import java.nio.file.Path;
import picocli.CommandLine;

@CommandLine.Command(
        name = "recover",
        mixinStandardHelpOptions = true,
        description = "Build a state file by replaying events from an event stream.")
@SubcommandOf(EventStreamCommand.class)
public final class EventStreamRecoverCommand extends AbstractCommand {

    @CommandLine.Mixin private ConfigParameter configParameter;

    @CommandLine.Mixin private OutputParameter outputParameter;

    @CommandLine.Mixin private EventStreamParameter eventStreamParameter;

    private String appMainName;
    private Path initialSignedState;
    private long selfId;
    private boolean ignorePartialRounds;
    private long finalRound = -1;

    private EventStreamRecoverCommand() {}

    @CommandLine.Option(
            names = {"-n", "--main-name"},
            required = true,
            description = "The fully qualified name of the application's main class.")
    private void setAppMainName(final String appMainName) {
        this.appMainName = appMainName;
    }

    @CommandLine.Option(
            names = {"-s", "--initial-state"},
            required = true,
            description =
                    "The path to the initial SignedState.swh file."
                            + "Events will be replayed on top of this state file.")
    private void setInitialSignedState(final Path initialSignedState) {
        this.initialSignedState = pathMustExist(initialSignedState.toAbsolutePath());
    }

    @CommandLine.Option(
            names = {"-i", "--id"},
            required = true,
            description =
                    "The ID of the node that is being used to recover the state. "
                            + "This node's keys should be available locally.")
    private void setSelfId(final long selfId) {
        this.selfId = selfId;
    }

    @CommandLine.Option(
            names = {"-p", "--ignore-partial"},
            description =
                    "if set then any partial rounds at the end of the event stream are ignored."
                            + " Default = false")
    private void setIgnorePartialRounds(final boolean ignorePartialRounds) {
        this.ignorePartialRounds = ignorePartialRounds;
    }

    @CommandLine.Option(
            names = {"-f", "--final-round"},
            defaultValue = "-1",
            description =
                    "The last round that should be applied to the state, any higher rounds are"
                            + " ignored. Default = apply all available rounds")
    private void setFinalRound(final long finalRound) {
        this.finalRound = finalRound;
    }

    @Override
    public Integer call() throws Exception {
        recoverState(
                initialSignedState,
                configParameter.getConfigurationPaths(),
                eventStreamParameter.getEventStreamDirectory(),
                appMainName,
                !ignorePartialRounds,
                finalRound,
                outputParameter.getOutputPath(),
                selfId);
        return 0;
    }
}
