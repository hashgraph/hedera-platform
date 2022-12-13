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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("SignedStateManager: Register States Without Signatures Test")
class RegisterStatesWithoutSignaturesTest extends AbstractSignedStateManagerTest {

    // Note: this unit test was long and complex, so it was split into its own class.
    // As such, this test was designed differently than it would be designed if it were sharing
    // the class file with other tests.
    // DO NOT ADD ADDITIONAL UNIT TESTS TO THIS CLASS!

    private final AddressBook addressBook =
            new RandomAddressBookGenerator(random).setSize(4).setSequentialIds(false).build();

    final long selfId = addressBook.getId(0);

    private final DispatchBuilder dispatchBuilder = new DispatchBuilder();
    private final SignedStateManager manager =
            buildSignedStateManager(dispatchBuilder, addressBook, selfId);

    /** This callback should be invoked after the state has been self signed. */
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

    /** This callback should be invoked after the state has been self signed. */
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
                    stateLacksSignaturesCount.getAndIncrement();

                    assertEquals(
                            highestRound.get() - roundsNonAncient,
                            notification.getSignedState().getRound(),
                            "unexpected round retired");
                    assertSame(
                            signedStates.get(highestRound.get() - roundsNonAncient),
                            notification.getSignedState(),
                            "unexpected state was retired");
                });
    }

    /** Called on each state as it gathers enough signatures to be complete. */
    private void stateHasEnoughSignaturesListener() {
        registerListener(
                StateHasEnoughSignaturesListener.class,
                notification -> {
                    // Not expected in this test, will fail if called even once
                    stateHasEnoughSignaturesCount.getAndIncrement();
                });
    }

    /**
     * Keep adding new states to the manager but never sign any of them (other than self
     * signatures).
     */
    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Register States Without Signatures")
    void registerStatesWithoutSignatures() throws InterruptedException {
        newSignedStateBeingTrackedListener();
        stateSelfSignedListener();
        stateLacksSignaturesListener();
        stateHasEnoughSignaturesListener();

        dispatchBuilder.registerObservers(manager);
        dispatchBuilder.start();

        // Create a series of signed states. Don't add any signatures. Self signatures will be
        // automatically added.
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

            try (final AutoCloseableWrapper<SignedState> lastState = manager.getLastSignedState()) {
                assertSame(signedState, lastState.get(), "last signed state has unexpected value");
            }
            try (final AutoCloseableWrapper<SignedState> lastCompletedState =
                    manager.getLastCompleteSignedState(false)) {
                assertNull(lastCompletedState.get(), "no states should be completed in this test");
            }

            final int expectedUnsignedStates = Math.max(0, round - roundsNonAncient + 1);

            validateCallbackCounts(round + 1, round + 1, expectedUnsignedStates, 0);
        }

        validateReservationCounts(round -> round < signedStates.size() - roundsNonAncient);

        // We don't expect any further callbacks. But wait a little while longer in case there is
        // something unexpected.
        SECONDS.sleep(1);

        validateCallbackCounts(count, count, count - roundsNonAncient, 0);

        manager.stop();
    }
}
