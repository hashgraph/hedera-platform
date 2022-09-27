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

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metric;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FunctionGaugeTest {


	private static final String CATEGORY = "CaTeGoRy";
	private static final String NAME = "NaMe";
	private static final String DESCRIPTION = "DeScRiPtIoN";
	private static final String FORMAT = "FoRmAt";
	private static final Supplier<String> SUPPLIER = () -> "Hello World";


	@Test
	void testConstructor() {
		final FunctionGauge<String> gauge = new FunctionGauge<>(CATEGORY, NAME, DESCRIPTION, FORMAT, SUPPLIER);

		assertEquals(CATEGORY, gauge.getCategory(), "The category was not set correctly in the constructor");
		assertEquals(NAME, gauge.getName(), "The name was not set correctly in the constructor");
		assertEquals(DESCRIPTION, gauge.getDescription(), "The description was not set correctly in the constructor");
		assertEquals(FORMAT, gauge.getFormat(), "The format was not set correctly in the constructor");
		assertEquals("Hello World", gauge.get());
		assertEquals("Hello World", gauge.get(VALUE));
		assertEquals(List.of(VALUE), gauge.getValueTypes(), "ValueTypes should be [VALUE]");
	}

	@Test
	void testConstructorWithNullParameter() {
		assertThrows(IllegalArgumentException.class, () -> new FunctionGauge<>(null, NAME, DESCRIPTION, FORMAT, SUPPLIER),
				"Calling the constructor without a category should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new FunctionGauge<>(CATEGORY, null, DESCRIPTION, FORMAT, SUPPLIER),
				"Calling the constructor without a name should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new FunctionGauge<>(CATEGORY, NAME, null, FORMAT, SUPPLIER),
				"Calling the constructor without a description should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new FunctionGauge<>(CATEGORY, NAME, DESCRIPTION, null, SUPPLIER),
				"Calling the constructor without a format should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new FunctionGauge<String>(CATEGORY, NAME, DESCRIPTION, FORMAT, null),
				"Calling the constructor without a supplier should throw an IAE");
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetAndSet() {
		// given
		final Supplier<String> supplier = mock(Supplier.class);
		final FunctionGauge<String> gauge = new FunctionGauge<>(CATEGORY, NAME, DESCRIPTION, FORMAT, supplier);

		// when
		when(supplier.get()).thenReturn("Hello World");

		// then
		assertEquals("Hello World", gauge.get(), "Value should be 'Hello World'");
		assertEquals("Hello World", gauge.get(VALUE), "Value should be 'Hello World'");

		// when
		when(supplier.get()).thenReturn("Goodbye World");

		// then
		assertEquals("Goodbye World", gauge.get(), "Value should be 'Goodbye World'");
		assertEquals("Goodbye World", gauge.get(VALUE), "Value should be 'Goodbye World'");
	}

	@SuppressWarnings("unchecked")
	@Test
	void testSnapshot() {
		// given
		final Supplier<String> supplier = mock(Supplier.class);
		final FunctionGauge<String> gauge = new FunctionGauge<>(CATEGORY, NAME, DESCRIPTION, FORMAT, supplier);

		// when
		when(supplier.get()).thenReturn("Hello World");
		final List<Pair<Metric.ValueType, Object>> snapshot = gauge.takeSnapshot();

		// then
		assertEquals("Hello World", gauge.get(), "Value should be 'Hello World'");
		assertEquals("Hello World", gauge.get(VALUE), "Value should be 'Hello World'");
		assertEquals(List.of(Pair.of(VALUE, "Hello World")), snapshot, "Snapshot is not correct");
	}

	@SuppressWarnings("unchecked")
	@Test
	void testInvalidGets() {
		// given
		final Supplier<String> supplier = mock(Supplier.class);
		final FunctionGauge<String> gauge = new FunctionGauge<>(CATEGORY, NAME, DESCRIPTION, FORMAT, supplier);

		// then
		assertThrows(IllegalArgumentException.class, () -> gauge.get(null),
				"Calling get() with null should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> gauge.get(Metric.ValueType.COUNTER),
				"Calling get() with an unsupported MetricType should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> gauge.get(Metric.ValueType.MIN),
				"Calling get() with an unsupported MetricType should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> gauge.get(Metric.ValueType.MAX),
				"Calling get() with an unsupported MetricType should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> gauge.get(Metric.ValueType.STD_DEV),
				"Calling get() with an unsupported MetricType should throw an IAE");
	}
}
