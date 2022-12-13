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
package com.swirlds.platform.state;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.RandomUtils.randomHash;
import static com.swirlds.common.test.RandomUtils.randomSignature;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.test.state.DummySwirldState2;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.SigInfo;
import com.swirlds.platform.state.signed.SignedState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/** A utility for generating random signed states. */
public class RandomSignedStateGenerator {

    final Random random;

    private State state;
    private Long round;
    private Long numEventsCons;
    private Hash hashEventsCons;
    private AddressBook addressBook;
    private EventImpl[] events;
    private Instant consensusTimestamp;
    private Boolean freezeState = false;
    private List<MinGenInfo> minGenInfo;
    private SoftwareVersion softwareVersion;
    private List<Long> signingNodeIds;
    private List<SigInfo> signatures;
    private boolean protectionEnabled = false;
    private boolean randomSwirldStateHash = true;

    /** Create a new signed state generator with a random seed. */
    public RandomSignedStateGenerator() {
        random = getRandomPrintSeed();
    }

    /** Create a new signed state generator with a specific seed. */
    public RandomSignedStateGenerator(final long seed) {
        random = new Random(seed);
    }

    /** Create a new signed state generator with a random object. */
    public RandomSignedStateGenerator(final Random random) {
        this.random = random;
    }

    /**
     * Create a fake signature
     *
     * @param round the round
     * @param memberId the node ID
     */
    public static SigInfo createSigInfo(
            final Random random, final long round, final long memberId) {
        return new SigInfo(round, memberId, randomHash(random), randomSignature(random));
    }

    /**
     * Build a new signed state.
     *
     * @return a new signed state
     */
    public SignedState build() {
        final AddressBook addressBookInstance;
        if (addressBook == null) {
            addressBookInstance =
                    new RandomAddressBookGenerator(random)
                            .setStakeDistributionStrategy(
                                    RandomAddressBookGenerator.StakeDistributionStrategy.BALANCED)
                            .setHashStrategy(RandomAddressBookGenerator.HashStrategy.REAL_HASH)
                            .setSequentialIds(true)
                            .build();
        } else {
            addressBookInstance = addressBook;
        }

        final State stateInstance;
        if (state == null) {
            stateInstance = new State();
            final DummySwirldState2 swirldState = new DummySwirldState2(addressBookInstance);
            if (randomSwirldStateHash) {
                swirldState.setHashOverride(randomHash(random));
            }
            stateInstance.setSwirldState(swirldState);
        } else {
            stateInstance = state;
        }

        final long roundInstance;
        if (round == null) {
            roundInstance = Math.abs(random.nextLong());
        } else {
            roundInstance = round;
        }

        final long numEventsConsInstance;
        if (numEventsCons == null) {
            numEventsConsInstance = Math.abs(random.nextLong());
        } else {
            numEventsConsInstance = numEventsCons;
        }

        final Hash hashEventsConsInstance;
        if (hashEventsCons == null) {
            hashEventsConsInstance = randomHash(random);
        } else {
            hashEventsConsInstance = hashEventsCons;
        }

        final EventImpl[] eventsInstance;
        if (events == null) {
            eventsInstance = new EventImpl[] {};
        } else {
            eventsInstance = events;
        }

        final Instant consensusTimestampInstance;
        if (consensusTimestamp == null) {
            consensusTimestampInstance = RandomUtils.randomInstant(random);
        } else {
            consensusTimestampInstance = consensusTimestamp;
        }

        final boolean freezeStateInstance;
        if (freezeState == null) {
            freezeStateInstance = random.nextBoolean();
        } else {
            freezeStateInstance = freezeState;
        }

        final List<MinGenInfo> minGenInfoInstance;
        if (minGenInfo == null) {
            minGenInfoInstance = List.of();
        } else {
            minGenInfoInstance = minGenInfo;
        }

        final SoftwareVersion softwareVersionInstance;
        if (softwareVersion == null) {
            softwareVersionInstance = new BasicSoftwareVersion(Math.abs(random.nextLong()));
        } else {
            softwareVersionInstance = softwareVersion;
        }

        final SignedState signedState =
                new SignedState(
                        stateInstance,
                        roundInstance,
                        numEventsConsInstance,
                        hashEventsConsInstance,
                        addressBookInstance,
                        eventsInstance,
                        consensusTimestampInstance,
                        freezeStateInstance,
                        minGenInfoInstance,
                        softwareVersionInstance);

        MerkleCryptoFactory.getInstance().digestTreeSync(signedState.getState());

        final List<SigInfo> signaturesInstance;
        if (signatures == null) {
            final List<Long> signingNodeIdsInstance;
            if (signingNodeIds == null) {
                signingNodeIdsInstance = new LinkedList<>();
                if (addressBookInstance.getSize() > 0) {
                    for (int i = 0; i < addressBookInstance.getSize() / 3 + 1; i++) {
                        signingNodeIdsInstance.add(addressBookInstance.getId(i));
                    }
                }
            } else {
                signingNodeIdsInstance = signingNodeIds;
            }

            signaturesInstance = new ArrayList<>(signingNodeIdsInstance.size());

            for (final long nodeID : signingNodeIdsInstance) {
                signaturesInstance.add(createSigInfo(random, roundInstance, nodeID));
            }
        } else {
            signaturesInstance = signatures;
        }

        for (final SigInfo signature : signaturesInstance) {
            signedState.getSigSet().addSigInfo(signature);
        }

        if (protectionEnabled
                && stateInstance.getSwirldState()
                        instanceof final DummySwirldState2 dummySwirldState2) {
            dummySwirldState2.disableArchiving();
            dummySwirldState2.disableDeletion();
        }

        return signedState;
    }

