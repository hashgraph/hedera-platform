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
package com.swirlds.platform.gui.hashgraph.internal;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.sync.ShadowGraph;

/**
 * A {@link ShadowgraphGuiSource} where the {@link ShadowGraph} is set in the constructor and never
 * changes
 */
public class FinalShadowgraphGuiSource implements ShadowgraphGuiSource {
    private final ShadowGraph shadowGraph;
    private final AddressBook addressBook;

    public FinalShadowgraphGuiSource(final ShadowGraph shadowGraph, final AddressBook addressBook) {
        this.shadowGraph = shadowGraph;
        this.addressBook = addressBook;
    }

    @Override
    public AddressBook getAddressBook() {
        return addressBook;
    }

    @Override
    public ShadowGraph getShadowGraph() {
        return shadowGraph;
    }
}
