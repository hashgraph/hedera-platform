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

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.statistics.StatsBuffered;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.swirlds.common.metrics.Metric.ValueType.COUNTER;
import static com.swirlds.common.metrics.Metric.ValueType.MAX;
import static com.swirlds.common.metrics.Metric.ValueType.MIN;
import static com.swirlds.common.metrics.Metric.ValueType.STD_DEV;
import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedometerMetricTest {

	private static final String CATEGORY = "CaTeGoRy";
	private static final String NAME = "NaMe";
	private static final String DESCRIPTION = "DeScRiPtIoN";
	private static final String FORMAT = "FoRmAt";

	private static final double EPSILON = 1e-6;

	@Test
	void testConstructor() {
		// when
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT);

		// then
		assertEquals(CATEGORY, metric.getCategory(), "The category was not set correctly");
		assertEquals(NAME, metric.getName(), "The name was not set correctly");
		assertEquals(DESCRIPTION, metric.getDescription(), "The description was not set correctly");
		assertEquals(FORMAT, metric.getFormat(), "The format was not set correctly");
		assertEquals(0.0, metric.get(), EPSILON, "The value was not initialized correctly");
		assertEquals(0.0, metric.get(VALUE), EPSILON, "The value was not initialized correctly");
		assertEquals(0.0, metric.get(MIN), EPSILON, "The minimum was not initialized correctly");
		assertEquals(0.0, metric.get(MAX), EPSILON, "The maximum was not initialized correctly");
		assertEquals(0.0, metric.get(STD_DEV), EPSILON, "The standard deviation was not initialized correctly");
		assertNotNull(metric.getStatsBuffered(), "StatsBuffered was not initialized correctly");
		assertTrue(metric.getValueTypes().contains(VALUE), "Metric needs to support VALUE");
		assertTrue(metric.getValueTypes().contains(MIN), "Metric needs to support MIN");
		assertTrue(metric.getValueTypes().contains(MAX), "Metric needs to support MAX");
		assertTrue(metric.getValueTypes().contains(STD_DEV), "Metric needs to support STD_DEV");
		assertFalse(metric.getValueTypes().contains(COUNTER), "Metric must not support COUNTER");
	}

	@Test
	void testInvalidConstructor() {
		// when
		assertThrows(IllegalArgumentException.class, () -> new SpeedometerMetric(null, NAME, DESCRIPTION, FORMAT),
				"Calling the constructor without a category should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new SpeedometerMetric(CATEGORY, null, DESCRIPTION, FORMAT),
				"Calling the constructor without a name should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new SpeedometerMetric(CATEGORY, NAME, null, FORMAT),
				"Calling the constructor without a description should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, null),
				"Calling the constructor without a format should throw an IAE");
	}

	@Test
	void testConstructorWithHalfLife() {
		// when
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, Math.PI);

		// then
		assertEquals(CATEGORY, metric.getCategory(), "The category was not set correctly");
		assertEquals(NAME, metric.getName(), "The name was not set correctly");
		assertEquals(DESCRIPTION, metric.getDescription(), "The description was not set correctly");
		assertEquals(FORMAT, metric.getFormat(), "The format was not set correctly");
		assertEquals(0.0, metric.get(), EPSILON, "The value was not initialized correctly");
		assertEquals(0.0, metric.get(VALUE), EPSILON, "The value was not initialized correctly");
		assertEquals(0.0, metric.get(MIN), EPSILON, "The minimum was not initialized correctly");
		assertEquals(0.0, metric.get(MAX), EPSILON, "The maximum was not initialized correctly");
		assertEquals(0.0, metric.get(STD_DEV), EPSILON, "The standard deviation was not initialized correctly");
		assertNotNull(metric.getStatsBuffered(), "StatsBuffered was not initialized correctly");
		assertTrue(metric.getValueTypes().contains(VALUE), "Metric needs to support VALUE");
		assertTrue(metric.getValueTypes().contains(MIN), "Metric needs to support MIN");
		assertTrue(metric.getValueTypes().contains(MAX), "Metric needs to support MAX");
		assertTrue(metric.getValueTypes().contains(STD_DEV), "Metric needs to support STD_DEV");
		assertFalse(metric.getValueTypes().contains(COUNTER), "Metric must not support COUNTER");
	}

	@Test
	void testInvalidConstructorWithHalfLife() {
		// when
		assertThrows(IllegalArgumentException.class, () -> new SpeedometerMetric(null, NAME, DESCRIPTION, FORMAT, Math.PI),
				"Calling the constructor without a category should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new SpeedometerMetric(CATEGORY, null, DESCRIPTION, FORMAT, Math.PI),
				"Calling the constructor without a name should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new SpeedometerMetric(CATEGORY, NAME, null, FORMAT, Math.PI),
				"Calling the constructor without a description should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, null, Math.PI),
				"Calling the constructor without a format should throw an IAE");
	}

	@Test
	void testInit() {
		// given
		final DummyClock clock = new DummyClock();
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);
		sendCycles(metric, clock, 0, 1000, 1000);
		clock.setSeconds(1000);
		assertEquals(1000.0, metric.get(), 0.001, "Rate should be 1000.0");

		// when
		metric.init();
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
	void testReset() {
		// given
		final DummyClock clock = new DummyClock();
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);
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
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

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
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

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
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

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
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		// when
		sendCycles(metric, clock, 0, 1000, 1000);
		clock.setSeconds(1000);
		double rate = metric.get();

		// then
		assertEquals(1000.0, rate, 0.001, "Rate should be 1000.0");
		assertEquals(1000.0, metric.get(VALUE), 0.001, "Mean rate should be 1000.0");
		assertEquals(1000.0, metric.get(MIN), 0.1, "Min. rate should be about 1000.0");
		assertEquals(1000.0, metric.get(MAX), 0.1, "Max. rate should be about 1000.0");
		assertEquals(0.02, metric.get(STD_DEV), 0.001,"Standard deviation should be around 0.02");
	}

	@Disabled("Fails currently. Should be enabled with new speedometer implementation")
	@Test
	void testDistributionForRegularRateWithUpdates() {
		// given
		final DummyClock clock = new DummyClock();
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

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
		assertEquals(0.0, metric.get(STD_DEV), EPSILON,"Standard deviation should be 0.0");
	}

	@Test
	void testDistributionForIncreasedRate() {
		// given
		final DummyClock clock = new DummyClock();
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		// when
		sendCycles(metric, clock, 0, 1000, 1000);
		sendCycles(metric, clock, 1000, 1000 + (int)SettingsCommon.halfLife, 2000);
		clock.setSeconds(1000 + (int)SettingsCommon.halfLife);
		double rate = metric.get();

		// then
		assertEquals(1500.0, rate, 0.001, "Rate should be 1500.0");
		assertEquals(1500.0, metric.get(VALUE), 0.001, "Mean rate should be 1500.0");
		assertEquals(1500.0, metric.get(MAX), 0.1, "Max. rate should be 1500.0");
	}

	@Test
	void testDistributionForTwiceIncreasedRate() {
		// given
		final DummyClock clock = new DummyClock();
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		// when
		sendCycles(metric, clock, 0, 1000, 1000);
		sendCycles(metric, clock, 1000, 1000 + (int)SettingsCommon.halfLife, 5000);
		sendCycles(metric, clock, 1000 + (int)SettingsCommon.halfLife, 1000 + 2 * (int)SettingsCommon.halfLife, 7000);
		clock.setSeconds(1000 + 2 * (int)SettingsCommon.halfLife);
		double rate = metric.get();

		// then
		final StatsBuffered buffered = metric.getStatsBuffered();
		assertEquals(5000.0, rate, 0.001, "Rate should be 5000.0");
		assertEquals(5000.0, metric.get(VALUE), 0.001, "Mean rate should be 5000.0");
		assertEquals(5000.0, metric.get(MAX), 0.1, "Max. rate should be 5000.0");
	}

	@Test
	void testDistributionForDecreasedRate() {
		// given
		final DummyClock clock = new DummyClock();
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		// when
		sendCycles(metric, clock, 0, 1000, 1000);
		sendCycles(metric, clock, 1000, 1000 + (int)SettingsCommon.halfLife, 500);
		clock.setSeconds(1000 + (int)SettingsCommon.halfLife);
		double rate = metric.get();

		// then
		assertEquals(750.0, rate, 0.001, "Rate should be 750.0");
		assertEquals(750.0, metric.get(VALUE), 0.001, "Mean rate should be 750.0");
		assertEquals(750.0, metric.get(MIN), 0.15, "Min. rate should be 750.0");
	}

	@Test
	void testDistributionForTwiceDecreasedRate() {
		// given
		final DummyClock clock = new DummyClock();
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		// when
		sendCycles(metric, clock, 0, 1000, 7000);
		sendCycles(metric, clock, 1000, 1000 + (int)SettingsCommon.halfLife, 5000);
		sendCycles(metric, clock, 1000 + (int)SettingsCommon.halfLife, 1000 + 2 * (int)SettingsCommon.halfLife, 2000);
		clock.setSeconds(1000 + 2 * (int)SettingsCommon.halfLife);
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
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		// when
		sendCycles(metric, clock, 0, 1000, 1000);
		clock.setSeconds(1000);
		final double rate = metric.get();
		final List<Pair<Metric.ValueType, Object>> snapshot = metric.takeSnapshot();

		// then
		assertEquals(1000.0, rate, 0.001, "Rate should be 1000.0");
		assertEquals(1000.0, metric.get(VALUE), 0.001, "Mean rate should be 1000.0");
		assertEquals(1000.0, metric.get(MIN), 0.1, "Min. rate should be about 1000.0");
		assertEquals(1000.0, metric.get(MAX), 0.1, "Max. rate should be about 1000.0");
		assertEquals(0.02, metric.get(STD_DEV), 0.001,"Standard deviation should be around 0.02");
		assertEquals(VALUE, snapshot.get(0).getLeft());
		assertEquals(1000.0, (double) snapshot.get(0).getRight(), 0.001, "Mean rate should be 1000.0");
		assertEquals(MAX, snapshot.get(1).getLeft());
		assertEquals(1000.0, (double) snapshot.get(1).getRight(), 0.1, "Mean rate should be 1000.0");
		assertEquals(MIN, snapshot.get(2).getLeft());
		assertEquals(1000.0, (double) snapshot.get(2).getRight(), 0.1, "Mean rate should be 1000.0");
		assertEquals(STD_DEV, snapshot.get(3).getLeft());
		assertEquals(0.02, (double) snapshot.get(3).getRight(), 0.001, "Mean rate should be 1000.0");
	}

	@Test
	void testInvalidGets() {
		// given
		final DummyClock clock = new DummyClock();
		final SpeedometerMetric metric = new SpeedometerMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		// then
		assertThrows(IllegalArgumentException.class, () -> metric.get(null),
				"Calling get() with null should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> metric.get(Metric.ValueType.COUNTER),
				"Calling get() with an unsupported MetricType should throw an IAE");
	}

	private static void sendCycles(final SpeedometerMetric metric, final DummyClock clock, final int start, final int stop, final int rate) {
		for (int i = start; i < stop; i++) {
			for (int j = 1; j <= rate; j++) {
				clock.set(Duration.ofSeconds(i).plus(Duration.ofSeconds(j).dividedBy(rate + 1)));
				metric.cycle();
			}
		}
	}

}
