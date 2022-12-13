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
package com.swirlds.platform.state.manager;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.triggers.flow.StateHashedTrigger;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.notifications.NewSignedStateBeingTrackedListener;
import com.swirlds.platform.state.notifications.StateHasEnoughSignaturesListener;
import com.swirlds.platform.state.notifications.StateLacksSignaturesListener;
import com.swirlds.platform.state.notifications.StateSelfSignedListener;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import com.swirlds.test.framework.TestQualifierTags;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("SignedStateManager: Sequential Signatures Test")
public class SequentialSignaturesTest extends AbstractSignedStateManagerTest {

    // Note: this unit test was long and complex, so it was split into its own class.
    // As such, this test was designed differently than it would be designed if it were sharing
    // the class file with other tests.
    // DO NOT ADD ADDITIONAL UNIT TESTS TO THIS CLASS!

    private final int roundAgeToSign = 3;

    private final AddressBook addressBook =
            new RandomAddressBookGenerator(random)
                    .setSize(4)
                    .setStakeDistributionStrategy(
                            RandomAddressBookGenerator.StakeDistributionStrategy.BALANCED)
                    .setSequentialIds(true)
                    .build();

    private final long selfId = addressBook.getId(0);

    private final DispatchBuilder dispatchBuilder = new DispatchBuilder();
    private final SignedStateManager manager =
            buildSignedStateManager(dispatchBuilder, addressBook, selfId);

    /** This callback should be invoked every time a signed state is added to the manager. */
    private void newSignedStateBeingTrackedListener() {
        registerListener(
                NewSignedStateBeingTrackedListener.class,
                notification -> {
                    stateTrackedCount.getAndIncrement();
                    assertEquals(
                            SourceOfSignedState.TRANSACTIONS,
                            notification.getSourceOfSignedState(),
                            "unexpected state source");
                    assertEquals(selfId, notification.getSelfId().getId(), "unexpected node ID");
                    assertSame(
                            signedStates.get(highestRound.get()),
                            notification.getSignedState(),
                            "unexpected state");
                });
    }

    /** This callback should be invoked after the state has been self signed */
    private void stateSelfSignedListener() {
        registerListener(
                StateSelfSignedListener.class,
                notification -> {
                    selfSignedCount.getAndIncrement();
                    assertEquals(selfId, notification.getSelfId().getId(), "unexpected node ID");
                });
    }

    /** Called on each state as it gets too old without collecting enough signatures. */
    private void stateLacksSignaturesListener() {
        registerListener(
                StateLacksSignaturesListener.class,
                notification -> {
                    // No state is unsigned in this test. If this method is called then the test is
                    // expected to fail.
                    stateLacksSignaturesCount.getAndIncrement();
                });
    }

    /** Called on each state as it gathers enough signatures to be complete. */
    private void stateHasEnoughSignaturesListener() {
        registerListener(
                StateHasEnoughSignaturesListener.class,
                notification -> {
                    assertEquals(
                            highestRound.get() - roundAgeToSign,
                            notification.getSignedState().getRound(),
                            "unexpected round completed");

                    stateHasEnoughSignaturesCount.getAndIncrement();
                });
    }

    @Test
    @DisplayName("Sequential Signatures Test")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void sequentialSignaturesTest() throws InterruptedException {

        dispatchBuilder.registerObservers(manager);

        final AtomicInteger hashCount = new AtomicInteger();
        dispatchBuilder.registerObserver(
                StateHashedTrigger.class, (round, hash) -> hashCount.getAndIncrement());

        dispatchBuilder.start();

        newSignedStateBeingTrackedListener();
        stateSelfSignedListener();
        stateLacksSignaturesListener();
        stateHasEnoughSignaturesListener();

        // Create a series of signed states.
        final int count = 100;
        for (int round = 0; round < count; round++) {
            final SignedState signedState =
                    new RandomSignedStateGenerator(random)
                            .setAddressBook(addressBook)
                            .setRound(round)
                            .setSignatures(new LinkedList<>())
                            .build();

            signedStates.put((long) round, signedState);
            highestRound.set(round);

            manager.addUnsignedState(signedState);
            assertEquals(round + 1, hashCount.get(), "hash dispatch not sent");

            // Add some signatures to one of the previous states
            final long roundToSign = round - roundAgeToSign;
            addSignature(manager, roundToSign, 1);
            addSignature(manager, roundToSign, 2);
            if (random.nextBoolean()) {
                addSignature(manager, roundToSign, 1);
                addSignature(manager, roundToSign, 1);
            }

            try (final AutoCloseableWrapper<SignedState> lastState = manager.getLastSignedState()) {
                assertSame(signedState, lastState.get(), "last signed state has unexpected value");
            }
            try (final AutoCloseableWrapper<SignedState> lastCompletedState =
                    manager.getLastCompleteSignedState(false)) {
                if (roundToSign >= 0) {
                    assertSame(
                            signedStates.get(roundToSign),
                            lastCompletedState.get(),
                            "unexpected last completed state");
                } else {
                    assertNull(lastCompletedState.get(), "no states should be completed yet");
                }
            }

            validateCallbackCounts(
                    round + 1, round + 1, 0, Math.max(0, round - roundAgeToSign + 1));
        }

        // Check reservation counts.
        validateReservationCounts(round -> round < signedStates.size() - roundAgeToSign - 1);

        // We don't expect any further callbacks. But wait a little while longer in case there is
        // something unexpected.
        SECONDS.sleep(1);

        validateCallbackCounts(count, count, 0, count - roundAgeToSign);

        manager.stop();
    }
}
