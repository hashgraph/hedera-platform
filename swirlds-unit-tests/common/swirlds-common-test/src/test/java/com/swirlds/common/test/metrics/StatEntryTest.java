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
import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.StatsBuffered;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.swirlds.common.metrics.Metric.ValueType.COUNTER;
import static com.swirlds.common.metrics.Metric.ValueType.MAX;
import static com.swirlds.common.metrics.Metric.ValueType.MIN;
import static com.swirlds.common.metrics.Metric.ValueType.STD_DEV;
import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatEntryTest {

	private static final String CATEGORY = "CaTeGoRy";
	private static final String NAME = "NaMe";
	private static final String DESCRIPTION = "DeScRiPtIoN";
	private static final String FORMAT = "FoRmAt";
	private static final double EPSILON = 1e-6;

	@SuppressWarnings("unchecked")
	@Test
	void testConstructor() {
		// given
		final StatsBuffered buffered = mock(StatsBuffered.class);
		final Function<Double, StatsBuffered> init = mock(Function.class);
		final Consumer<Double> reset = mock(Consumer.class);
		final Supplier<Object> getter = mock(Supplier.class);

		// when
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, buffered, init, reset, getter);

		// then
		assertEquals(CATEGORY, statEntry.getCategory(), "The category was not set correctly in the constructor");
		assertEquals(NAME, statEntry.getName(), "The name was not set correctly in the constructor");
		assertEquals(DESCRIPTION, statEntry.getDescription(), "The description was not set correctly in the constructor");
		assertEquals(FORMAT, statEntry.getFormat(), "The format was not set correctly in the constructor");
		assertEquals(buffered, statEntry.getBuffered(), "StatsBuffered was not set correctly in the constructor");
		assertEquals(init, statEntry.getInit(), "The init-lambda was not set correctly in the constructor");
		assertEquals(reset, statEntry.getReset(), "The reset-lambda was not set correctly in the constructor");
		assertEquals(getter, statEntry.getStatsStringSupplier(), "The get-lambda was not set correctly in the constructor");
		assertEquals(getter, statEntry.getResetStatsStringSupplier(), "The get-and-reset-lambda was not set correctly in the constructor");
		assertEquals(buffered, statEntry.getStatsBuffered(), "StatsBuffered was not set correctly in the constructor");
		assertTrue(statEntry.getValueTypes().contains(VALUE), "Metric needs to support VALUE");
		assertTrue(statEntry.getValueTypes().contains(MIN), "Metric needs to support MIN");
		assertTrue(statEntry.getValueTypes().contains(MAX), "Metric needs to support MAX");
		assertTrue(statEntry.getValueTypes().contains(STD_DEV), "Metric needs to support STD_DEV");
		assertFalse(statEntry.getValueTypes().contains(COUNTER), "Metric must not support COUNTER");
	}

	@Test
	void testConstructorWithNulls() {
		// given
		final Supplier<Object> getter = mock(Supplier.class);

		// when
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, null, null, null, getter);

		// then
		assertEquals(CATEGORY, statEntry.getCategory(), "The category was not set correctly in the constructor");
		assertEquals(NAME, statEntry.getName(), "The name was not set correctly in the constructor");
		assertEquals(DESCRIPTION, statEntry.getDescription(), "The description was not set correctly in the constructor");
		assertEquals(FORMAT, statEntry.getFormat(), "The format was not set correctly in the constructor");
		assertNull(statEntry.getBuffered(), "StatsBuffered was not initialized correctly in the constructor");
		assertNull(statEntry.getInit(), "The init-lambda was not initialized correctly in the constructor");
		assertNull(statEntry.getReset(), "The reset-lambda was not initialized correctly in the constructor");
		assertEquals(getter, statEntry.getStatsStringSupplier(), "The get-lambda was not set correctly in the constructor");
		assertEquals(getter, statEntry.getResetStatsStringSupplier(), "The get-and-reset-lambda was not set correctly in the constructor");
		assertNull(statEntry.getStatsBuffered(), "StatsBuffered was not initialized correctly in the constructor");
		assertEquals(List.of(VALUE), statEntry.getValueTypes(), "ValueTypes should be [VALUE]");
	}

	@SuppressWarnings("unchecked")
	@Test
	void testInvalidConstructor() {
		// given
		final Supplier<Object> getter = mock(Supplier.class);

		// when
		assertThrows(IllegalArgumentException.class, () -> new StatEntry(null, NAME, DESCRIPTION, FORMAT, null, null, null, getter),
				"Calling the constructor without a category should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new StatEntry(CATEGORY, null, DESCRIPTION, FORMAT, null, null, null, getter),
				"Calling the constructor without a name should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new StatEntry(CATEGORY, NAME, null, FORMAT, null, null, null, getter),
				"Calling the constructor without a description should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new StatEntry(CATEGORY, NAME, DESCRIPTION, null, null, null, null, getter),
				"Calling the constructor without a format should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, null, null, null, null),
				"Calling the constructor without a statsStringSupplier should throw an IAE");
	}

	@SuppressWarnings("unchecked")
	@Test
	void testConstructorWithReset() {
		// given
		final StatsBuffered buffered = mock(StatsBuffered.class);
		final Function<Double, StatsBuffered> init = mock(Function.class);
		final Consumer<Double> reset = mock(Consumer.class);
		final Supplier<Object> getter = mock(Supplier.class);
		final Supplier<Object> getAndReset = mock(Supplier.class);

		// when
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, buffered, init, reset, getter, getAndReset);

		// then
		assertEquals(CATEGORY, statEntry.getCategory(), "The category was not set correctly in the constructor");
		assertEquals(NAME, statEntry.getName(), "The name was not set correctly in the constructor");
		assertEquals(DESCRIPTION, statEntry.getDescription(), "The description was not set correctly in the constructor");
		assertEquals(FORMAT, statEntry.getFormat(), "The format was not set correctly in the constructor");
		assertEquals(buffered, statEntry.getBuffered(), "StatsBuffered was not set correctly in the constructor");
		assertEquals(init, statEntry.getInit(), "The init-lambda was not set correctly in the constructor");
		assertEquals(reset, statEntry.getReset(), "The reset-lambda was not set correctly in the constructor");
		assertEquals(getter, statEntry.getStatsStringSupplier(), "The get-lambda was not set correctly in the constructor");
		assertEquals(getAndReset, statEntry.getResetStatsStringSupplier(), "The get-and-reset-lambda was not set correctly in the constructor");
		assertEquals(buffered, statEntry.getStatsBuffered(), "StatsBuffered was not set correctly in the constructor");
		assertTrue(statEntry.getValueTypes().contains(VALUE), "Metric needs to support VALUE");
		assertTrue(statEntry.getValueTypes().contains(MIN), "Metric needs to support MIN");
		assertTrue(statEntry.getValueTypes().contains(MAX), "Metric needs to support MAX");
		assertTrue(statEntry.getValueTypes().contains(STD_DEV), "Metric needs to support STD_DEV");
		assertFalse(statEntry.getValueTypes().contains(COUNTER), "Metric must not support COUNTER");
	}

	@Test
	void testConstructorWithResetAndNulls() {
		// given
		final Supplier<Object> getter = mock(Supplier.class);
		final Supplier<Object> getAndReset = mock(Supplier.class);

		// when
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, null, null, null, getter, getAndReset);

		// then
		assertEquals(CATEGORY, statEntry.getCategory(), "The category was not set correctly in the constructor");
		assertEquals(NAME, statEntry.getName(), "The name was not set correctly in the constructor");
		assertEquals(DESCRIPTION, statEntry.getDescription(), "The description was not set correctly in the constructor");
		assertEquals(FORMAT, statEntry.getFormat(), "The format was not set correctly in the constructor");
		assertNull(statEntry.getBuffered(), "StatsBuffered was not initialized correctly in the constructor");
		assertNull(statEntry.getInit(), "The init-lambda was not initialized correctly in the constructor");
		assertNull(statEntry.getReset(), "The reset-lambda was not initialized correctly in the constructor");
		assertEquals(getter, statEntry.getStatsStringSupplier(), "The get-lambda was not set correctly in the constructor");
		assertEquals(getAndReset, statEntry.getResetStatsStringSupplier(), "The get-and-reset-lambda was not set correctly in the constructor");
		assertNull(statEntry.getStatsBuffered(), "StatsBuffered was not initialized correctly in the constructor");
		assertEquals(List.of(VALUE), statEntry.getValueTypes(), "ValueTypes should be [VALUE]");
	}

	@SuppressWarnings("unchecked")
	@Test
	void testInvalidConstructorWithReset() {
		// given
		final Supplier<Object> getter = mock(Supplier.class);
		final Supplier<Object> getAndReset = mock(Supplier.class);

		// when-then
		assertThrows(IllegalArgumentException.class, () -> new StatEntry(null, NAME, DESCRIPTION, FORMAT, null, null, null, getter, getAndReset),
				"Calling the constructor without a category should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new StatEntry(CATEGORY, null, DESCRIPTION, FORMAT, null, null, null, getter, getAndReset),
				"Calling the constructor without a name should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new StatEntry(CATEGORY, NAME, null, FORMAT, null, null, null, getter, getAndReset),
				"Calling the constructor without a description should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new StatEntry(CATEGORY, NAME, DESCRIPTION, null, null, null, null, getter, getAndReset),
				"Calling the constructor without a format should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, null, null, null, null, getAndReset),
				"Calling the constructor without a statsStringSupplier should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, null, null, null, getter, null),
				"Calling the constructor without a resetStatsStringSupplier should throw an IAE");
	}

	@SuppressWarnings("unchecked")
	@Test
	void testInit() {
		// given
		final Function<Double, StatsBuffered> init = mock(Function.class);
		final Supplier<Object> getter = mock(Supplier.class);
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, null, init, null, getter);

		final StatsBuffered newBuffered = mock(StatsBuffered.class);
		when(init.apply(anyDouble())).thenReturn(newBuffered);

		// when
		statEntry.init();

		// then
		verify(init).apply(SettingsCommon.halfLife);
		assertEquals(newBuffered, statEntry.getStatsBuffered(), "The returned object is not the one that was provided.");
	}

	@SuppressWarnings("unchecked")
	@Test
	void testInitWithoutInitLambda() {
		// given
		final Supplier<Object> getter = mock(Supplier.class);
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, null, null, null, getter);

		// when
		statEntry.init();

		// then
		assertNull(statEntry.getStatsBuffered(), "StatsBuffered should be null, but is not.");
	}

	@SuppressWarnings("unchecked")
	@Test
	void testReset() {
		// given
		final StatsBuffered buffered = mock(StatsBuffered.class);
		final Consumer<Double> reset = mock(Consumer.class);
		final Supplier<Object> getter = mock(Supplier.class);
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, buffered, null, reset, getter);

		// when
		statEntry.reset();

		// then
		verify(reset).accept(SettingsCommon.halfLife);
		verify(buffered, never()).reset(anyDouble());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testResetWithoutResetLambda() {
		// given
		final StatsBuffered buffered = mock(StatsBuffered.class);
		final Supplier<Object> getter = mock(Supplier.class);
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, buffered, null, null, getter);

		// when
		statEntry.reset();

		// then
		verify(buffered).reset(SettingsCommon.halfLife);
	}

	@SuppressWarnings("unchecked")
	@Test
	void testResetWithoutResetLambdaAndBuffer() {
		// given
		final Supplier<Object> getter = mock(Supplier.class);
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, null, null, null, getter);

		// when
		assertDoesNotThrow(statEntry::reset, "Calling reset() should have been a no-op, but was not.");
	}

	@SuppressWarnings("unchecked")
	@Test
	void testSnapshotWithBuffered() {
		// given
		final StatsBuffered buffered = mock(StatsBuffered.class);
		final Supplier<Object> getter = mock(Supplier.class);
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, buffered, null, null, getter);

		// when
		when(getter.get()).thenReturn(3 * Math.PI);
		when(buffered.getMin()).thenReturn(2 * Math.PI);
		when(buffered.getMean()).thenReturn(3 * Math.PI);
		when(buffered.getMax()).thenReturn(5 * Math.PI);
		when(buffered.getStdDev()).thenReturn(Math.PI);
		final List<Pair<Metric.ValueType, Object>> snapshot = statEntry.takeSnapshot();

		// then
		assertEquals(2 * Math.PI, statEntry.get(MIN));
		assertEquals(3 * Math.PI, statEntry.get(VALUE));
		assertEquals(5 * Math.PI, statEntry.get(MAX));
		assertEquals(Math.PI, statEntry.get(STD_DEV));
		assertEquals(VALUE, snapshot.get(0).getLeft());
		assertEquals(3 * Math.PI, (double) snapshot.get(0).getRight(), EPSILON, "Mean value should be " + (3 * Math.PI));
		assertEquals(MAX, snapshot.get(1).getLeft());
		assertEquals(5 * Math.PI, (double) snapshot.get(1).getRight(), EPSILON, "Max. value should be " + (5 * Math.PI));
		assertEquals(MIN, snapshot.get(2).getLeft());
		assertEquals(2 * Math.PI, (double) snapshot.get(2).getRight(), EPSILON, "Min. value should be" + (2 * Math.PI));
		assertEquals(STD_DEV, snapshot.get(3).getLeft());
		assertEquals(Math.PI, (double) snapshot.get(3).getRight(), EPSILON, "Standard deviation should be " + Math.PI);
	}

	@SuppressWarnings("unchecked")
	@Test
	void testSnapshotWithoutBuffered() {
		// given
		final Supplier<Object> getter = mock(Supplier.class);
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, null, null, null, getter);

		// when
		when(getter.get()).thenReturn("Hello World");
		final List<Pair<Metric.ValueType, Object>> snapshot = statEntry.takeSnapshot();

		// then
		assertEquals("Hello World", statEntry.get(VALUE), "Value should be \"Hello World\"");
		assertEquals(List.of(Pair.of(VALUE, "Hello World")), snapshot, "Snapshot is not correct");
	}

	@SuppressWarnings("unchecked")
	@Test
	void testInvalidGetsWithBuffered() {
		// given
		final StatsBuffered buffered = mock(StatsBuffered.class);
		final Supplier<Object> getter = mock(Supplier.class);
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, buffered, null, null, getter);

		// then
		assertThrows(IllegalArgumentException.class, () -> statEntry.get(null),
				"Calling get() with null should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> statEntry.get(Metric.ValueType.COUNTER),
				"Calling get() with an unsupported MetricType should throw an IAE");
	}


	@SuppressWarnings("unchecked")
	@Test
	void testInvalidGetsWithoutBuffered() {
		// given
		final Supplier<Object> getter = mock(Supplier.class);
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, null, null, null, getter);

		// then
		assertThrows(IllegalArgumentException.class, () -> statEntry.get(null),
				"Calling get() with null should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> statEntry.get(Metric.ValueType.COUNTER),
				"Calling get() with an unsupported MetricType should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> statEntry.get(Metric.ValueType.MIN),
				"Calling get() with an unsupported MetricType should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> statEntry.get(Metric.ValueType.MAX),
				"Calling get() with an unsupported MetricType should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> statEntry.get(Metric.ValueType.STD_DEV),
				"Calling get() with an unsupported MetricType should throw an IAE");
	}

}