    /**
     * Build multiple states.
     *
     * @param count the number of states to build
     */
    public List<SignedState> build(final int count) {
        final List<SignedState> states = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            states.add(build());
        }

        return states;
    }

    /**
     * Set the state.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setState(final State state) {
        this.state = state;
        return this;
    }

    /**
     * Set the round when the state was generated.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setRound(final long round) {
        this.round = round;
        return this;
    }

    /**
     * Set the number of events that have been applied to this state since genesis.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setNumEventsCons(final long numEventsCons) {
        this.numEventsCons = numEventsCons;
        return this;
    }

    /**
     * Set the running hash of all events that have been applied to this state since genesis.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setHashEventsCons(final Hash hashEventsCons) {
        this.hashEventsCons = hashEventsCons;
        return this;
    }

    /**
     * Set the address book.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setAddressBook(final AddressBook addressBook) {
        this.addressBook = addressBook;
        return this;
    }

    /**
     * Set the events contained within the state.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setEvents(final EventImpl[] events) {
        this.events = events;
        return this;
    }

    /**
     * Set the timestamp associated with this state.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setConsensusTimestamp(final Instant consensusTimestamp) {
        this.consensusTimestamp = consensusTimestamp;
        return this;
    }

    /**
     * Specify if this state was written to disk as a result of a freeze.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setFreezeState(final boolean freezeState) {
        this.freezeState = freezeState;
        return this;
    }

    /**
     * Set minimum generation info for the state.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setMinGenInfo(final List<MinGenInfo> minGenInfo) {
        this.minGenInfo = minGenInfo;
        return this;
    }

    /**
     * Set the software version that was used to create this state.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setSoftwareVersion(final SoftwareVersion softwareVersion) {
        this.softwareVersion = softwareVersion;
        return this;
    }

    /**
     * Specify which nodes have signed this signed state. Ignored if signatures are set.
     *
     * @param signingNodeIds a list of nodes that have signed this state
     * @return this object
     */
    public RandomSignedStateGenerator setSigningNodeIds(final List<Long> signingNodeIds) {
        this.signingNodeIds = signingNodeIds;
        return this;
    }

    /**
     * Provide signatures for the signed state.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setSignatures(final List<SigInfo> signatures) {
        this.signatures = signatures;
        return this;
    }

    /**
     * Default false. If true and a {@link DummySwirldState2} is being used, then disable archiving
     * and deletion on the state.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setProtectionEnabled(final boolean protectionEnabled) {
        this.protectionEnabled = protectionEnabled;
        return this;
    }

    /**
     * Default true. If true and if a SwirldState is being randomly generated by this utility, then
     * that swirld state will pretend to have a random hash.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setRandomSwirldStateHash(
            final boolean randomSwirldStateHash) {
        this.randomSwirldStateHash = randomSwirldStateHash;
        return this;
    }
}
