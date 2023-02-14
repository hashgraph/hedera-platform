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
package com.swirlds.common.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Config that control the SignedStateManager and SignedStateFileManager behaviors.
 *
 * @param savedStateDirectory The directory where states are saved. This is relative to the current
 *     working directory, unless the provided path begins with "/", in which case it will be
 *     interpreted as an absolute path.
 * @param mainClassNameOverride Typically, the fully qualified name of the application class
 *     implementing {@link com.swirlds.common.system.SwirldMain SwirldMain} is used as a directory
 *     name when saving signed states. If this property is not the empty string then it overrides
 *     the main class name for signed states.
 * @param cleanSavedStateDirectory If true, clean out all data in the {@link #savedStateDirectory}
 *     except for the previously saved state.
 * @param stateSavingQueueSize The number of states permitted to sit in the signed state file
 *     manager's queue of states being written. If this queue backs up then some states may not be
 *     written to disk.
 * @param saveStatePeriod The frequency of writes of a state to disk every this many seconds (0 to
 *     never write).
 * @param signedStateDisk Keep at least this many of the old complete signed states on disk. This
 *     should be at least 2 so that we don't delete an old state while a new one is in the process
 *     of writing to disk. set to 0 to not keep any states to disk.
 * @param dumpStateOnISS If true, save the state to disk when an ISS is detected. May negatively
 *     affect the performance of the node where the ISS occurs. This feature is for debugging
 *     purposes and should not be active in production systems.
 * @param dumpStateOnFatal If true, then save the state to disk when there is a fatal exception.
 * @param secondsBetweenISSDumps If one ISS is detected, it is likely that others will be detected
 *     shortly afterwards. Specify the minimum time, in seconds, that must transpire after dumping a
 *     state before another state dump is permitted. Ignored if dumpStateOnISS is false.
 * @param stateDeletionErrorLogFrequencySeconds If there are problems with state lifecycle then
 *     write errors to the log at most once per this period of time.
 * @param enableHashStreamLogging When enabled, hashes for the nodes are logged per round.
 * @param backgroundHashChecking If true then a single background thread is used to do validation of
 *     signed state hashes. Validation is on a best effort basis. If it takes too long to validate a
 *     state then new states will be skipped.
 * @param debugHashDepth When logging debug information about the hashes in a merkle tree, do not
 *     display hash information for nodes deeper than this.
 * @param maxAgeOfFutureStateSignatures It's possible to receive state signatures before it's time
 *     to process the round signed by the signature. This is the maximum number of rounds, in the
 *     future, for which a node will accept a state signature.
 */
@ConfigData("state")
public record StateConfig(
        @ConfigProperty(defaultValue = "data/saved") String savedStateDirectory,
        @ConfigProperty(defaultValue = "") String mainClassNameOverride,
        @ConfigProperty(defaultValue = "false") boolean cleanSavedStateDirectory,
        @ConfigProperty(defaultValue = "20") int stateSavingQueueSize,
        @ConfigProperty(defaultValue = "0") int saveStatePeriod,
        @ConfigProperty(defaultValue = "3") int signedStateDisk,
        @ConfigProperty(defaultValue = "false") boolean dumpStateOnISS,
        @ConfigProperty(defaultValue = "true") boolean dumpStateOnFatal,
        @ConfigProperty(defaultValue = "21600") double secondsBetweenISSDumps,
        @ConfigProperty(defaultValue = "60") int stateDeletionErrorLogFrequencySeconds,
        @ConfigProperty(defaultValue = "true") boolean enableHashStreamLogging,
        @ConfigProperty(defaultValue = "false") boolean backgroundHashChecking,
        @ConfigProperty(defaultValue = "5") int debugHashDepth,
        @ConfigProperty(defaultValue = "1000") int maxAgeOfFutureStateSignatures) {

    /**
     * Get the main class name that should be used for signed states.
     *
     * @param defaultMainClassName the default main class name derived from the {@link
     *     com.swirlds.common.system.SwirldMain SwirldMain} name.
     * @return the main class name that should be used for signed states
     */
    public String getMainClassName(final String defaultMainClassName) {
        return mainClassNameOverride.isEmpty() ? defaultMainClassName : mainClassNameOverride;
    }
}
