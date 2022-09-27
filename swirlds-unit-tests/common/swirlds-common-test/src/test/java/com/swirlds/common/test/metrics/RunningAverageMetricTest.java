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
import com.swirlds.common.metrics.RunningAverageMetric;
import org.apache.commons.lang3.tuple.Pair;
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

class RunningAverageMetricTest {

	private static final String CATEGORY = "CaTeGoRy";
	private static final String NAME = "NaMe";
	private static final String DESCRIPTION = "DeScRiPtIoN";
	private static final String FORMAT = "FoRmAt";

	private static final double EPSILON = 1e-6;

	@Test
	void testConstructor() {
		// when
		final RunningAverageMetric metric = new RunningAverageMetric(CATEGORY, NAME, DESCRIPTION, FORMAT);

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
		assertThrows(IllegalArgumentException.class, () -> new RunningAverageMetric(null, NAME, DESCRIPTION, FORMAT),
				"Calling the constructor without a category should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new RunningAverageMetric(CATEGORY, null, DESCRIPTION, FORMAT),
				"Calling the constructor without a name should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new RunningAverageMetric(CATEGORY, NAME, null, FORMAT),
				"Calling the constructor without a description should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new RunningAverageMetric(CATEGORY, NAME, DESCRIPTION, null),
				"Calling the constructor without a format should throw an IAE");
	}

