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
import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.platform.PlatformIntegerGauge;
import com.swirlds.common.metrics.platform.PlatformSpeedometerMetric;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotValue;
import com.swirlds.common.statistics.StatsBuffered;
import com.swirlds.common.test.DummyClock;
import com.swirlds.test.framework.TestQualifierTags;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class PlatformSpeedometerMetricTest {

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
        final SpeedometerMetric.Config config =
                new SpeedometerMetric.Config(CATEGORY, NAME)
                        .withDescription(DESCRIPTION)
                        .withUnit(UNIT)
                        .withFormat(FORMAT)
                        .withHalfLife(Math.PI);
        final SpeedometerMetric metric = new PlatformSpeedometerMetric(config);

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
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);
        final SpeedometerMetric metric = new PlatformSpeedometerMetric(config, clock);
        sendCycles(metric, clock, 0, 1000, 1000);
        clock.setSeconds(1000);
        assertEquals(1000.0, metric.get(), 0.001, "Rate should be 1000.0");

        // when
        metric.reset();
        clock.set(Duration.ofSeconds(1000, 1));

        // then
        assertEquals(0.0, metric.get(), EPSILON, "Rate should be reset to 0.0");

        // when
        sendCycles(metric, clock, 1000, 2000, 2000);
        clock.setSeconds(2000);

        // then
        assertEquals(2000.0, metric.get(), 0.001, "Rate should be 2000.0");
    }

    @Test
    void testRegularRateOnePerSecond() {
        // given
        final DummyClock clock = new DummyClock();
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);
        final SpeedometerMetric metric = new PlatformSpeedometerMetric(config, clock);

        for (int i = 0; i < 1000; i++) {
            // when
            clock.set(Duration.ofSeconds(i).plusMillis(500));
            metric.cycle();
            clock.set(Duration.ofSeconds(i + 1));
            final double rate = metric.get();

            // then
            assertEquals(1.0, rate, 0.001, "Rate should be 1.0");
        }
    }

    @Test
    void testRegularRateFivePerSecond() {
        // given
        final DummyClock clock = new DummyClock();
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);
        final SpeedometerMetric metric = new PlatformSpeedometerMetric(config, clock);

        // when
        for (int i = 0; i < 1000; i++) {
            // when
            for (int j = 1; j <= 5; j++) {
                clock.set(Duration.ofSeconds(i).plus(Duration.ofSeconds(j).dividedBy(6)));
                metric.cycle();
            }
            clock.set(Duration.ofSeconds(i + 1));
            final double rate = metric.get();

            // then
            assertEquals(5.0, rate, 0.001, "Rate should be 5.0");
        }
    }

    @Test
    void testRegularRateFivePerSecondWithUpdate() {
        // given
        final DummyClock clock = new DummyClock();
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);
        final SpeedometerMetric metric = new PlatformSpeedometerMetric(config, clock);

        // when
        for (int i = 0; i < 1000; i++) {
            // when
            clock.set(Duration.ofSeconds(i).plusMillis(500));
            metric.update(5);
            clock.set(Duration.ofSeconds(i + 1));
            final double rate = metric.get();

            // then
            assertEquals(5.0, rate, 0.01, "Rate should be 5.0");
        }
    }

    @Test
    void testDistributionForRegularRate() {
        // given
        final DummyClock clock = new DummyClock();
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);
        final SpeedometerMetric metric = new PlatformSpeedometerMetric(config, clock);

        // when
        sendCycles(metric, clock, 0, 1000, 1000);
        clock.setSeconds(1000);
        double rate = metric.get();

        // then
        assertEquals(1000.0, rate, 0.001, "Rate should be 1000.0");
        assertEquals(1000.0, metric.get(VALUE), 0.001, "Mean rate should be 1000.0");
        assertEquals(1000.0, metric.get(MIN), 0.1, "Min. rate should be about 1000.0");
        assertEquals(1000.0, metric.get(MAX), 0.1, "Max. rate should be about 1000.0");
        assertEquals(0.02, metric.get(STD_DEV), 0.001, "Standard deviation should be around 0.02");
    }

    @Disabled("Fails currently. Should be enabled with new speedometer implementation")
    @Test
    void testDistributionForRegularRateWithUpdates() {
        // given
        final DummyClock clock = new DummyClock();
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);
        final SpeedometerMetric metric = new PlatformSpeedometerMetric(config, clock);

        // when
        for (int i = 1; i <= 1000; i++) {
            clock.setSeconds(i);
            metric.update(1000);
        }
        clock.setSeconds(1000);
        double rate = metric.get();

        // then
        assertEquals(1000.0, rate, 0.001, "Rate should be 1000.0");
        assertEquals(1000.0, metric.get(VALUE), 0.001, "Mean rate should be 1000.0");
        assertEquals(1000.0, metric.get(MIN), 0.1, "Min. rate should be about 1000.0");
        assertEquals(1000.0, metric.get(MAX), 0.1, "Max. rate should be about 1000.0");
        assertEquals(0.0, metric.get(STD_DEV), EPSILON, "Standard deviation should be 0.0");
    }

    @Test
    @Tag(TIME_CONSUMING)
    void testDistributionForIncreasedRate() {
        // given
        final DummyClock clock = new DummyClock();
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);
        final SpeedometerMetric metric = new PlatformSpeedometerMetric(config, clock);

        // when
        sendCycles(metric, clock, 0, 1000, 1000);
        sendCycles(metric, clock, 1000, 1000 + (int) SettingsCommon.halfLife, 2000);
        clock.setSeconds(1000 + (int) SettingsCommon.halfLife);
        double rate = metric.get();

        // then
        assertEquals(1500.0, rate, 0.001, "Rate should be 1500.0");
        assertEquals(1500.0, metric.get(VALUE), 0.001, "Mean rate should be 1500.0");
        assertEquals(1500.0, metric.get(MAX), 0.1, "Max. rate should be 1500.0");
    }

    @SuppressWarnings("removal")
    @Test
    void testDistributionForTwiceIncreasedRate() {
        // given
        final DummyClock clock = new DummyClock();
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);
        final SpeedometerMetric metric = new PlatformSpeedometerMetric(config, clock);

        // when
        sendCycles(metric, clock, 0, 1000, 1000);
        sendCycles(metric, clock, 1000, 1000 + (int) SettingsCommon.halfLife, 5000);
        sendCycles(
                metric,
                clock,
                1000 + (int) SettingsCommon.halfLife,
                1000 + 2 * (int) SettingsCommon.halfLife,
                7000);
        clock.setSeconds(1000 + 2 * (int) SettingsCommon.halfLife);
        double rate = metric.get();

        // then
        final StatsBuffered buffered = metric.getStatsBuffered();
        assertEquals(5000.0, rate, 0.001, "Rate should be 5000.0");
        assertEquals(5000.0, metric.get(VALUE), 0.001, "Mean rate should be 5000.0");
        assertEquals(5000.0, metric.get(MAX), 0.1, "Max. rate should be 5000.0");
    }

    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void testDistributionForDecreasedRate() {
        // given
        final DummyClock clock = new DummyClock();
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);
        final SpeedometerMetric metric = new PlatformSpeedometerMetric(config, clock);

        // when
        sendCycles(metric, clock, 0, 1000, 1000);
        sendCycles(metric, clock, 1000, 1000 + (int) SettingsCommon.halfLife, 500);
        clock.setSeconds(1000 + (int) SettingsCommon.halfLife);
        double rate = metric.get();

        // then
        assertEquals(750.0, rate, 0.001, "Rate should be 750.0");
        assertEquals(750.0, metric.get(VALUE), 0.001, "Mean rate should be 750.0");
        assertEquals(750.0, metric.get(MIN), 0.15, "Min. rate should be 750.0");
    }

    @Test
    @Tag(TIME_CONSUMING)
    void testDistributionForTwiceDecreasedRate() {
        // given
        final DummyClock clock = new DummyClock();
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);
        final SpeedometerMetric metric = new PlatformSpeedometerMetric(config, clock);

        // when
        sendCycles(metric, clock, 0, 1000, 7000);
        sendCycles(metric, clock, 1000, 1000 + (int) SettingsCommon.halfLife, 5000);
        sendCycles(
                metric,
                clock,
                1000 + (int) SettingsCommon.halfLife,
                1000 + 2 * (int) SettingsCommon.halfLife,
                2000);
        clock.setSeconds(1000 + 2 * (int) SettingsCommon.halfLife);
        double rate = metric.get();

        // then
        assertEquals(4000.0, rate, 0.001, "Rate should be 4000.0");
        assertEquals(4000.0, metric.get(VALUE), 0.001, "Mean rate should be 4000.0");
        assertEquals(4000.0, metric.get(MIN), 0.15, "Min. rate should be 4000.0");
    }

    @Test
    void testSnapshot() {
        // given
        final DummyClock clock = new DummyClock();
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);
        final PlatformSpeedometerMetric metric = new PlatformSpeedometerMetric(config, clock);

        // when
        sendCycles(metric, clock, 0, 1000, 1000);
        clock.setSeconds(1000);
        final double rate = metric.get();
        final List<SnapshotValue> snapshot = metric.takeSnapshot();

        // then
        assertEquals(1000.0, rate, 0.001, "Rate should be 1000.0");
        assertEquals(1000.0, metric.get(VALUE), 0.001, "Mean rate should be 1000.0");
        assertEquals(1000.0, metric.get(MIN), 0.1, "Min. rate should be about 1000.0");
        assertEquals(1000.0, metric.get(MAX), 0.1, "Max. rate should be about 1000.0");
        assertEquals(0.02, metric.get(STD_DEV), 0.001, "Standard deviation should be around 0.02");
        assertEquals(VALUE, snapshot.get(0).valueType());
        assertEquals(1000.0, (double) snapshot.get(0).value(), 0.001, "Mean rate should be 1000.0");
        assertEquals(MAX, snapshot.get(1).valueType());
        assertEquals(1000.0, (double) snapshot.get(1).value(), 0.1, "Mean rate should be 1000.0");
        assertEquals(MIN, snapshot.get(2).valueType());
        assertEquals(1000.0, (double) snapshot.get(2).value(), 0.1, "Mean rate should be 1000.0");
        assertEquals(STD_DEV, snapshot.get(3).valueType());
        assertEquals(0.02, (double) snapshot.get(3).value(), 0.001, "Mean rate should be 1000.0");
    }

    @Test
    void testInvalidGets() {
        // given
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);
        final SpeedometerMetric metric = new PlatformSpeedometerMetric(config);

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
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);
        final SpeedometerMetric metric1 = new PlatformSpeedometerMetric(config);
        final SpeedometerMetric metric2 = new PlatformSpeedometerMetric(config);
        metric2.cycle();

        // then
        assertThat(metric1)
                .isEqualTo(metric2)
                .hasSameHashCodeAs(metric2)
                .isNotEqualTo(
                        new PlatformSpeedometerMetric(new SpeedometerMetric.Config("Other", NAME)))
                .isNotEqualTo(
                        new PlatformSpeedometerMetric(
                                new SpeedometerMetric.Config(CATEGORY, "Other")))
                .isNotEqualTo(new PlatformIntegerGauge(new IntegerGauge.Config(CATEGORY, NAME)));
    }

    @Test
    void testToString() {
        // given
        final SpeedometerMetric.Config config =
                new SpeedometerMetric.Config(CATEGORY, NAME)
                        .withDescription(DESCRIPTION)
                        .withUnit(UNIT)
                        .withFormat(FORMAT)
                        .withHalfLife(Math.PI);
        final SpeedometerMetric metric = new PlatformSpeedometerMetric(config);

        // then
        assertThat(metric.toString())
                .contains(CATEGORY, NAME, DESCRIPTION, UNIT, FORMAT, FLOAT.toString(), "3.1415");
    }

    private static void sendCycles(
            final SpeedometerMetric metric,
            final DummyClock clock,
            final int start,
            final int stop,
            final int rate) {
        for (int i = start; i < stop; i++) {
            for (int j = 1; j <= rate; j++) {
                clock.set(Duration.ofSeconds(i).plus(Duration.ofSeconds(j).dividedBy(rate + 1)));
                metric.cycle();
            }
        }
    }
}
