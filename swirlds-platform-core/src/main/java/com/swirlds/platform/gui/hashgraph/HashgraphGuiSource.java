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
package com.swirlds.platform.gui.hashgraph;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.PlatformEvent;

/** Provides the {@link HashgraphGui} information it needs to render an image of the hashgraph */
public interface HashgraphGuiSource {

    /**
     * @return the maximum generation of all events this source has
     */
    long getMaxGeneration();

    /**
     * Get events to be displayed by the GUI
     *
     * @param startGeneration the start generation of events returned
     * @param numGenerations the number of generations to be returned
     * @return an array of requested events
     */
    PlatformEvent[] getEvents(final long startGeneration, final int numGenerations);

    /**
     * Get the Address Book
     *
     * @return AddressBook
     */
    AddressBook getAddressBook();

    /**
     * @return true if the source is ready to return data
     */
    boolean isReady();

    /**
     * @return the number of members in the address book
     */
    default int getNumMembers() {
        return getAddressBook().getSize();
    }
}
