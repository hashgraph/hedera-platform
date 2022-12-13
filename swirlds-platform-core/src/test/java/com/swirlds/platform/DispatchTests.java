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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.Observer;
import com.swirlds.platform.dispatch.types.TriggerEight;
import com.swirlds.platform.dispatch.types.TriggerFive;
import com.swirlds.platform.dispatch.types.TriggerFour;
import com.swirlds.platform.dispatch.types.TriggerNine;
import com.swirlds.platform.dispatch.types.TriggerOne;
import com.swirlds.platform.dispatch.types.TriggerSeven;
import com.swirlds.platform.dispatch.types.TriggerSix;
import com.swirlds.platform.dispatch.types.TriggerTen;
import com.swirlds.platform.dispatch.types.TriggerThree;
import com.swirlds.platform.dispatch.types.TriggerTwo;
import com.swirlds.platform.dispatch.types.TriggerZero;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Dispatch Test")
class DispatchTests {

    @FunctionalInterface
    public interface TestDispatchZero extends TriggerZero {}

    @FunctionalInterface
    public interface TestDispatchOne extends TriggerOne<Integer> {}

    @FunctionalInterface
    public interface TestDispatchTwo extends TriggerTwo<Integer, Integer> {}

    @FunctionalInterface
    public interface TestDispatchThree extends TriggerThree<Integer, Integer, Integer> {}

    @FunctionalInterface
    public interface TestDispatchFour extends TriggerFour<Integer, Integer, Integer, Integer> {}

    @FunctionalInterface
    public interface TestDispatchFive
            extends TriggerFive<Integer, Integer, Integer, Integer, Integer> {}

    @FunctionalInterface
    public interface TestDispatchSix
            extends TriggerSix<Integer, Integer, Integer, Integer, Integer, Integer> {}

    @FunctionalInterface
    public interface TestDispatchSeven
            extends TriggerSeven<Integer, Integer, Integer, Integer, Integer, Integer, Integer> {}

    @FunctionalInterface
    public interface TestDispatchEight
            extends TriggerEight<
                    Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer> {}

    @FunctionalInterface
    public interface TestDispatchNine
            extends TriggerNine<
                    Integer,
                    Integer,
                    Integer,
                    Integer,
                    Integer,
                    Integer,
                    Integer,
                    Integer,
                    Integer> {}

    @FunctionalInterface
    public interface TestDispatchTen
            extends TriggerTen<
                    Integer,
                    Integer,
                    Integer,
                    Integer,
                    Integer,
                    Integer,
                    Integer,
                    Integer,
                    Integer,
                    Integer> {}

    public static class ObserverClass {

        private final AtomicInteger count = new AtomicInteger(0);

        public int getCount() {
            return count.get();
        }

        @DisplayName("bogus annotation that should be ignored")
        @Observer(dispatchType = TestDispatchZero.class)
        public void observeZero() {
            count.getAndIncrement();
        }

        @Tag("bogus annotation")
        @Tag("should be ignored")
        @Observer(dispatchType = TestDispatchOne.class)
        public void observeOne(final Integer a) {
            count.getAndAdd(a);
        }

        @Observer(dispatchType = TestDispatchTwo.class)
        public void observeTwo(final Integer a, final Integer b) {
            count.getAndAdd(a);
            count.getAndAdd(b);
        }

        @Observer(dispatchType = TestDispatchThree.class)
        public void observeThree(final Integer a, final Integer b, final Integer c) {
            count.getAndAdd(a);
            count.getAndAdd(b);
            count.getAndAdd(c);
        }

        @Observer(dispatchType = TestDispatchFour.class)
        public void observeFour(
                final Integer a, final Integer b, final Integer c, final Integer d) {
            count.getAndAdd(a);
            count.getAndAdd(b);
            count.getAndAdd(c);
            count.getAndAdd(d);
        }

        @Observer(dispatchType = TestDispatchFive.class)
        public void observeFive(
                final Integer a,
                final Integer b,
                final Integer c,
                final Integer d,
                final Integer e) {
            count.getAndAdd(a);
            count.getAndAdd(b);
            count.getAndAdd(c);
            count.getAndAdd(d);
            count.getAndAdd(e);
        }

