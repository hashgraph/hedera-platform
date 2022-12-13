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

import static com.swirlds.common.metrics.Metric.DataType.FLOAT;
import static com.swirlds.common.metrics.Metric.ValueType.MAX;
import static com.swirlds.common.metrics.Metric.ValueType.MIN;
import static com.swirlds.common.metrics.Metric.ValueType.STD_DEV;
import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.platform.PlatformIntegerGauge;
import com.swirlds.common.metrics.platform.PlatformRunningAverageMetric;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotValue;
import com.swirlds.common.test.DummyClock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformRunningAverageMetricTest {

    private static final String CATEGORY = "CaTeGoRy";
    private static final String NAME = "NaMe";
    private static final String DESCRIPTION = "DeScRiPtIoN";
    private static final String UNIT = "UnIt";
    private static final String FORMAT = "FoRmAt";

    private static final double EPSILON = 1e-6;

    @SuppressWarnings("removal")
    @Test
    @DisplayName("Constructor should store values")
    void testConstructor() {
        // when
        final RunningAverageMetric.Config config =
                new RunningAverageMetric.Config(CATEGORY, NAME)
                        .withDescription(DESCRIPTION)
                        .withUnit(UNIT)
                        .withFormat(FORMAT)
                        .withHalfLife(Math.PI);
        final RunningAverageMetric metric = new PlatformRunningAverageMetric(config);

        // then
        assertEquals(CATEGORY, metric.getCategory(), "The category was not set correctly");
        assertEquals(NAME, metric.getName(), "The name was not set correctly");
        assertEquals(DESCRIPTION, metric.getDescription(), "The description was not set correctly");
        assertEquals(UNIT, metric.getUnit(), "The unit was not set correctly in the constructor");
        assertEquals(FORMAT, metric.getFormat(), "The format was not set correctly");
        assertEquals(
                Math.PI, metric.getHalfLife(), EPSILON, "HalfLife was not initialized correctly");
        assertEquals(0.0, metric.get(), EPSILON, "The value was not initialized correctly");
        assertEquals(0.0, metric.get(VALUE), EPSILON, "The value was not initialized correctly");
        assertEquals(0.0, metric.get(MIN), EPSILON, "The minimum was not initialized correctly");
        assertEquals(0.0, metric.get(MAX), EPSILON, "The maximum was not initialized correctly");
        assertEquals(
                0.0,
                metric.get(STD_DEV),
                EPSILON,
                "The standard deviation was not initialized correctly");
        assertNotNull(metric.getStatsBuffered(), "StatsBuffered was not initialized correctly");
        assertThat(metric.getValueTypes()).containsExactly(VALUE, MAX, MIN, STD_DEV);
    }

    @Test
    void testReset() {
        // given
        final DummyClock clock = new DummyClock();
        final RunningAverageMetric.Config config = new RunningAverageMetric.Config(CATEGORY, NAME);
        final RunningAverageMetric metric = new PlatformRunningAverageMetric(config, clock);
        recordValues(metric, clock, 0, 1000, Math.E);
        clock.setSeconds(1000);
        assertEquals(Math.E, metric.get(), EPSILON, "Mean should be " + Math.E);

        // when
        metric.reset();
        clock.set(Duration.ofSeconds(1000, 1));

        // then
        assertEquals(Math.E, metric.get(), EPSILON, "Mean should (?) still be " + Math.E);

        // when
        clock.set(Duration.ofSeconds(1000, 2));
        metric.update(Math.PI);

        // then
        assertEquals(Math.PI, metric.get(), EPSILON, "Mean should now be " + Math.PI);

        // when
        recordValues(metric, clock, 1000, 2000, Math.PI);
        clock.setSeconds(2000);

        // then
        assertEquals(Math.PI, metric.get(), EPSILON, "Rate should be " + Math.PI);
    }

    @Test
    void testRegularUpdates() {
        // given
        final DummyClock clock = new DummyClock();
        final RunningAverageMetric.Config config = new RunningAverageMetric.Config(CATEGORY, NAME);
        final RunningAverageMetric metric = new PlatformRunningAverageMetric(config, clock);

        for (int i = 0; i < 1000; i++) {
            // when
            clock.set(Duration.ofSeconds(i).plusMillis(500));
            metric.update(Math.PI);
            clock.set(Duration.ofSeconds(i + 1));
            final double mean = metric.get();

            // then
            assertEquals(Math.PI, mean, EPSILON, "Mean should be " + Math.PI);
        }
    }

    @Test
    void testDistributionForRegularUpdates() {
        // given
        final DummyClock clock = new DummyClock();
        final RunningAverageMetric.Config config = new RunningAverageMetric.Config(CATEGORY, NAME);
        final RunningAverageMetric metric = new PlatformRunningAverageMetric(config, clock);

        // when
        recordValues(metric, clock, 0, 1000, Math.PI);
        clock.setSeconds(1000);
        double avg = metric.get();

        // then
        assertEquals(Math.PI, avg, EPSILON, "Value should be " + Math.PI);
        assertEquals(Math.PI, metric.get(VALUE), EPSILON, "Mean value should be " + Math.PI);
        assertEquals(Math.PI, metric.get(MIN), EPSILON, "Min. should be " + Math.PI);
        assertEquals(Math.PI, metric.get(MAX), EPSILON, "Max. should be " + Math.PI);
        assertEquals(0.0, metric.get(STD_DEV), EPSILON, "Standard deviation should be around 0.0");
    }

    @Test
    void testDistributionForIncreasedValue() {
        // given
        final DummyClock clock = new DummyClock();
        final RunningAverageMetric.Config config = new RunningAverageMetric.Config(CATEGORY, NAME);
        final RunningAverageMetric metric = new PlatformRunningAverageMetric(config, clock);

        // when
        recordValues(metric, clock, 0, 1000, Math.E);
        recordValues(metric, clock, 1000, 1000 + (int) SettingsCommon.halfLife, Math.PI);
        clock.setSeconds(1000 + (int) SettingsCommon.halfLife);
        double avg = metric.get();

        // then
        final double expected = 0.5 * (Math.E + Math.PI);
        assertEquals(expected, avg, EPSILON, "Value should be " + expected);
        assertEquals(expected, metric.get(VALUE), EPSILON, "Mean value should be " + expected);
        assertEquals(expected, metric.get(MAX), EPSILON, "Max. value should be " + expected);
    }

    @Test
    void testDistributionForTwiceIncreasedValue() {
        // given
        final DummyClock clock = new DummyClock();
        final RunningAverageMetric.Config config = new RunningAverageMetric.Config(CATEGORY, NAME);
        final RunningAverageMetric metric = new PlatformRunningAverageMetric(config, clock);

        // when
        recordValues(metric, clock, 0, 1000, Math.E);
        recordValues(metric, clock, 1000, 1000 + (int) SettingsCommon.halfLife, Math.PI);
        recordValues(
                metric,
                clock,
                1000 + (int) SettingsCommon.halfLife,
                1000 + 2 * (int) SettingsCommon.halfLife,
                Math.PI + 0.5 * (Math.PI - Math.E));
        clock.setSeconds(1000 + 2 * (int) SettingsCommon.halfLife);
        double avg = metric.get();

        // then
        assertEquals(Math.PI, avg, EPSILON, "Value should be " + Math.PI);
        assertEquals(Math.PI, metric.get(VALUE), EPSILON, "Mean value should be " + Math.PI);
        assertEquals(Math.PI, metric.get(MAX), EPSILON, "Max. value should be " + Math.PI);
    }

    @Test
    void testDistributionForDecreasedValue() {
        // given
        final DummyClock clock = new DummyClock();
        final RunningAverageMetric.Config config = new RunningAverageMetric.Config(CATEGORY, NAME);
        final RunningAverageMetric metric = new PlatformRunningAverageMetric(config, clock);

        // when
        recordValues(metric, clock, 0, 1000, Math.PI);
        recordValues(metric, clock, 1000, 1000 + (int) SettingsCommon.halfLife, Math.E);
        clock.setSeconds(1000 + (int) SettingsCommon.halfLife);
        double avg = metric.get();

        // then
        final double expected = 0.5 * (Math.E + Math.PI);
        assertEquals(expected, avg, EPSILON, "Value should be " + expected);
        assertEquals(expected, metric.get(VALUE), EPSILON, "Mean value should be " + expected);
        assertEquals(expected, metric.get(MIN), EPSILON, "Min. value should be " + expected);
    }

    @Test
    void testDistributionForTwiceDecreasedValue() {
        // given
        final DummyClock clock = new DummyClock();
        final RunningAverageMetric.Config config = new RunningAverageMetric.Config(CATEGORY, NAME);
        final RunningAverageMetric metric = new PlatformRunningAverageMetric(config, clock);

        // when
        recordValues(metric, clock, 0, 1000, Math.PI);
        recordValues(metric, clock, 1000, 1000 + (int) SettingsCommon.halfLife, Math.E);
        recordValues(
                metric,
                clock,
                1000 + (int) SettingsCommon.halfLife,
                1000 + 2 * (int) SettingsCommon.halfLife,
                Math.E - 0.5 * (Math.PI - Math.E));
        clock.setSeconds(1000 + 2 * (int) SettingsCommon.halfLife);
        double avg = metric.get();

        // then
        assertEquals(Math.E, avg, EPSILON, "Value should be " + Math.E);
        assertEquals(Math.E, metric.get(VALUE), EPSILON, "Mean value should be " + Math.E);
        assertEquals(Math.E, metric.get(MIN), EPSILON, "Min. value should be " + Math.E);
    }

    @Test
    void testSnapshot() {
        // given
        final DummyClock clock = new DummyClock();
        final RunningAverageMetric.Config config = new RunningAverageMetric.Config(CATEGORY, NAME);
        final PlatformRunningAverageMetric metric = new PlatformRunningAverageMetric(config, clock);

        // when
        recordValues(metric, clock, 0, 1000, Math.PI);
        clock.setSeconds(1000);
        final double avg = metric.get();
        final List<SnapshotValue> snapshot = metric.takeSnapshot();

        // then
        assertEquals(Math.PI, avg, EPSILON, "Value should be " + Math.PI);
        assertEquals(Math.PI, metric.get(VALUE), EPSILON, "Mean value should be " + Math.PI);
        assertEquals(Math.PI, metric.get(MIN), EPSILON, "Min. should be " + Math.PI);
        assertEquals(Math.PI, metric.get(MAX), EPSILON, "Max. should be " + Math.PI);
        assertEquals(0.0, metric.get(STD_DEV), EPSILON, "Standard deviation should be around 0.0");
        assertEquals(VALUE, snapshot.get(0).valueType());
        assertEquals(
                Math.PI,
                (double) snapshot.get(0).value(),
                EPSILON,
                "Mean value should be " + Math.PI);
        assertEquals(MAX, snapshot.get(1).valueType());
        assertEquals(
                Math.PI,
                (double) snapshot.get(1).value(),
                EPSILON,
                "Max. value should be " + Math.PI);
        assertEquals(MIN, snapshot.get(2).valueType());
        assertEquals(
                Math.PI,
                (double) snapshot.get(2).value(),
                EPSILON,
                "Min. value should be " + Math.PI);
        assertEquals(STD_DEV, snapshot.get(3).valueType());
        assertEquals(
                0.0, (double) snapshot.get(3).value(), EPSILON, "Standard deviation should be 0");
    }

    @Test
    void testInvalidGets() {
        // given
        final RunningAverageMetric.Config config = new RunningAverageMetric.Config(CATEGORY, NAME);
        final RunningAverageMetric metric = new PlatformRunningAverageMetric(config);

        // then
        assertThrows(
                IllegalArgumentException.class,
                () -> metric.get(null),
                "Calling get() with null should throw an IAE");
        assertThrows(
                IllegalArgumentException.class,
                () -> metric.get(Metric.ValueType.COUNTER),
                "Calling get() with an unsupported MetricType should throw an IAE");
    }

    @Test
    void testEquals() {
        // given
        final RunningAverageMetric.Config config = new RunningAverageMetric.Config(CATEGORY, NAME);
        final RunningAverageMetric metric1 = new PlatformRunningAverageMetric(config);
        final RunningAverageMetric metric2 = new PlatformRunningAverageMetric(config);
        metric2.update(1000.0);

        // then
        assertThat(metric1)
                .isEqualTo(metric2)
                .hasSameHashCodeAs(metric2)
                .isNotEqualTo(
                        new PlatformRunningAverageMetric(
                                new RunningAverageMetric.Config("Other", NAME)))
                .isNotEqualTo(
                        new PlatformRunningAverageMetric(
                                new RunningAverageMetric.Config(CATEGORY, "Other")))
                .isNotEqualTo(new PlatformIntegerGauge(new IntegerGauge.Config(CATEGORY, NAME)));
    }

    @Test
    void testToString() {
        // given
        final RunningAverageMetric.Config config =
                new RunningAverageMetric.Config(CATEGORY, NAME)
                        .withDescription(DESCRIPTION)
                        .withUnit(UNIT)
                        .withFormat(FORMAT)
                        .withHalfLife(Math.PI);
        final RunningAverageMetric metric = new PlatformRunningAverageMetric(config);

        // then
        assertThat(metric.toString())
                .contains(CATEGORY, NAME, DESCRIPTION, UNIT, FORMAT, FLOAT.toString(), "3.1415");
    }

    private static void recordValues(
            final RunningAverageMetric metric,
            final DummyClock clock,
            final int start,
            final int stop,
            final double value) {
        for (int i = start; i < stop; i++) {
            clock.set(Duration.ofSeconds(i).plusMillis(500));
            metric.update(value);
        }
    }
}
