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

import static com.swirlds.common.test.RandomUtils.randomInstant;
import static com.swirlds.common.test.RandomUtils.randomPositiveLong;
import static com.swirlds.platform.test.PlatformStateUtils.randomPlatformState;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.test.state.DummySwirldState2;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.MinGenInfo;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.event.RandomEventUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SignedStateUtils {
    private static final int DEFAULT_AB_SIZE = 10;
    private static final int DEFAULT_MIN_GEN_LIST_SIZE = 25;

    public static SignedState randomSignedState(long seed) {
        return randomSignedState(new Random(seed));
    }

    public static SignedState randomSignedState(Random random) {
        int nodeNumber = DEFAULT_AB_SIZE;

        SwirldState state = new DummySwirldState2();
        State root = new State();
        root.setSwirldState(state);
        root.setPlatformState(randomPlatformState(random, false));

        long lastRoundReceived = randomPositiveLong(random);
        long numEventsCons = randomPositiveLong(random);
        Hash hashEventsCons = RandomUtils.randomHash(random);
        final AddressBook addressBook =
                new RandomAddressBookGenerator(random)
                        .setSize(nodeNumber)
                        .setStakeDistributionStrategy(
                                RandomAddressBookGenerator.StakeDistributionStrategy.BALANCED)
                        .build();
        EventImpl[] events = new EventImpl[nodeNumber];
        for (int i = 0; i < nodeNumber; i++) {
            events[i] = RandomEventUtils.randomEvent(random, i, null, null);
        }
        Instant consensusTimestamp = randomInstant(random);
        boolean shouldSaveToDisk = random.nextBoolean();
        final List<MinGenInfo> minGenInfo = new ArrayList<>(DEFAULT_MIN_GEN_LIST_SIZE);
        for (int i = 0; i < DEFAULT_MIN_GEN_LIST_SIZE; i++) {
            minGenInfo.add(new MinGenInfo(randomPositiveLong(random), randomPositiveLong(random)));
        }
        SignedState signedState =
                new SignedState(
                        root,
                        lastRoundReceived,
                        numEventsCons,
                        hashEventsCons,
                        addressBook,
                        events,
                        consensusTimestamp,
                        shouldSaveToDisk,
                        minGenInfo,
                        SoftwareVersion.NO_VERSION);
        signedState.getState().setHash(RandomUtils.randomHash(random));
        return signedState;
    }
}
