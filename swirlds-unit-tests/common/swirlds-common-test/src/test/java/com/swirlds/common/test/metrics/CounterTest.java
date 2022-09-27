/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.test.metrics;

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metric;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.swirlds.common.metrics.Counter.Mode.INCREASE_AND_DECREASE;
import static com.swirlds.common.metrics.Counter.Mode.INCREASE_ONLY;
import static com.swirlds.common.metrics.Metric.ValueType.COUNTER;
import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CounterTest {

    private static final String CATEGORY = "CaTeGoRy";
    private static final String NAME = "NaMe";
    private static final String DESCRIPTION = "DeScRiPtIoN";

    @Test
    @DisplayName("Constructor should store values")
    void testConstructor() {
        final Counter counter = new Counter(CATEGORY, NAME, DESCRIPTION);

        assertEquals(CATEGORY, counter.getCategory(), "The category was not set correctly in the constructor");
        assertEquals(NAME, counter.getName(), "The name was not set correctly in the constructor");
        assertEquals(DESCRIPTION, counter.getDescription(), "The description was not set correctly in the constructor");
        assertEquals(INCREASE_ONLY, counter.getMode(), "The mode should be INCREASE_ONLY");
        assertEquals(0L, counter.get(), "The value was not initialized correctly");
        assertEquals(0L, counter.get(COUNTER), "The value was not initialized correctly");
        assertEquals(0L, counter.get(VALUE), "The value was not initialized correctly");
        assertEquals(List.of(COUNTER), counter.getValueTypes(), "ValueTypes should be [COUNTER]");
    }

    @Test
    @DisplayName("Constructor should throw IAE when passing null")
    void testConstructorWithNullParameter() {
        assertThrows(IllegalArgumentException.class, () -> new Counter(null, NAME, DESCRIPTION),
                "Calling the constructor without a category should throw an IAE");
        assertThrows(IllegalArgumentException.class, () -> new Counter(CATEGORY, null, DESCRIPTION),
                "Calling the constructor without a name should throw an IAE");
        assertThrows(IllegalArgumentException.class, () -> new Counter(CATEGORY, NAME, null),
                "Calling the constructor without a description should throw an IAE");
    }

    @Test
    @DisplayName("Constructor with mode should store values")
    void testModeConstructor() {
        final Counter counter = new Counter(CATEGORY, NAME, DESCRIPTION, INCREASE_AND_DECREASE);

        assertEquals(CATEGORY, counter.getCategory(), "The category was not set correctly in the constructor");
        assertEquals(NAME, counter.getName(), "The name was not set correctly in the constructor");
        assertEquals(DESCRIPTION, counter.getDescription(), "The description was not set correctly in the constructor");
        assertEquals(INCREASE_AND_DECREASE, counter.getMode(), "The mode should be INCREASE_ONLY");
        assertEquals(0L, counter.get(), "The value was not initialized correctly");
        assertEquals(0L, counter.get(COUNTER), "The value was not initialized correctly");
        assertEquals(0L, counter.get(VALUE), "The value was not initialized correctly");
        assertEquals(List.of(VALUE), counter.getValueTypes(), "ValueTypes should be [VALUE]");
    }

    @Test
    @DisplayName("Constructor with mode should throw IAE when passing null")
    void testInitialValueConstructorWithNullParameter() {
        assertThrows(IllegalArgumentException.class, () -> new Counter(null, NAME, DESCRIPTION, INCREASE_ONLY),
                "Calling the constructor without a category should throw an IAE");
        assertThrows(IllegalArgumentException.class, () -> new Counter(CATEGORY, null, DESCRIPTION, INCREASE_ONLY),
                "Calling the constructor without a name should throw an IAE");
        assertThrows(IllegalArgumentException.class, () -> new Counter(CATEGORY, NAME, null, INCREASE_ONLY),
                "Calling the constructor without a description should throw an IAE");
        assertThrows(IllegalArgumentException.class, () -> new Counter(CATEGORY, NAME, DESCRIPTION, null),
                "Calling the constructor without a description should throw an IAE");
    }

    @Test
    @DisplayName("Counter should add non-negative values")
    void testAddingValues() {
        // given
        final Counter counter = new Counter(CATEGORY, NAME, DESCRIPTION);

        // when
        counter.add(3L);

        // then
        assertEquals(3L, counter.get(), "Value should be 3");
        assertEquals(3L, counter.get(COUNTER), "Value should be 3");
        assertEquals(3L, counter.get(VALUE), "Value should be 3");

        // when
        counter.add(5L);

        // then
        assertEquals(8L, counter.get(), "Value should be 8");
        assertEquals(8L, counter.get(COUNTER), "Value should be 8");
        assertEquals(8L, counter.get(VALUE), "Value should be 8");
    }

    @Test
    @DisplayName("Increase-only Counter should not allow to add negative value")
    void testAddingNegativeValueIncreaseOnly() {
        final Counter counter = new Counter(CATEGORY, NAME, DESCRIPTION);
        assertThrows(IllegalArgumentException.class, () -> counter.add(-1L),
                "Calling add() with negative value should throw IAE");
    }

    @Test
    @DisplayName("Increase-and-decrease Counter should add negative value")
    void testAddingNegativeValueIncreaseAndDecrease() {
        // given
        final Counter counter = new Counter(CATEGORY, NAME, DESCRIPTION, INCREASE_AND_DECREASE);

        // when
        counter.add(-3L);

        // then
        assertEquals(-3L, counter.get(), "Value should be -3");
        assertEquals(-3L, counter.get(COUNTER), "Value should be -3");
        assertEquals(-3L, counter.get(VALUE), "Value should be -3");
    }

    @Test
    @DisplayName("Increase-only Counter should not subtract")
    void testSubtractingValueIncreaseOnly() {
        final Counter counter = new Counter(CATEGORY, NAME, DESCRIPTION);
        assertThrows(UnsupportedOperationException.class, () -> counter.subtract(1L),
                "Calling subtract() should throw IAE");
    }

    @Test
    @DisplayName("Increase-and-decrease Counter should subtract values")
    void testSubtractingValuesIncreaseAndDecrease() {
        // given
        final Counter counter = new Counter(CATEGORY, NAME, DESCRIPTION, INCREASE_AND_DECREASE);

        // when
        counter.subtract(3L);

        // then
        assertEquals(-3L, counter.get(), "Value should be -3");
        assertEquals(-3L, counter.get(COUNTER), "Value should be -3");
        assertEquals(-3L, counter.get(VALUE), "Value should be -3");

        // when
        counter.subtract(-5L);

        // then
        assertEquals(2L, counter.get(), "Value should be 2");
        assertEquals(2L, counter.get(COUNTER), "Value should be 2");
        assertEquals(2L, counter.get(VALUE), "Value should be 2");

        // when
        counter.subtract(0L);

        // then
        assertEquals(2L, counter.get(), "Value should be 2");
        assertEquals(2L, counter.get(COUNTER), "Value should be 2");
        assertEquals(2L, counter.get(VALUE), "Value should be 2");
    }

    @Test
    @DisplayName("Counter should increment by 1")
    void testIncrement() {
        // given
        final Counter counter = new Counter(CATEGORY, NAME, DESCRIPTION);

        // when
        counter.increment();

        // then
        assertEquals(1L, counter.get(), "Value should be 1");
        assertEquals(1L, counter.get(COUNTER), "Value should be 1");
        assertEquals(1L, counter.get(VALUE), "Value should be 1");

        // when
        counter.increment();

        // then
        assertEquals(2L, counter.get(), "Value should be 2");
        assertEquals(2L, counter.get(COUNTER), "Value should be 2");
        assertEquals(2L, counter.get(VALUE), "Value should be 2");
    }

    @Test
    @DisplayName("Increase-only Counter should not decrement")
    void testDecrementIncreaseOnly() {
        final Counter counter = new Counter(CATEGORY, NAME, DESCRIPTION);
        assertThrows(UnsupportedOperationException.class, counter::decrement,
                "Calling subtract() should throw IAE");
    }

    @Test
    @DisplayName("Increase-and-decrease Counter should decrement by 1")
    void testDecrementIncreaseAndDecrease() {
        // given
        final Counter counter = new Counter(CATEGORY, NAME, DESCRIPTION, INCREASE_AND_DECREASE);

        // when
        counter.decrement();

        // then
        assertEquals(-1L, counter.get(), "Value should be -1");
        assertEquals(-1L, counter.get(COUNTER), "Value should be -1");
        assertEquals(-1L, counter.get(VALUE), "Value should be -1");

        // when
        counter.decrement();

        // then
        assertEquals(-2L, counter.get(), "Value should be -2");
        assertEquals(-2L, counter.get(COUNTER), "Value should be -2");
        assertEquals(-2L, counter.get(VALUE), "Value should be -2");
    }

    @Test
    void testSnapshotWithIncreaseOnly() {
        // given
        final Counter counter = new Counter(CATEGORY, NAME, DESCRIPTION);
        counter.add(2L);

        // when
        final List<Pair<Metric.ValueType, Object>> snapshot = counter.takeSnapshot();

        // then
        assertEquals(2L, counter.get(), "Value should be 2");
        assertEquals(2L, counter.get(COUNTER), "Value should be 2");
        assertEquals(2L, counter.get(VALUE), "Value should be 2");
        assertEquals(List.of(Pair.of(COUNTER, 2L)), snapshot, "Snapshot is not correct");
    }

    @Test
    void testSnapshotWithIncreaseAndDecrease() {
        // given
        final Counter counter = new Counter(CATEGORY, NAME, DESCRIPTION, INCREASE_AND_DECREASE);
        counter.add(2L);

        // when
        final List<Pair<Metric.ValueType, Object>> snapshot = counter.takeSnapshot();

        // then
        assertEquals(2L, counter.get(), "Value should be 2");
        assertEquals(2L, counter.get(COUNTER), "Value should be 2");
        assertEquals(2L, counter.get(VALUE), "Value should be 2");
        assertEquals(List.of(Pair.of(VALUE, 2L)), snapshot, "Snapshot is not correct");
    }

    @Test
    void testInvalidGets() {
        // given
        final Counter counter = new Counter(CATEGORY, NAME, DESCRIPTION, INCREASE_AND_DECREASE);

        // then
        assertThrows(IllegalArgumentException.class, () -> counter.get(null),
                "Calling get() with null should throw an IAE");
        assertThrows(IllegalArgumentException.class, () -> counter.get(Metric.ValueType.MIN),
                "Calling get() with an unsupported MetricType should throw an IAE");
        assertThrows(IllegalArgumentException.class, () -> counter.get(Metric.ValueType.MAX),
                "Calling get() with an unsupported MetricType should throw an IAE");
        assertThrows(IllegalArgumentException.class, () -> counter.get(Metric.ValueType.STD_DEV),
                "Calling get() with an unsupported MetricType should throw an IAE");
    }
}
