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
package com.swirlds.platform.test;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.test.merkle.util.MerkleTestUtils;
import com.swirlds.common.test.state.DummySwirldState2;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Signed State Synchronization Tests")
public class SignedStateSynchronizationTests {

    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        // This hack is required to run this test from intellij
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(State.class, State::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(PlatformState.class, PlatformState::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(DummySwirldState2.class, DummySwirldState2::new));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Signed State Synchronization")
    public void SignedStateSynchronization() throws Exception {

        SettingsCommon.transactionMaxBytes = 1024;

        SignedState state = SignedStateUtils.randomSignedState(1234);
        state.getState().setHash(null); // TODO root has a hash but other parts do not...
        MerkleTestUtils.hashAndTestSynchronization(null, state.getState());
    }
}