        @Observer(dispatchType = TestDispatchSix.class)
        public void observeSix(
                final Integer a,
                final Integer b,
                final Integer c,
                final Integer d,
                final Integer e,
                final Integer f) {
            count.getAndAdd(a);
            count.getAndAdd(b);
            count.getAndAdd(c);
            count.getAndAdd(d);
            count.getAndAdd(e);
            count.getAndAdd(f);
        }

        @Observer(dispatchType = TestDispatchSeven.class)
        public void observeSeven(
                final Integer a,
                final Integer b,
                final Integer c,
                final Integer d,
                final Integer e,
                final Integer f,
                final Integer g) {
            count.getAndAdd(a);
            count.getAndAdd(b);
            count.getAndAdd(c);
            count.getAndAdd(d);
            count.getAndAdd(e);
            count.getAndAdd(f);
            count.getAndAdd(g);
        }

        @Observer(dispatchType = TestDispatchEight.class)
        public void observeEight(
                final Integer a,
                final Integer b,
                final Integer c,
                final Integer d,
                final Integer e,
                final Integer f,
                final Integer g,
                final Integer h) {
            count.getAndAdd(a);
            count.getAndAdd(b);
            count.getAndAdd(c);
            count.getAndAdd(d);
            count.getAndAdd(e);
            count.getAndAdd(f);
            count.getAndAdd(g);
            count.getAndAdd(h);
        }

        @Observer(dispatchType = TestDispatchNine.class)
        public void observeNine(
                final Integer a,
                final Integer b,
                final Integer c,
                final Integer d,
                final Integer e,
                final Integer f,
                final Integer g,
                final Integer h,
                final Integer i) {
            count.getAndAdd(a);
            count.getAndAdd(b);
            count.getAndAdd(c);
            count.getAndAdd(d);
            count.getAndAdd(e);
            count.getAndAdd(f);
            count.getAndAdd(g);
            count.getAndAdd(h);
            count.getAndAdd(i);
        }

        @Observer(dispatchType = TestDispatchTen.class)
        public void observeTen(
                final Integer a,
                final Integer b,
                final Integer c,
                final Integer d,
                final Integer e,
                final Integer f,
                final Integer g,
                final Integer h,
                final Integer i,
                final Integer j) {
            count.getAndAdd(a);
            count.getAndAdd(b);
            count.getAndAdd(c);
            count.getAndAdd(d);
            count.getAndAdd(e);
            count.getAndAdd(f);
            count.getAndAdd(g);
            count.getAndAdd(h);
            count.getAndAdd(i);
            count.getAndAdd(j);
        }
    }

    @Test
    @DisplayName("Double Start test")
    void doubleStartTest() {
        final DispatchBuilder builder = new DispatchBuilder();
        builder.start();
        assertThrows(
                MutabilityException.class, builder::start, "should only be able to start once");
    }

