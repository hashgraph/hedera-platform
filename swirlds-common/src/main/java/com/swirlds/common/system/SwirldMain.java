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
package com.swirlds.common.system;

import com.swirlds.config.api.Configuration;

/**
 * To implement a swirld, create a class that implements SwirldMain. Its constructor should have no
 * parameters, and its run() method should run until the user quits the swirld.
 */
public interface SwirldMain extends Runnable {

    /**
     * Pass in a copy of the configuration.
     *
     * @param configuration configuration for this node
     */
    default void setConfiguration(final Configuration configuration) {
        // override if needed
    }

    /**
     * This should only be called by the Platform. It is passed a reference to the platform, so the
     * SwirldMain will know who to call. (This is dependency injection).
     *
     * <p>Any changes necessary to initialize {@link SwirldState} should be made in {@link
     * SwirldState#init(Platform, SwirldDualState, InitTrigger, SoftwareVersion)}
     *
     * @param platform the Platform that instantiated this SwirldMain
     * @param selfId the ID number for this member (myself)
     */
    void init(Platform platform, NodeId selfId);

    /**
     * This is where the app manages the screen and I/O, and creates transactions as needed. It
     * should return when the user quits the app, but may also return earlier.
     */
    @Override
    void run();

    /**
     * Instantiate and return a SwirldState object that corresponds to this SwirldMain object.
     * Typically, if class ExampleMain implements SwirldMain, then newState will return an object of
     * class ExampleMain.
     *
     * @return the newly instantiated SwirldState object
     */
    SwirldState newState();

    /**
     * Get the current software version.
     *
     * <ul>
     *   <li>This version should not change except when a node is restarted.
     *   <li>Every time a node restarts, the supplied version must be greater or equal to the
     *       previous version.
     *   <li>Every supplied version for a particular app should have the same type. Failure to
     *       follow this restriction may lead to miscellaneous {@link ClassCastException}s.
     * </ul>
     *
     * @return the current version
     */
    SoftwareVersion getSoftwareVersion();
}
