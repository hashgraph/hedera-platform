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
package com.swirlds.platform;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.RandomUtils.randomHash;
import static com.swirlds.common.test.RandomUtils.randomSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.platform.state.signed.SigInfo;
import com.swirlds.platform.state.signed.SigSet;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SigSet Tests")
class SigSetTests {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void completionTest(final boolean evenStaking) {
        final Random random = getRandomPrintSeed();

        final int iterations = 100;

        for (int nodeCount = 0; nodeCount < iterations; nodeCount++) {
            final AddressBook addressBook =
                    new RandomAddressBookGenerator()
                            .setStakeDistributionStrategy(
                                    evenStaking
                                            ? RandomAddressBookGenerator.StakeDistributionStrategy
                                                    .BALANCED
                                            : RandomAddressBookGenerator.StakeDistributionStrategy
                                                    .GAUSSIAN)
                            .setSequentialIds(true)
                            .setSize(nodeCount)
                            .build();

            final long totalStake = addressBook.getTotalStake();
            int currentStake = 0;

            final SigSet sigSet = new SigSet(addressBook);

            for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {

                final boolean completeBefore = currentStake * 2L > totalStake;
                assertEquals(sigSet.isComplete(), completeBefore, "incorrect completeness");

                sigSet.addSigInfo(
                        new SigInfo(
                                0,
                                addressBook.getId(nodeIndex),
                                randomHash(random),
                                randomSignature(random)));

                currentStake += addressBook.getAddress(addressBook.getId(nodeIndex)).getStake();

                final boolean completeAfter = currentStake * 2L > totalStake;
                assertEquals(sigSet.isComplete(), completeAfter, "incorrect completeness");
            }
        }
    }
}