    @Test
    @DisplayName("Null Argument Test")
    void nullArgumentTest() {
        final DispatchBuilder builder = new DispatchBuilder();

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.registerObserver(null, null),
                "null arguments not allowed");
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.registerObserver(TestDispatchOne.class, null),
                "null arguments not allowed");
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.registerObserver(null, (TestDispatchOne) x -> {}),
                "null arguments not allowed");
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.registerObservers(null),
                "null arguments not allowed");

        builder.start();

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.getDispatcher(null),
                "null arguments not allowed");
    }

    @Test
    @DisplayName("Early Dispatch Test")
    void earlyDispatchTest() {
        final DispatchBuilder builder = new DispatchBuilder();

        final TestDispatchZero d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
        final TestDispatchOne d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
        final TestDispatchTwo d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
        final TestDispatchThree d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
        final TestDispatchFour d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
        final TestDispatchFive d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
        final TestDispatchSix d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
        final TestDispatchSeven d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
        final TestDispatchEight d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
        final TestDispatchNine d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
        final TestDispatchTen d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;

        assertThrows(
                MutabilityException.class,
                d0::dispatch,
                "shouldn't be able to dispatch before builder is started");
        assertThrows(
                MutabilityException.class,
                () -> d1.dispatch(0),
                "shouldn't be able to dispatch before builder is started");
        assertThrows(
                MutabilityException.class,
                () -> d2.dispatch(0, 0),
                "shouldn't be able to dispatch before builder is started");
        assertThrows(
                MutabilityException.class,
                () -> d3.dispatch(0, 0, 0),
                "shouldn't be able to dispatch before builder is started");
        assertThrows(
                MutabilityException.class,
                () -> d4.dispatch(0, 0, 0, 0),
                "shouldn't be able to dispatch before builder is started");
        assertThrows(
                MutabilityException.class,
                () -> d5.dispatch(0, 0, 0, 0, 0),
                "shouldn't be able to dispatch before builder is started");
        assertThrows(
                MutabilityException.class,
                () -> d6.dispatch(0, 0, 0, 0, 0, 0),
                "shouldn't be able to dispatch before builder is started");
        assertThrows(
                MutabilityException.class,
                () -> d7.dispatch(0, 0, 0, 0, 0, 0, 0),
                "shouldn't be able to dispatch before builder is started");
        assertThrows(
                MutabilityException.class,
                () -> d8.dispatch(0, 0, 0, 0, 0, 0, 0, 0),
                "shouldn't be able to dispatch before builder is started");
        assertThrows(
                MutabilityException.class,
                () -> d9.dispatch(0, 0, 0, 0, 0, 0, 0, 0, 0),
                "shouldn't be able to dispatch before builder is started");
        assertThrows(
                MutabilityException.class,
                () -> d10.dispatch(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                "shouldn't be able to dispatch before builder is started");
    }

    @Test
    @DisplayName("Late Registration Test")
    void lateRegistrationTest() {
        final DispatchBuilder builder = new DispatchBuilder();
        builder.start();

        assertThrows(
                MutabilityException.class,
                () -> builder.registerObserver(TestDispatchZero.class, () -> {}),
                "should not be able to register new observers after start");
        assertThrows(
                MutabilityException.class,
                () -> builder.registerObserver(TestDispatchOne.class, (a) -> {}),
                "should not be able to register new observers after start");
        assertThrows(
                MutabilityException.class,
                () -> builder.registerObserver(TestDispatchTwo.class, (a, b) -> {}),
                "should not be able to register new observers after start");
        assertThrows(
                MutabilityException.class,
                () -> builder.registerObserver(TestDispatchThree.class, (a, b, c) -> {}),
                "should not be able to register new observers after start");
        assertThrows(
                MutabilityException.class,
                () -> builder.registerObserver(TestDispatchFour.class, (a, b, c, d) -> {}),
                "should not be able to register new observers after start");
        assertThrows(
                MutabilityException.class,
                () -> builder.registerObserver(TestDispatchFive.class, (a, b, c, d, e) -> {}),
                "should not be able to register new observers after start");
        assertThrows(
                MutabilityException.class,
                () -> builder.registerObserver(TestDispatchSix.class, (a, b, c, d, e, f) -> {}),
                "should not be able to register new observers after start");
        assertThrows(
                MutabilityException.class,
                () ->
                        builder.registerObserver(
                                TestDispatchSeven.class, (a, b, c, d, e, f, g) -> {}),
                "should not be able to register new observers after start");
        assertThrows(
                MutabilityException.class,
                () ->
                        builder.registerObserver(
                                TestDispatchEight.class, (a, b, c, d, e, f, g, h) -> {}),
                "should not be able to register new observers after start");
        assertThrows(
                MutabilityException.class,
                () ->
                        builder.registerObserver(
                                TestDispatchNine.class, (a, b, c, d, e, f, g, h, i) -> {}),
                "should not be able to register new observers after start");
        assertThrows(
                MutabilityException.class,
                () ->
                        builder.registerObserver(
                                TestDispatchTen.class, (a, b, c, d, e, f, g, h, i, j) -> {}),
                "should not be able to register new observers after start");
        assertThrows(
                MutabilityException.class,
                () -> builder.registerObservers(new ObserverClass()),
                "should not be able to register new observers after start");
    }

    @Test
    @DisplayName("No Observer Test")
    void noObserverTest() {
        final DispatchBuilder builder = new DispatchBuilder();
        builder.start();

        final TestDispatchZero d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
        final TestDispatchOne d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
        final TestDispatchTwo d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
        final TestDispatchThree d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
        final TestDispatchFour d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
        final TestDispatchFive d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
        final TestDispatchSix d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
        final TestDispatchSeven d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
        final TestDispatchEight d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
        final TestDispatchNine d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
        final TestDispatchTen d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;

        assertDoesNotThrow(d0::dispatch, "no observers should be supported");
        assertDoesNotThrow(() -> d1.dispatch(0), "no observers should be supported");
        assertDoesNotThrow(() -> d2.dispatch(0, 0), "no observers should be supported");
        assertDoesNotThrow(() -> d3.dispatch(0, 0, 0), "no observers should be supported");
        assertDoesNotThrow(() -> d4.dispatch(0, 0, 0, 0), "no observers should be supported");
        assertDoesNotThrow(() -> d5.dispatch(0, 0, 0, 0, 0), "no observers should be supported");
        assertDoesNotThrow(() -> d6.dispatch(0, 0, 0, 0, 0, 0), "no observers should be supported");
        assertDoesNotThrow(
                () -> d7.dispatch(0, 0, 0, 0, 0, 0, 0), "no observers should be supported");
        assertDoesNotThrow(
                () -> d8.dispatch(0, 0, 0, 0, 0, 0, 0, 0), "no observers should be supported");
        assertDoesNotThrow(
                () -> d9.dispatch(0, 0, 0, 0, 0, 0, 0, 0, 0), "no observers should be supported");
        assertDoesNotThrow(
                () -> d10.dispatch(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                "no observers should be supported");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    @DisplayName("One-To-One Dispatch Test")
    void oneToOneDispatchTest(final int dispatchBuildLocation) {
        final DispatchBuilder builder = new DispatchBuilder();

        TestDispatchZero d0 = null;
        TestDispatchOne d1 = null;
        TestDispatchTwo d2 = null;
        TestDispatchThree d3 = null;
        TestDispatchFour d4 = null;
        TestDispatchFive d5 = null;
        TestDispatchSix d6 = null;
        TestDispatchSeven d7 = null;
        TestDispatchEight d8 = null;
        TestDispatchNine d9 = null;
        TestDispatchTen d10 = null;

        if (dispatchBuildLocation == 0) {
            d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
            d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
            d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
            d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
            d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
            d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
            d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
            d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
            d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
            d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
            d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;
        }

        final AtomicInteger sum = new AtomicInteger();
        assertSame(
                builder,
                builder.registerObserver(TestDispatchZero.class, sum::getAndIncrement),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(TestDispatchOne.class, sum::getAndAdd),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(TestDispatchTwo.class, (a, b) -> sum.getAndAdd(a + b)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchThree.class, (a, b, c) -> sum.getAndAdd(a + b + c)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchFour.class, (a, b, c, d) -> sum.getAndAdd(a + b + c + d)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchFive.class,
                        (a, b, c, d, e) -> sum.getAndAdd(a + b + c + d + e)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchSix.class,
                        (a, b, c, d, e, f) -> sum.getAndAdd(a + b + c + d + e + f)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchSeven.class,
                        (a, b, c, d, e, f, g) -> sum.getAndAdd(a + b + c + d + e + f + g)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchEight.class,
                        (a, b, c, d, e, f, g, h) -> sum.getAndAdd(a + b + c + d + e + f + g + h)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchNine.class,
                        (a, b, c, d, e, f, g, h, i) ->
                                sum.getAndAdd(a + b + c + d + e + f + g + h + i)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchTen.class,
                        (a, b, c, d, e, f, g, h, i, j) ->
                                sum.getAndAdd(a + b + c + d + e + f + g + h + i + j)),
                "should have returned self");

        if (dispatchBuildLocation == 1) {
            d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
            d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
            d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
            d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
            d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
            d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
            d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
            d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
            d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
            d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
            d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;
        }

        builder.start();

        if (dispatchBuildLocation == 2) {
            d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
            d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
            d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
            d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
            d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
            d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
            d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
            d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
            d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
            d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
            d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;
        }

        assertNotNull(d0, "dispatcher should have been initialized by now");
        assertNotNull(d1, "dispatcher should have been initialized by now");
        assertNotNull(d2, "dispatcher should have been initialized by now");
        assertNotNull(d3, "dispatcher should have been initialized by now");
        assertNotNull(d4, "dispatcher should have been initialized by now");
        assertNotNull(d5, "dispatcher should have been initialized by now");
        assertNotNull(d6, "dispatcher should have been initialized by now");
        assertNotNull(d7, "dispatcher should have been initialized by now");
        assertNotNull(d8, "dispatcher should have been initialized by now");
        assertNotNull(d9, "dispatcher should have been initialized by now");
        assertNotNull(d10, "dispatcher should have been initialized by now");

        int expectedSum = 0;
        for (int i = 0; i < 100; i++) {
            expectedSum += 1;
            d0.dispatch();

            expectedSum += i;
            d1.dispatch(i);

            expectedSum += 2 * i;
            d2.dispatch(i, i);

            expectedSum += 3 * i;
            d3.dispatch(i, i, i);

            expectedSum += 4 * i;
            d4.dispatch(i, i, i, i);

            expectedSum += 5 * i;
            d5.dispatch(i, i, i, i, i);

            expectedSum += 6 * i;
            d6.dispatch(i, i, i, i, i, i);

            expectedSum += 7 * i;
            d7.dispatch(i, i, i, i, i, i, i);

            expectedSum += 8 * i;
            d8.dispatch(i, i, i, i, i, i, i, i);

            expectedSum += 9 * i;
            d9.dispatch(i, i, i, i, i, i, i, i, i);
        }

        assertEquals(expectedSum, sum.get(), "callbacks not invoked correctly");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    @DisplayName("One-To-Many Dispatch Test")
    void oneToManyDispatchTest(final int dispatchBuildLocation) {
        final DispatchBuilder builder = new DispatchBuilder();

        TestDispatchZero d0 = null;
        TestDispatchOne d1 = null;
        TestDispatchTwo d2 = null;
        TestDispatchThree d3 = null;
        TestDispatchFour d4 = null;
        TestDispatchFive d5 = null;
        TestDispatchSix d6 = null;
        TestDispatchSeven d7 = null;
        TestDispatchEight d8 = null;
        TestDispatchNine d9 = null;
        TestDispatchTen d10 = null;

        if (dispatchBuildLocation == 0) {
            d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
            d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
            d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
            d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
            d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
            d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
            d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
            d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
            d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
            d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
            d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;
        }

        final AtomicInteger sum1 = new AtomicInteger();
        assertSame(
                builder,
                builder.registerObserver(TestDispatchZero.class, sum1::getAndIncrement),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(TestDispatchOne.class, sum1::getAndAdd),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(TestDispatchTwo.class, (a, b) -> sum1.getAndAdd(a + b)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchThree.class, (a, b, c) -> sum1.getAndAdd(a + b + c)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchFour.class, (a, b, c, d) -> sum1.getAndAdd(a + b + c + d)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchFive.class,
                        (a, b, c, d, e) -> sum1.getAndAdd(a + b + c + d + e)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchSix.class,
                        (a, b, c, d, e, f) -> sum1.getAndAdd(a + b + c + d + e + f)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchSeven.class,
                        (a, b, c, d, e, f, g) -> sum1.getAndAdd(a + b + c + d + e + f + g)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchEight.class,
                        (a, b, c, d, e, f, g, h) -> sum1.getAndAdd(a + b + c + d + e + f + g + h)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchNine.class,
                        (a, b, c, d, e, f, g, h, i) ->
                                sum1.getAndAdd(a + b + c + d + e + f + g + h + i)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchTen.class,
                        (a, b, c, d, e, f, g, h, i, j) ->
                                sum1.getAndAdd(a + b + c + d + e + f + g + h + i + j)),
                "should have returned self");

        if (dispatchBuildLocation == 1) {
            d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
            d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
            d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
            d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
            d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
            d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
            d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
            d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
            d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
            d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
            d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;
        }

        final AtomicInteger sum2 = new AtomicInteger();
        assertSame(
                builder,
                builder.registerObserver(TestDispatchZero.class, sum2::getAndIncrement),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(TestDispatchOne.class, sum2::getAndAdd),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(TestDispatchTwo.class, (a, b) -> sum2.getAndAdd(a + b)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchThree.class, (a, b, c) -> sum2.getAndAdd(a + b + c)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchFour.class, (a, b, c, d) -> sum2.getAndAdd(a + b + c + d)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchFive.class,
                        (a, b, c, d, e) -> sum2.getAndAdd(a + b + c + d + e)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchSix.class,
                        (a, b, c, d, e, f) -> sum2.getAndAdd(a + b + c + d + e + f)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchSeven.class,
                        (a, b, c, d, e, f, g) -> sum2.getAndAdd(a + b + c + d + e + f + g)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchEight.class,
                        (a, b, c, d, e, f, g, h) -> sum2.getAndAdd(a + b + c + d + e + f + g + h)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchNine.class,
                        (a, b, c, d, e, f, g, h, i) ->
                                sum2.getAndAdd(a + b + c + d + e + f + g + h + i)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchTen.class,
                        (a, b, c, d, e, f, g, h, i, j) ->
                                sum2.getAndAdd(a + b + c + d + e + f + g + h + i + j)),
                "should have returned self");

        if (dispatchBuildLocation == 2) {
            d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
            d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
            d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
            d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
            d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
            d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
            d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
            d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
            d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
            d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
            d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;
        }

        final AtomicInteger sum3 = new AtomicInteger();
        assertSame(
                builder,
                builder.registerObserver(TestDispatchZero.class, sum3::getAndIncrement),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(TestDispatchOne.class, sum3::getAndAdd),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(TestDispatchTwo.class, (a, b) -> sum3.getAndAdd(a + b)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchThree.class, (a, b, c) -> sum3.getAndAdd(a + b + c)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchFour.class, (a, b, c, d) -> sum3.getAndAdd(a + b + c + d)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchFive.class,
                        (a, b, c, d, e) -> sum3.getAndAdd(a + b + c + d + e)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchSix.class,
                        (a, b, c, d, e, f) -> sum3.getAndAdd(a + b + c + d + e + f)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchSeven.class,
                        (a, b, c, d, e, f, g) -> sum3.getAndAdd(a + b + c + d + e + f + g)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchEight.class,
                        (a, b, c, d, e, f, g, h) -> sum3.getAndAdd(a + b + c + d + e + f + g + h)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchNine.class,
                        (a, b, c, d, e, f, g, h, i) ->
                                sum3.getAndAdd(a + b + c + d + e + f + g + h + i)),
                "should have returned self");
        assertSame(
                builder,
                builder.registerObserver(
                        TestDispatchTen.class,
                        (a, b, c, d, e, f, g, h, i, j) ->
                                sum3.getAndAdd(a + b + c + d + e + f + g + h + i + j)),
                "should have returned self");

        if (dispatchBuildLocation == 3) {
            d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
            d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
            d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
            d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
            d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
            d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
            d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
            d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
            d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
            d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
            d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;
        }

        builder.start();

        if (dispatchBuildLocation == 4) {
            d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
            d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
            d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
            d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
            d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
            d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
            d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
            d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
            d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
            d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
            d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;
        }

        assertNotNull(d0, "dispatcher should have been initialized by now");
        assertNotNull(d1, "dispatcher should have been initialized by now");
        assertNotNull(d2, "dispatcher should have been initialized by now");
        assertNotNull(d3, "dispatcher should have been initialized by now");
        assertNotNull(d4, "dispatcher should have been initialized by now");
        assertNotNull(d5, "dispatcher should have been initialized by now");
        assertNotNull(d6, "dispatcher should have been initialized by now");
        assertNotNull(d7, "dispatcher should have been initialized by now");
        assertNotNull(d8, "dispatcher should have been initialized by now");
        assertNotNull(d9, "dispatcher should have been initialized by now");
        assertNotNull(d10, "dispatcher should have been initialized by now");

        int expectedSum = 0;
        for (int i = 0; i < 100; i++) {
            expectedSum += 1;
            d0.dispatch();

            expectedSum += i;
            d1.dispatch(i);

            expectedSum += 2 * i;
            d2.dispatch(i, i);

            expectedSum += 3 * i;
            d3.dispatch(i, i, i);

            expectedSum += 4 * i;
            d4.dispatch(i, i, i, i);

            expectedSum += 5 * i;
            d5.dispatch(i, i, i, i, i);

            expectedSum += 6 * i;
            d6.dispatch(i, i, i, i, i, i);

            expectedSum += 7 * i;
            d7.dispatch(i, i, i, i, i, i, i);

            expectedSum += 8 * i;
            d8.dispatch(i, i, i, i, i, i, i, i);

            expectedSum += 9 * i;
            d9.dispatch(i, i, i, i, i, i, i, i, i);
        }

        assertEquals(expectedSum, sum1.get(), "callbacks not invoked correctly");
        assertEquals(expectedSum, sum2.get(), "callbacks not invoked correctly");
        assertEquals(expectedSum, sum3.get(), "callbacks not invoked correctly");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    @DisplayName("Auto-Register Test")
    void autoRegisterTest(final int dispatchBuildLocation) {
        final DispatchBuilder builder = new DispatchBuilder();

        TestDispatchZero d0 = null;
        TestDispatchOne d1 = null;
        TestDispatchTwo d2 = null;
        TestDispatchThree d3 = null;
        TestDispatchFour d4 = null;
        TestDispatchFive d5 = null;
        TestDispatchSix d6 = null;
        TestDispatchSeven d7 = null;
        TestDispatchEight d8 = null;
        TestDispatchNine d9 = null;
        TestDispatchTen d10 = null;

        if (dispatchBuildLocation == 0) {
            d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
            d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
            d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
            d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
            d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
            d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
            d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
            d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
            d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
            d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
            d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;
        }

        final ObserverClass observerClass1 = new ObserverClass();
        assertSame(
                builder, builder.registerObservers(observerClass1), "builder should return itself");

        if (dispatchBuildLocation == 1) {
            d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
            d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
            d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
            d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
            d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
            d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
            d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
            d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
            d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
            d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
            d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;
        }

        final ObserverClass observerClass2 = new ObserverClass();
        assertSame(
                builder, builder.registerObservers(observerClass2), "builder should return itself");

        if (dispatchBuildLocation == 2) {
            d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
            d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
            d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
            d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
            d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
            d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
            d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
            d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
            d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
            d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
            d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;
        }

        final ObserverClass observerClass3 = new ObserverClass();
        assertSame(
                builder, builder.registerObservers(observerClass3), "builder should return itself");

        if (dispatchBuildLocation == 3) {
            d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
            d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
            d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
            d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
            d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
            d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
            d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
            d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
            d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
            d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
            d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;
        }

        builder.start();

        if (dispatchBuildLocation == 4) {
            d0 = builder.getDispatcher(TestDispatchZero.class)::dispatch;
            d1 = builder.getDispatcher(TestDispatchOne.class)::dispatch;
            d2 = builder.getDispatcher(TestDispatchTwo.class)::dispatch;
            d3 = builder.getDispatcher(TestDispatchThree.class)::dispatch;
            d4 = builder.getDispatcher(TestDispatchFour.class)::dispatch;
            d5 = builder.getDispatcher(TestDispatchFive.class)::dispatch;
            d6 = builder.getDispatcher(TestDispatchSix.class)::dispatch;
            d7 = builder.getDispatcher(TestDispatchSeven.class)::dispatch;
            d8 = builder.getDispatcher(TestDispatchEight.class)::dispatch;
            d9 = builder.getDispatcher(TestDispatchNine.class)::dispatch;
            d10 = builder.getDispatcher(TestDispatchTen.class)::dispatch;
        }

        assertNotNull(d0, "dispatcher should have been initialized by now");
        assertNotNull(d1, "dispatcher should have been initialized by now");
        assertNotNull(d2, "dispatcher should have been initialized by now");
        assertNotNull(d3, "dispatcher should have been initialized by now");
        assertNotNull(d4, "dispatcher should have been initialized by now");
        assertNotNull(d5, "dispatcher should have been initialized by now");
        assertNotNull(d6, "dispatcher should have been initialized by now");
        assertNotNull(d7, "dispatcher should have been initialized by now");
        assertNotNull(d8, "dispatcher should have been initialized by now");
        assertNotNull(d9, "dispatcher should have been initialized by now");
        assertNotNull(d10, "dispatcher should have been initialized by now");

        int expectedSum = 0;
        for (int i = 0; i < 100; i++) {
            expectedSum += 1;
            d0.dispatch();

            expectedSum += i;
            d1.dispatch(i);

            expectedSum += 2 * i;
            d2.dispatch(i, i);

            expectedSum += 3 * i;
            d3.dispatch(i, i, i);

            expectedSum += 4 * i;
            d4.dispatch(i, i, i, i);

            expectedSum += 5 * i;
            d5.dispatch(i, i, i, i, i);

            expectedSum += 6 * i;
            d6.dispatch(i, i, i, i, i, i);

            expectedSum += 7 * i;
            d7.dispatch(i, i, i, i, i, i, i);

            expectedSum += 8 * i;
            d8.dispatch(i, i, i, i, i, i, i, i);

            expectedSum += 9 * i;
            d9.dispatch(i, i, i, i, i, i, i, i, i);
        }

        assertEquals(expectedSum, observerClass1.getCount(), "callbacks not invoked correctly");
        assertEquals(expectedSum, observerClass1.getCount(), "callbacks not invoked correctly");
        assertEquals(expectedSum, observerClass1.getCount(), "callbacks not invoked correctly");
    }
}
