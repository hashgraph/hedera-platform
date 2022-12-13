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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SignedStateMap Tests")
class SignedStateMapTests {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("get() Test")
    void getTest(final boolean strongMap) {

        final SignedStateMap map = new SignedStateMap(strongMap);
        assertEquals(0, map.getSize(), "unexpected size");

        final AtomicInteger strongReferences = new AtomicInteger();
        final AtomicInteger weakReferences = new AtomicInteger();

        final AtomicInteger referencesHeldByMap = strongMap ? strongReferences : weakReferences;
        final AtomicInteger referencesNotHeldByMap = strongMap ? weakReferences : strongReferences;

        final SignedState signedState =
                SignedStateReferenceTests.buildSignedState(strongReferences, weakReferences);
        final long round = 1234;
        doReturn(round).when(signedState).getRound();

        map.put(signedState);
        assertEquals(1, map.getSize(), "unexpected size");

        assertEquals(1, referencesHeldByMap.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap.get(), "invalid reference count");

        // Subtract away the reference held by map, makes logic below simpler
        referencesHeldByMap.getAndDecrement();

        AutoCloseableWrapper<SignedState> wrapper;

        // Get a strong reference to a round that is not in the map
        if (strongMap) {
            wrapper = map.get(0);
            assertNull(wrapper.get());
            wrapper.close();

            wrapper = map.get(0, true);
            assertNull(wrapper.get());
            wrapper.close();
        } else {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> map.get(0),
                    "can't get strong reference from weak map");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> map.get(0, true),
                    "can't get strong reference from weak map");
        }

        // Get a weak reference to a round that is not in the map
        wrapper = map.get(0, false);
        assertNull(wrapper.get());
        wrapper.close();

        // Get a strong reference to a state in the map
        if (strongMap) {
            wrapper = map.get(round);
            assertSame(signedState, wrapper.get(), "wrapper returned incorrect object");
            assertEquals(1, strongReferences.get(), "invalid reference count");
            assertEquals(0, weakReferences.get(), "invalid reference count");
            wrapper.close();
            assertEquals(0, strongReferences.get(), "invalid reference count");
            assertEquals(0, weakReferences.get(), "invalid reference count");

            wrapper = map.get(round, true);
            assertSame(signedState, wrapper.get(), "wrapper returned incorrect object");
            assertEquals(1, strongReferences.get(), "invalid reference count");
            assertEquals(0, weakReferences.get(), "invalid reference count");
            wrapper.close();
            assertEquals(0, strongReferences.get(), "invalid reference count");
            assertEquals(0, weakReferences.get(), "invalid reference count");
        } else {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> map.get(round),
                    "can't get strong reference from weak map");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> map.get(round, true),
                    "can't get strong reference from weak map");
        }

        // Get a weak reference to a state in the map
        wrapper = map.get(round, false);
        assertSame(signedState, wrapper.get(), "wrapper returned incorrect object");
        assertEquals(1, weakReferences.get(), "invalid reference count");
        assertEquals(0, strongReferences.get(), "invalid reference count");
        wrapper.close();
        assertEquals(0, weakReferences.get(), "invalid reference count");
        assertEquals(0, strongReferences.get(), "invalid reference count");

        assertEquals(1, map.getSize(), "unexpected size");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("remove() Test")
    void removeTest(final boolean strongMap) {
        final SignedStateMap map = new SignedStateMap(strongMap);
        assertEquals(0, map.getSize(), "unexpected size");

        final AtomicInteger strongReferences = new AtomicInteger();
        final AtomicInteger weakReferences = new AtomicInteger();

        final AtomicInteger referencesHeldByMap = strongMap ? strongReferences : weakReferences;
        final AtomicInteger referencesNotHeldByMap = strongMap ? weakReferences : strongReferences;

        final SignedState signedState =
                SignedStateReferenceTests.buildSignedState(strongReferences, weakReferences);
        final long round = 1234;
        doReturn(round).when(signedState).getRound();

        map.put(signedState);
        assertEquals(1, map.getSize(), "unexpected size");

        assertEquals(1, referencesHeldByMap.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap.get(), "invalid reference count");

        // remove an element in the map
        map.remove(round);
        assertEquals(0, map.getSize(), "unexpected size");
        assertEquals(0, referencesHeldByMap.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap.get(), "invalid reference count");

        // remove an element not in the map, should not throw
        map.remove(0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("replace() Test")
    void replaceTest(final boolean strongMap) {
        final SignedStateMap map = new SignedStateMap(strongMap);
        assertEquals(0, map.getSize(), "unexpected size");

        final AtomicInteger strongReferences1 = new AtomicInteger();
        final AtomicInteger weakReferences1 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap1 = strongMap ? strongReferences1 : weakReferences1;
        final AtomicInteger referencesNotHeldByMap1 =
                strongMap ? weakReferences1 : strongReferences1;

        final SignedState signedState1 =
                SignedStateReferenceTests.buildSignedState(strongReferences1, weakReferences1);
        final long round = 1234;
        doReturn(round).when(signedState1).getRound();

        final AtomicInteger strongReferences2 = new AtomicInteger();
        final AtomicInteger weakReferences2 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap2 = strongMap ? strongReferences2 : weakReferences2;
        final AtomicInteger referencesNotHeldByMap2 =
                strongMap ? weakReferences2 : strongReferences2;

        final SignedState signedState2 =
                SignedStateReferenceTests.buildSignedState(strongReferences2, weakReferences2);
        doReturn(round).when(signedState2).getRound();

        map.put(signedState1);
        assertEquals(1, map.getSize(), "unexpected size");
        assertEquals(1, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap2.get(), "invalid reference count");

        map.put(signedState2);
        assertEquals(1, map.getSize(), "unexpected size");
        assertEquals(0, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap1.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap2.get(), "invalid reference count");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("No Null Values Test")
    void noNullValuesTest(final boolean strongMap) {
        final SignedStateMap map = new SignedStateMap(strongMap);
        assertEquals(0, map.getSize(), "unexpected size");

        assertThrows(
                IllegalArgumentException.class,
                () -> map.put(null),
                "map should reject a null signed state");
        assertEquals(0, map.getSize(), "unexpected size");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("clear() Test")
    void clearTest(final boolean strongMap) {
        final SignedStateMap map = new SignedStateMap(strongMap);
        assertEquals(0, map.getSize(), "unexpected size");

        final AtomicInteger strongReferences1 = new AtomicInteger();
        final AtomicInteger weakReferences1 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap1 = strongMap ? strongReferences1 : weakReferences1;
        final AtomicInteger referencesNotHeldByMap1 =
                strongMap ? weakReferences1 : strongReferences1;

        final SignedState signedState1 =
                SignedStateReferenceTests.buildSignedState(strongReferences1, weakReferences1);
        final long round1 = 1234;
        doReturn(round1).when(signedState1).getRound();

        final AtomicInteger strongReferences2 = new AtomicInteger();
        final AtomicInteger weakReferences2 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap2 = strongMap ? strongReferences2 : weakReferences2;
        final AtomicInteger referencesNotHeldByMap2 =
                strongMap ? weakReferences2 : strongReferences2;

        final SignedState signedState2 =
                SignedStateReferenceTests.buildSignedState(strongReferences2, weakReferences2);
        final long round2 = 1235;
        doReturn(round2).when(signedState2).getRound();

        final AtomicInteger strongReferences3 = new AtomicInteger();
        final AtomicInteger weakReferences3 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap3 = strongMap ? strongReferences3 : weakReferences3;
        final AtomicInteger referencesNotHeldByMap3 =
                strongMap ? weakReferences3 : strongReferences3;

        final SignedState signedState3 =
                SignedStateReferenceTests.buildSignedState(strongReferences3, weakReferences3);
        final long round3 = 1236;
        doReturn(round3).when(signedState2).getRound();

        map.put(signedState1);
        map.put(signedState2);
        map.put(signedState3);
        assertEquals(3, map.getSize(), "unexpected size");
        assertEquals(1, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap1.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap2.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap3.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap3.get(), "invalid reference count");

        map.clear();
        assertEquals(0, map.getSize(), "unexpected size");
        assertEquals(0, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap2.get(), "invalid reference count");
        assertEquals(0, referencesHeldByMap3.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap3.get(), "invalid reference count");

        assertNull(map.get(round1, false).get(), "state should not be in map");
        assertNull(map.get(round2, false).get(), "state should not be in map");
        assertNull(map.get(round3, false).get(), "state should not be in map");
        assertEquals(0, map.getSize(), "unexpected size");
        assertEquals(0, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap2.get(), "invalid reference count");
        assertEquals(0, referencesHeldByMap3.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap3.get(), "invalid reference count");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Iteration Test")
    void iterationTest(final boolean strongMap) {
        final SignedStateMap map = new SignedStateMap(strongMap);
        assertEquals(0, map.getSize(), "unexpected size");

        final AtomicInteger strongReferences1 = new AtomicInteger();
        final AtomicInteger weakReferences1 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap1 = strongMap ? strongReferences1 : weakReferences1;
        final AtomicInteger referencesNotHeldByMap1 =
                strongMap ? weakReferences1 : strongReferences1;

        final SignedState signedState1 =
                SignedStateReferenceTests.buildSignedState(strongReferences1, weakReferences1);
        final long round1 = 1234;
        doReturn(round1).when(signedState1).getRound();

        final AtomicInteger strongReferences2 = new AtomicInteger();
        final AtomicInteger weakReferences2 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap2 = strongMap ? strongReferences2 : weakReferences2;
        final AtomicInteger referencesNotHeldByMap2 =
                strongMap ? weakReferences2 : strongReferences2;

        final SignedState signedState2 =
                SignedStateReferenceTests.buildSignedState(strongReferences2, weakReferences2);
        final long round2 = 1235;
        doReturn(round2).when(signedState2).getRound();

        final AtomicInteger strongReferences3 = new AtomicInteger();
        final AtomicInteger weakReferences3 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap3 = strongMap ? strongReferences3 : weakReferences3;
        final AtomicInteger referencesNotHeldByMap3 =
                strongMap ? weakReferences3 : strongReferences3;

        final SignedState signedState3 =
                SignedStateReferenceTests.buildSignedState(strongReferences3, weakReferences3);
        final long round3 = 1236;
        doReturn(round3).when(signedState2).getRound();

        map.put(signedState1);
        map.put(signedState2);
        map.put(signedState3);
        assertEquals(3, map.getSize(), "unexpected size");
        assertEquals(1, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap1.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap2.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap3.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap3.get(), "invalid reference count");

        final AtomicBoolean state1Found = new AtomicBoolean();
        final AtomicBoolean state2Found = new AtomicBoolean();
        final AtomicBoolean state3Found = new AtomicBoolean();
        map.atomicIteration(
                iterator ->
                        iterator.forEachRemaining(
                                state -> {
                                    if (state == signedState1) {
                                        assertFalse(
                                                state1Found.get(),
                                                "should only encounter state once");
                                        state1Found.set(true);
                                    }
                                    if (state == signedState2) {
                                        assertFalse(
                                                state2Found.get(),
                                                "should only encounter state once");
                                        state2Found.set(true);
                                    }
                                    if (state == signedState3) {
                                        assertFalse(
                                                state3Found.get(),
                                                "should only encounter state once");
                                        state3Found.set(true);
                                    }
                                }));
        assertTrue(state1Found.get(), "state not found");
        assertTrue(state2Found.get(), "state not found");
        assertTrue(state3Found.get(), "state not found");
        assertEquals(3, map.getSize(), "unexpected size");
        assertEquals(1, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap1.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap2.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap3.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap3.get(), "invalid reference count");

        map.atomicIteration(
                iterator ->
                        iterator.forEachRemaining(
                                state -> {
                                    if (state == signedState2) {
                                        iterator.remove();
                                    }
                                }));
        assertEquals(2, map.getSize(), "unexpected size");
        assertEquals(1, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap2.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap3.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap3.get(), "invalid reference count");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("find() Test")
    void findTest(final boolean strongMap) {
        final SignedStateMap map = new SignedStateMap(strongMap);
        assertEquals(0, map.getSize(), "unexpected size");

        final AtomicInteger strongReferences1 = new AtomicInteger();
        final AtomicInteger weakReferences1 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap1 = strongMap ? strongReferences1 : weakReferences1;
        final AtomicInteger referencesNotHeldByMap1 =
                strongMap ? weakReferences1 : strongReferences1;

        final SignedState signedState1 =
                SignedStateReferenceTests.buildSignedState(strongReferences1, weakReferences1);
        final long round1 = 1234;
        doReturn(round1).when(signedState1).getRound();

        final AtomicInteger strongReferences2 = new AtomicInteger();
        final AtomicInteger weakReferences2 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap2 = strongMap ? strongReferences2 : weakReferences2;
        final AtomicInteger referencesNotHeldByMap2 =
                strongMap ? weakReferences2 : strongReferences2;

        final SignedState signedState2 =
                SignedStateReferenceTests.buildSignedState(strongReferences2, weakReferences2);
        final long round2 = 1235;
        doReturn(round2).when(signedState2).getRound();

        final AtomicInteger strongReferences3 = new AtomicInteger();
        final AtomicInteger weakReferences3 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap3 = strongMap ? strongReferences3 : weakReferences3;
        final AtomicInteger referencesNotHeldByMap3 =
                strongMap ? weakReferences3 : strongReferences3;

        final SignedState signedState3 =
                SignedStateReferenceTests.buildSignedState(strongReferences3, weakReferences3);
        final long round3 = 1236;
        doReturn(round3).when(signedState2).getRound();

        map.put(signedState1);
        map.put(signedState2);
        map.put(signedState3);
        assertEquals(3, map.getSize(), "unexpected size");
        assertEquals(1, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap1.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap2.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap3.get(), "invalid reference count");
        assertEquals(0, referencesNotHeldByMap3.get(), "invalid reference count");

        try (final AutoCloseableWrapper<SignedState> strongFoundState1 =
                map.find(ss -> ss == signedState1, true)) {
            assertEquals(signedState1, strongFoundState1.get(), "incorrect state found");
            assertEquals(strongMap ? 2 : 1, strongReferences1.get(), "invalid reference count");
            assertEquals(strongMap ? 0 : 1, weakReferences1.get(), "invalid reference count");
        }
        try (final AutoCloseableWrapper<SignedState> weakFoundState1 =
                map.find(ss -> ss == signedState1, false)) {
            assertEquals(signedState1, weakFoundState1.get(), "incorrect state found");
            assertEquals(strongMap ? 1 : 2, weakReferences1.get(), "invalid reference count");
            assertEquals(strongMap ? 1 : 0, strongReferences1.get(), "invalid reference count");
        }

        try (final AutoCloseableWrapper<SignedState> strongFoundState2 =
                map.find(ss -> ss == signedState2, true)) {
            assertEquals(signedState2, strongFoundState2.get(), "incorrect state found");
            assertEquals(strongMap ? 2 : 1, strongReferences2.get(), "invalid reference count");
            assertEquals(strongMap ? 0 : 1, weakReferences2.get(), "invalid reference count");
        }
        try (final AutoCloseableWrapper<SignedState> weakFoundState2 =
                map.find(ss -> ss == signedState2, false)) {
            assertEquals(signedState2, weakFoundState2.get(), "incorrect state found");
            assertEquals(strongMap ? 1 : 2, weakReferences2.get(), "invalid reference count");
            assertEquals(strongMap ? 1 : 0, strongReferences2.get(), "invalid reference count");
        }

        try (final AutoCloseableWrapper<SignedState> strongFoundState3 =
                map.find(ss -> ss == signedState3, true)) {
            assertEquals(signedState3, strongFoundState3.get(), "incorrect state found");
            assertEquals(strongMap ? 2 : 1, strongReferences3.get(), "invalid reference count");
            assertEquals(strongMap ? 0 : 1, weakReferences3.get(), "invalid reference count");
        }
        try (final AutoCloseableWrapper<SignedState> weakFoundState3 =
                map.find(ss -> ss == signedState3, false)) {
            assertEquals(signedState3, weakFoundState3.get(), "incorrect state found");
            assertEquals(strongMap ? 1 : 2, weakReferences3.get(), "invalid reference count");
            assertEquals(strongMap ? 1 : 0, strongReferences3.get(), "invalid reference count");
        }
    }
}
