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
package com.swirlds.common.io.settings;

/** Settings for address books and utilities that deal with address books. */
public interface TemporaryFileSettings {

    /**
     * Get the location where temporary files are stored.
     *
     * <p>WARNING! Any files in this location are deleted at boot time. It's ok to set the temporary
     * file location to something like "/myTemporaryFiles" or "~/myTemporaryFiles". If it's set to
     * something like "/" or "~/", EVERYTHING IN THOSE DIRECTORIES WILL BE DELETED!
     *
     * @return the location where temporary files are stored
     */
    String getTemporaryFilePath();
}
