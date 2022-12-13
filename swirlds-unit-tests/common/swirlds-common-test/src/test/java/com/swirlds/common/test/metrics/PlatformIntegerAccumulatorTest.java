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
package com.swirlds.common.test.metrics;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.metrics.IntegerAccumulator;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.platform.PlatformIntegerAccumulator;
import com.swirlds.common.metrics.platform.PlatformIntegerGauge;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotValue;
import com.swirlds.common.statistics.StatsBuffered;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformIntegerAccumulatorTest {

    private static final String CATEGORY = "CaTeGoRy";
    private static final String NAME = "NaMe";
    private static final String DESCRIPTION = "DeScRiPtIoN";
    private static final String UNIT = "UnIt";
    private static final String FORMAT = "FoRmAt";

    @Test
    @DisplayName("Constructor should store values")
    void testConstructor() {
        final IntegerAccumulator.Config config =
                new IntegerAccumulator.Config(CATEGORY, NAME)
                        .withDescription(DESCRIPTION)
                        .withUnit(UNIT)
                        .withFormat(FORMAT)
                        .withInitialValue(42);
        final IntegerAccumulator accumulator = new PlatformIntegerAccumulator(config);

        assertEquals(
                CATEGORY,
                accumulator.getCategory(),
                "The category was not set correctly in the constructor");
        assertEquals(
                NAME, accumulator.getName(), "The name was not set correctly in the constructor");
        assertEquals(
                DESCRIPTION,
                accumulator.getDescription(),
                "The description was not set correctly in the constructor");
        assertEquals(
                FORMAT,
                accumulator.getFormat(),
                "The format was not set correctly in the constructor");
        assertEquals(
                42,
                accumulator.getInitialValue(),
                "The initial value was not initialized correctly");
        assertEquals(42, accumulator.get(), "The value was not initialized correctly");
        assertEquals(42, accumulator.get(VALUE), "The value was not initialized correctly");
        assertThat(accumulator.getValueTypes()).containsExactly(VALUE);
    }

    @Test
    @DisplayName("Test of get() and update()-operation")
    void testGetAndUpdate() {
        // given
        final IntegerAccumulator.Config config =
                new IntegerAccumulator.Config(CATEGORY, NAME)
                        .withAccumulator((op1, op2) -> op1 - op2)
                        .withInitialValue(2);
        final IntegerAccumulator accumulator = new PlatformIntegerAccumulator(config);

        // when
        accumulator.update(5);

        // then
        assertEquals(-3, accumulator.get(), "Value should be -3");
        assertEquals(-3, accumulator.get(VALUE), "Value should be -3");

        // when
        accumulator.update(3);

        // then
        assertEquals(-6, accumulator.get(), "Value should be -6");
        assertEquals(-6, accumulator.get(VALUE), "Value should be -6");
    }

    @Test
    void testPositiveOverflow() {
        // given
        final IntegerAccumulator.Config config =
                new IntegerAccumulator.Config(CATEGORY, NAME)
                        .withAccumulator(Integer::sum)
                        .withInitialValue(Integer.MAX_VALUE);
        final IntegerAccumulator accumulator = new PlatformIntegerAccumulator(config);

        // when
        accumulator.update(1);

        // then
        final int expected = Integer.MAX_VALUE + 1;
        assertEquals(
                expected,
                accumulator.get(),
                "Value should be the same as if a regular int overflows");
    }

    @Test
    void testNegativeOverflow() {
        // given
        final IntegerAccumulator.Config config =
                new IntegerAccumulator.Config(CATEGORY, NAME)
                        .withAccumulator(Integer::sum)
                        .withInitialValue(Integer.MIN_VALUE);
        final IntegerAccumulator accumulator = new PlatformIntegerAccumulator(config);

        // when
        accumulator.update(-1);

        // then
        final int expected = Integer.MIN_VALUE - 1;
        assertEquals(
                expected,
                accumulator.get(),
                "Value should be the same as if a regular int overflows");
    }

    @Test
    void testSnapshot() {
        // given
        final IntegerAccumulator.Config config =
                new IntegerAccumulator.Config(CATEGORY, NAME).withInitialValue(2);
        final PlatformIntegerAccumulator accumulator = new PlatformIntegerAccumulator(config);
        accumulator.update(5);

        // when
        final List<SnapshotValue> snapshot = accumulator.takeSnapshot();

        // then
        assertEquals(2, accumulator.get(), "Value should be 2");
        assertEquals(2, accumulator.get(VALUE), "Value should be 2");
        assertThat(snapshot).containsExactly(new SnapshotValue(VALUE, 5));
    }

    @Test
    void testInvalidGets() {
        // given
        final IntegerAccumulator.Config config = new IntegerAccumulator.Config(CATEGORY, NAME);
        final IntegerAccumulator accumulator = new PlatformIntegerAccumulator(config);

        // then
        assertThrows(
                IllegalArgumentException.class,
                () -> accumulator.get(null),
                "Calling get() with null should throw an IAE");
        assertThrows(
                IllegalArgumentException.class,
                () -> accumulator.get(Metric.ValueType.COUNTER),
                "Calling get() with an unsupported MetricType should throw an IAE");
        assertThrows(
                IllegalArgumentException.class,
                () -> accumulator.get(Metric.ValueType.MIN),
                "Calling get() with an unsupported MetricType should throw an IAE");
        assertThrows(
                IllegalArgumentException.class,
                () -> accumulator.get(Metric.ValueType.MAX),
                "Calling get() with an unsupported MetricType should throw an IAE");
        assertThrows(
                IllegalArgumentException.class,
                () -> accumulator.get(Metric.ValueType.STD_DEV),
                "Calling get() with an unsupported MetricType should throw an IAE");
    }

    @Test
    void testReset() {
        // given
        final IntegerAccumulator.Config config = new IntegerAccumulator.Config(CATEGORY, NAME);
        final IntegerAccumulator accumulator = new PlatformIntegerAccumulator(config);

        // then
        assertThatCode(accumulator::reset).doesNotThrowAnyException();
    }

    @SuppressWarnings("removal")
    @Test
    void testGetStatBuffered() {
        // given
        final IntegerAccumulator.Config config = new IntegerAccumulator.Config(CATEGORY, NAME);
        final IntegerAccumulator accumulator = new PlatformIntegerAccumulator(config);

        // when
        final StatsBuffered actual = accumulator.getStatsBuffered();

        // then
        assertThat(actual).isNull();
    }

    @Test
    void testEquals() {
        // given
        final IntegerAccumulator.Config config = new IntegerAccumulator.Config(CATEGORY, NAME);
        final IntegerAccumulator accumulator1 = new PlatformIntegerAccumulator(config);
        final IntegerAccumulator accumulator2 = new PlatformIntegerAccumulator(config);
        accumulator2.update(42);

        // then
        assertThat(accumulator1)
                .isEqualTo(accumulator2)
                .hasSameHashCodeAs(accumulator2)
                .isNotEqualTo(
                        new PlatformIntegerAccumulator(
                                new IntegerAccumulator.Config("Other", NAME)))
                .isNotEqualTo(
                        new PlatformIntegerAccumulator(
                                new IntegerAccumulator.Config(CATEGORY, "Other")))
                .isNotEqualTo(new PlatformIntegerGauge(new IntegerGauge.Config(CATEGORY, NAME)));
    }

    @Test
    void testToString() {
        // given
        final IntegerAccumulator.Config config =
                new IntegerAccumulator.Config(CATEGORY, NAME)
                        .withDescription(DESCRIPTION)
                        .withUnit(UNIT)
                        .withFormat(FORMAT)
                        .withInitialValue(42);
        final IntegerAccumulator accumulator = new PlatformIntegerAccumulator(config);

        // then
        assertThat(accumulator.toString())
                .contains(
                        CATEGORY,
                        NAME,
                        DESCRIPTION,
                        UNIT,
                        FORMAT,
                        Metric.DataType.INT.toString(),
                        "42");
    }
}
