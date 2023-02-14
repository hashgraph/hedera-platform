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

import com.swirlds.cli.commands.StateCommand;
import com.swirlds.cli.parameters.ConfigParameter;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateComparison;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import com.swirlds.platform.util.BootstrapUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "compare",
        mixinStandardHelpOptions = true,
        description =
                "Compare two signed states for differences. Useful for debugging ISS incidents.")
@SubcommandOf(StateCommand.class)
public final class CompareStatesCommand extends AbstractCommand {

    /** The path to the first state being compared. */
    private Path stateAPath;

    /** The path to the first state being compared. */
    private Path stateBPath;

    /** The maximum number of nodes to print. */
    private int nodeLimit = 100;

    /** If true then do a deep comparison of the states. */
    private boolean deepComparison = false;

    @CommandLine.Mixin private ConfigParameter configParameter;

    private CompareStatesCommand() {}

    /** Set the path to state A. */
    @CommandLine.Option(
            names = {"--stateA"},
            required = true,
            description = "the path to the first SignedState.swh that is being compared")
    private void setStateAPath(final Path stateAPath) {
        this.stateAPath = pathMustExist(stateAPath.toAbsolutePath());
    }

    /** Set the path to state B. */
    @CommandLine.Option(
            names = {"--stateB"},
            required = true,
            description = "the path to the second SignedState.swh that is being compared")
    private void setStateBPath(final Path stateBPath) {
        this.stateBPath = pathMustExist(stateBPath.toAbsolutePath());
    }

    @CommandLine.Option(
            names = {"--limit"},
            description = "the maximum number of mismatched merkle nodes to print")
    private void setNodeLimit(final int nodeLimit) {
        if (nodeLimit <= 0) {
            throw new CommandLine.ParameterException(
                    getSpec().commandLine(), "node limit must be non-zero positive");
        }
        this.nodeLimit = nodeLimit;
    }

    @CommandLine.Option(
            names = {"--deep"},
            description =
                    "if set then do a deep comparison of the states, "
                            + "useful if internal hashes have been corrupted")
    private void setDeepComparison(final boolean deepComparison) {
        this.deepComparison = deepComparison;
    }

    /**
     * Load a state from disk and hash it.
     *
     * @param statePath the location of the state to load
     * @return the loaded state
     */
    private static SignedState loadAndHashState(final Path statePath) throws IOException {
        System.out.println("Loading state from " + statePath);
        final SignedState signedState =
                SignedStateFileReader.readStateFile(statePath).signedState();
        System.out.println("Hashing state");
        try {
            MerkleCryptoFactory.getInstance().digestTreeAsync(signedState.getState()).get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RuntimeException("unable to hash state", e);
        }

        return signedState;
    }

    /**
     * This method is called after command line input is parsed.
     *
     * @return return code of the program
     */
    @Override
    public Integer call() throws IOException {
        BootstrapUtils.loadConfiguration(configParameter.getConfigurationPaths());
        BootstrapUtils.setupConstructableRegistry();

        final SignedState stateA = loadAndHashState(stateAPath);
        final SignedState stateB = loadAndHashState(stateBPath);

        SignedStateComparison.printMismatchedNodes(
                SignedStateComparison.mismatchedNodeIterator(
                        stateA.getState(), stateB.getState(), deepComparison),
                nodeLimit);

        return 0;
    }
}
