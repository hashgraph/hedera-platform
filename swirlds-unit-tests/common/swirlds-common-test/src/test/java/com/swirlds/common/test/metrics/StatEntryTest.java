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
import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.StatsBuffered;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

	@SuppressWarnings("unchecked")
	@Test
	void testConstructor() {
		// given
		final Supplier<Object> getter = mock(Supplier.class);

		// when
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, null, null, null, getter);

		// then
		assertEquals(CATEGORY, statEntry.getCategory(), "The category was not set correctly in the constructor");
		assertEquals(NAME, statEntry.getName(), "The name was not set correctly in the constructor");
		assertEquals(DESCRIPTION, statEntry.getDescription(), "The description was not set correctly in the constructor");
		assertEquals(FORMAT, statEntry.getFormat(), "The format was not set correctly in the constructor");
		assertNull(statEntry.getStatsBuffered(), "StatsBuffered was not initialized correctly in the constructor");
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
		final Supplier<Object> getter = mock(Supplier.class);
		final Supplier<Object> getAndReset = mock(Supplier.class);

		// when
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, null, null, null, getter, getAndReset);

		// then
		assertEquals(CATEGORY, statEntry.getCategory(), "The category was not set correctly in the constructor");
		assertEquals(NAME, statEntry.getName(), "The name was not set correctly in the constructor");
		assertEquals(DESCRIPTION, statEntry.getDescription(), "The description was not set correctly in the constructor");
		assertEquals(FORMAT, statEntry.getFormat(), "The format was not set correctly in the constructor");
		assertNull(statEntry.getStatsBuffered(), "StatsBuffered was not initialized correctly in the constructor");
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
	void testGetValue() {
		// given
		final Supplier<Object> getter = mock(Supplier.class);
		final Supplier<Object> getAndReset = mock(Supplier.class);
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, null, null, null, getter, getAndReset);

		final Object expected = new Object();
		when(getter.get()).thenReturn(expected);

		// when
		final Object actual = statEntry.getValue();

		// then
		verify(getter).get();
		verify(getAndReset, never()).get();
		assertEquals(expected, actual, "The value that was read was not the one expected");
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetAndResetValue() {
		// given
		final Supplier<Object> getter = mock(Supplier.class);
		final Supplier<Object> getAndReset = mock(Supplier.class);
		final StatEntry statEntry = new StatEntry(CATEGORY, NAME, DESCRIPTION, FORMAT, null, null, null, getter, getAndReset);

		final Object expected = new Object();
		when(getAndReset.get()).thenReturn(expected);

		// when
		final Object actual = statEntry.getValueAndReset();

		// then
		verify(getter, never()).get();
		verify(getAndReset).get();
		assertEquals(expected, actual, "The value that was read was not the one expected");
	}
}