	@Test
	void testConstructorWithHalfLife() {
		// when
		final RunningAverageMetric metric = new RunningAverageMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, Math.PI);

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
		assertThrows(IllegalArgumentException.class, () -> new RunningAverageMetric(null, NAME, DESCRIPTION, FORMAT, Math.PI),
				"Calling the constructor without a category should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new RunningAverageMetric(CATEGORY, null, DESCRIPTION, FORMAT, Math.PI),
				"Calling the constructor without a name should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new RunningAverageMetric(CATEGORY, NAME, null, FORMAT, Math.PI),
				"Calling the constructor without a description should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new RunningAverageMetric(CATEGORY, NAME, DESCRIPTION, null, Math.PI),
				"Calling the constructor without a format should throw an IAE");
	}

	@Test
	void testInit() {
		// given
		final DummyClock clock = new DummyClock();
		final RunningAverageMetric metric = new RunningAverageMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);
		recordValues(metric, clock, 0, 1000, Math.E);
		clock.setSeconds(1000);
		assertEquals(Math.E, metric.get(), EPSILON, "Mean should be " + Math.E);

		// when
		metric.init();
		clock.set(Duration.ofSeconds(1000, 1));

		// then
		assertEquals(0.0, metric.get(), EPSILON, "Mean should be reset to 0.0");

		// when
		recordValues(metric, clock, 1000, 2000, Math.PI);
		clock.setSeconds(2000);

		// then
		assertEquals(Math.PI, metric.get(), EPSILON, "Rate should be " + Math.PI);
	}

	@Test
	void testReset() {
		// given
		final DummyClock clock = new DummyClock();
		final RunningAverageMetric metric = new RunningAverageMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);
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
		metric.recordValue(Math.PI);

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
		final RunningAverageMetric metric = new RunningAverageMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		for (int i = 0; i < 1000; i++) {
			// when
			clock.set(Duration.ofSeconds(i).plusMillis(500));
			metric.recordValue(Math.PI);
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
		final RunningAverageMetric metric = new RunningAverageMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		// when
		recordValues(metric, clock, 0, 1000, Math.PI);
		clock.setSeconds(1000);
		double avg = metric.get();

		// then
		assertEquals(Math.PI, avg, EPSILON, "Value should be " + Math.PI);
		assertEquals(Math.PI, metric.get(VALUE), EPSILON, "Mean value should be " + Math.PI);
		assertEquals(Math.PI, metric.get(MIN), EPSILON, "Min. should be " + Math.PI);
		assertEquals(Math.PI, metric.get(MAX), EPSILON, "Max. should be " + Math.PI);
		assertEquals(0.0, metric.get(STD_DEV), EPSILON,"Standard deviation should be around 0.0");
	}

	@Test
	void testDistributionForIncreasedValue() {
		// given
		final DummyClock clock = new DummyClock();
		final RunningAverageMetric metric = new RunningAverageMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		// when
		recordValues(metric, clock, 0, 1000, Math.E);
		recordValues(metric, clock, 1000, 1000 + (int)SettingsCommon.halfLife, Math.PI);
		clock.setSeconds(1000 + (int)SettingsCommon.halfLife);
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
		final RunningAverageMetric metric = new RunningAverageMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		// when
		recordValues(metric, clock, 0, 1000, Math.E);
		recordValues(metric, clock, 1000, 1000 + (int)SettingsCommon.halfLife, Math.PI);
		recordValues(metric, clock, 1000 + (int)SettingsCommon.halfLife, 1000 + 2 * (int)SettingsCommon.halfLife, Math.PI + 0.5 * (Math.PI - Math.E));
		clock.setSeconds(1000 + 2 * (int)SettingsCommon.halfLife);
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
		final RunningAverageMetric metric = new RunningAverageMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		// when
		recordValues(metric, clock, 0, 1000, Math.PI);
		recordValues(metric, clock, 1000, 1000 + (int)SettingsCommon.halfLife, Math.E);
		clock.setSeconds(1000 + (int)SettingsCommon.halfLife);
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
		final RunningAverageMetric metric = new RunningAverageMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		// when
		recordValues(metric, clock, 0, 1000, Math.PI);
		recordValues(metric, clock, 1000, 1000 + (int)SettingsCommon.halfLife, Math.E);
		recordValues(metric, clock, 1000 + (int)SettingsCommon.halfLife, 1000 + 2 * (int)SettingsCommon.halfLife, Math.E - 0.5 * (Math.PI - Math.E));
		clock.setSeconds(1000 + 2 * (int)SettingsCommon.halfLife);
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
		final RunningAverageMetric metric = new RunningAverageMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		// when
		recordValues(metric, clock, 0, 1000, Math.PI);
		clock.setSeconds(1000);
		final double avg = metric.get();
		final List<Pair<Metric.ValueType, Object>> snapshot = metric.takeSnapshot();

		// then
		assertEquals(Math.PI, avg, EPSILON, "Value should be " + Math.PI);
		assertEquals(Math.PI, metric.get(VALUE), EPSILON, "Mean value should be " + Math.PI);
		assertEquals(Math.PI, metric.get(MIN), EPSILON, "Min. should be " + Math.PI);
		assertEquals(Math.PI, metric.get(MAX), EPSILON, "Max. should be " + Math.PI);
		assertEquals(0.0, metric.get(STD_DEV), EPSILON,"Standard deviation should be around 0.0");
		assertEquals(VALUE, snapshot.get(0).getLeft());
		assertEquals(Math.PI, (double) snapshot.get(0).getRight(), EPSILON, "Mean value should be " + Math.PI);
		assertEquals(MAX, snapshot.get(1).getLeft());
		assertEquals(Math.PI, (double) snapshot.get(1).getRight(), EPSILON, "Max. value should be " + Math.PI);
		assertEquals(MIN, snapshot.get(2).getLeft());
		assertEquals(Math.PI, (double) snapshot.get(2).getRight(), EPSILON, "Min. value should be " + Math.PI);
		assertEquals(STD_DEV, snapshot.get(3).getLeft());
		assertEquals(0.0, (double) snapshot.get(3).getRight(), EPSILON, "Standard deviation should be 0");
	}

	@Test
	void testInvalidGets() {
		// given
		final DummyClock clock = new DummyClock();
		final RunningAverageMetric metric = new RunningAverageMetric(CATEGORY, NAME, DESCRIPTION, FORMAT, SettingsCommon.halfLife, clock);

		// then
		assertThrows(IllegalArgumentException.class, () -> metric.get(null),
				"Calling get() with null should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> metric.get(Metric.ValueType.COUNTER),
				"Calling get() with an unsupported MetricType should throw an IAE");
	}

	private static void recordValues(final RunningAverageMetric metric, final DummyClock clock, final int start, final int stop, final double value) {
		for (int i = start; i < stop; i++) {
			clock.set(Duration.ofSeconds(i).plusMillis(500));
			metric.recordValue(value);
		}
	}
}
