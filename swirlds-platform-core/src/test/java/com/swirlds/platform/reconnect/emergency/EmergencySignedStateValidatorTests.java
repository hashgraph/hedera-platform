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
package com.swirlds.platform.reconnect.emergency;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.reconnect.ReconnectException;
import com.swirlds.platform.state.EmergencyRecoveryFile;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.SignedState;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class EmergencySignedStateValidatorTests {

    private static final long STAKE_PER_NODE = 100L;
    private static final int NUM_NODES = 4;
    private static final long EMERGENCY_ROUND = 20L;
    private static Future<Boolean> trueFuture;
    private Crypto crypto = mock(Crypto.class);
    private AddressBook addressBook;
    private EmergencySignedStateValidator validator;

    @BeforeEach
    void setup() throws ExecutionException, InterruptedException {
        addressBook =
                new RandomAddressBookGenerator()
                        .setSize(NUM_NODES)
                        .setAverageStake(STAKE_PER_NODE)
                        .setStakeDistributionStrategy(
                                RandomAddressBookGenerator.StakeDistributionStrategy.BALANCED)
                        .setSequentialIds(true)
                        .build();

        trueFuture = mock(Future.class);
        when(trueFuture.get()).thenReturn(true);

        // Make all signatures on the state are valid
        when(crypto.verifySignatureParallel(any(), any(), any(), any())).thenReturn(trueFuture);
    }

    @DisplayName("A state for a round earlier than request should always fail validation")
    @Test
    void stateTooOld() {
        final SignedState oldState =
                new RandomSignedStateGenerator()
                        .setAddressBook(addressBook)
                        .setRound(EMERGENCY_ROUND - 1)
                        .build();

        validator =
                new EmergencySignedStateValidator(
                        crypto,
                        new EmergencyRecoveryFile(EMERGENCY_ROUND, RandomUtils.randomHash()));

        assertThrows(
                ReconnectException.class,
                () -> validator.validate(oldState, addressBook),
                "A state older than the state requested should throw an exception");
    }

    @DisplayName("A state for the requested round but a different hash should fail validation")
    @Test
    void stateMatchesRoundButNotHash() {
        final SignedState stateWithWrongHash =
                new RandomSignedStateGenerator()
                        .setAddressBook(addressBook)
                        .setRound(EMERGENCY_ROUND)
                        .build();
        stateWithWrongHash.getState().setHash(RandomUtils.randomHash());

        validator =
                new EmergencySignedStateValidator(
                        crypto,
                        new EmergencyRecoveryFile(EMERGENCY_ROUND, RandomUtils.randomHash()));

        assertThrows(
                ReconnectException.class,
                () -> validator.validate(stateWithWrongHash, addressBook),
                "A state with the requested round but a different hash should throw an exception");
    }

    @DisplayName("A state for the requested round and hash should pass validation")
    @Test
    void stateMatchesRoundAndHash() {
        final Hash hash = RandomUtils.randomHash();
        final SignedState matchingState =
                new RandomSignedStateGenerator()
                        .setAddressBook(addressBook)
                        .setRound(EMERGENCY_ROUND)
                        .build();
        matchingState.getState().setHash(hash);

        validator =
                new EmergencySignedStateValidator(
                        crypto, new EmergencyRecoveryFile(EMERGENCY_ROUND, hash));

        assertDoesNotThrow(
                () -> validator.validate(matchingState, addressBook),
                "A state with the requested round and hash should pass validation");
    }

    @DisplayName("A state for a later round signed by a majority should pass validation")
    @Test
    void laterStateSignedByMajority() {
        final List<Long> majorityStakeNodes =
                IntStream.range(0, NUM_NODES - 1).mapToLong(i -> (long) i).boxed().toList();

        final SignedState laterState =
                new RandomSignedStateGenerator()
                        .setAddressBook(addressBook)
                        .setRound(EMERGENCY_ROUND + 1)
                        .setSigningNodeIds(majorityStakeNodes)
                        .build();

        validator =
                new EmergencySignedStateValidator(
                        crypto,
                        new EmergencyRecoveryFile(EMERGENCY_ROUND, RandomUtils.randomHash()));

        assertDoesNotThrow(
                () -> validator.validate(laterState, addressBook),
                "A later state signed by a majority of stake should pass validation");
    }

    @DisplayName("A state for a later round signed by less than a majority should fail validation")
    @Test
    void laterStateNotSignedByMajority() {
        final List<Long> lessThanMajorityStakeNodes =
                IntStream.range(0, NUM_NODES / 2).mapToLong(i -> (long) i).boxed().toList();

        final SignedState laterState =
                new RandomSignedStateGenerator()
                        .setAddressBook(addressBook)
                        .setRound(EMERGENCY_ROUND + 1)
                        .setSigningNodeIds(lessThanMajorityStakeNodes)
                        .build();

        validator =
                new EmergencySignedStateValidator(
                        crypto,
                        new EmergencyRecoveryFile(EMERGENCY_ROUND, RandomUtils.randomHash()));

        assertThrows(
                ReconnectException.class,
                () -> validator.validate(laterState, addressBook),
                "A later state signed by less than a majority of stake should not pass validation");
    }
}
