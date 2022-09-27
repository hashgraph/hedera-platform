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

import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.metrics.Metric;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DoubleGaugeTest {


	private static final String CATEGORY = "CaTeGoRy";
	private static final String NAME = "NaMe";
	private static final String DESCRIPTION = "DeScRiPtIoN";
	private static final String FORMAT = "FoRmAt";

	private static final double EPSILON = 1e-6;


	@Test
	@DisplayName("Constructor should store values")
	void testConstructor() {
		final DoubleGauge gauge = new DoubleGauge(CATEGORY, NAME, DESCRIPTION, FORMAT);

		assertEquals(CATEGORY, gauge.getCategory(), "The category was not set correctly in the constructor");
		assertEquals(NAME, gauge.getName(), "The name was not set correctly in the constructor");
		assertEquals(DESCRIPTION, gauge.getDescription(), "The description was not set correctly in the constructor");
		assertEquals(FORMAT, gauge.getFormat(), "The format was not set correctly in the constructor");
		assertEquals(0.0, gauge.get(), EPSILON, "The value was not initialized correctly");
		assertEquals(0.0, gauge.get(VALUE), EPSILON, "The value was not initialized correctly");
		assertEquals(List.of(VALUE), gauge.getValueTypes(), "ValueTypes should be [VALUE]");
	}

	@Test
	@DisplayName("Constructor should throw IAE when passing null")
	void testConstructorWithNullParameter() {
		assertThrows(IllegalArgumentException.class, () -> new DoubleGauge(null, NAME, DESCRIPTION, FORMAT),
				"Calling the constructor without a category should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new DoubleGauge(CATEGORY, null, DESCRIPTION, FORMAT),
				"Calling the constructor without a name should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new DoubleGauge(CATEGORY, NAME, null, FORMAT),
				"Calling the constructor without a description should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new DoubleGauge(CATEGORY, NAME, DESCRIPTION, null),
				"Calling the constructor without a format should throw an IAE");
	}

	@Test
	@DisplayName("Constructor with initial value should store values")
	void testInitialValueConstructor() {
		final DoubleGauge gauge = new DoubleGauge(CATEGORY, NAME, DESCRIPTION, FORMAT, Math.PI);

		assertEquals(CATEGORY, gauge.getCategory(), "The category was not set correctly in the constructor");
		assertEquals(NAME, gauge.getName(), "The name was not set correctly in the constructor");
		assertEquals(DESCRIPTION, gauge.getDescription(), "The description was not set correctly in the constructor");
		assertEquals(FORMAT, gauge.getFormat(), "The format was not set correctly in the constructor");
		assertEquals(Math.PI, gauge.get(), EPSILON, "The value was not initialized correctly");
		assertEquals(Math.PI, gauge.get(VALUE), EPSILON, "The value was not initialized correctly");
		assertEquals(List.of(VALUE), gauge.getValueTypes(), "ValueTypes should be [VALUE]");
	}

	@Test
	@DisplayName("Constructor with initial value should throw IAE when passing null")
	void testInitialValueConstructorWithNullParameter() {
		assertThrows(IllegalArgumentException.class, () -> new DoubleGauge(null, NAME, DESCRIPTION, FORMAT, Math.PI),
				"Calling the constructor without a category should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new DoubleGauge(CATEGORY, null, DESCRIPTION, FORMAT, Math.PI),
				"Calling the constructor without a name should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new DoubleGauge(CATEGORY, NAME, null, FORMAT, Math.PI),
				"Calling the constructor without a description should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new DoubleGauge(CATEGORY, NAME, DESCRIPTION, null, Math.PI),
				"Calling the constructor without a format should throw an IAE");
	}

	@Test
	void testGetAndSet() {
		// given
		final DoubleGauge gauge = new DoubleGauge(CATEGORY, NAME, DESCRIPTION, FORMAT, Math.PI);

		// when
		gauge.set(Math.E);

		// then
		assertEquals(Math.E, gauge.get(), EPSILON, "Value should be " + Math.E);
		assertEquals(Math.E, gauge.get(VALUE), EPSILON, "Value should be " + Math.E);

		// when
		gauge.set(Math.sqrt(2.0));

		// then
		assertEquals(Math.sqrt(2.0), gauge.get(), EPSILON, "Value should be " + Math.sqrt(2.0));
		assertEquals(Math.sqrt(2.0), gauge.get(VALUE), EPSILON, "Value should be " + Math.sqrt(2.0));
	}

	@Test
	void testSnapshot() {
		// given
		final DoubleGauge gauge = new DoubleGauge(CATEGORY, NAME, DESCRIPTION, FORMAT, Math.PI);

		// when
		final List<Pair<Metric.ValueType, Object>> snapshot = gauge.takeSnapshot();

		// then
		assertEquals(Math.PI, gauge.get(), EPSILON, "Value should be " + Math.PI);
		assertEquals(Math.PI, gauge.get(VALUE), EPSILON, "Value should be " + Math.PI);
		assertEquals(List.of(Pair.of(VALUE, Math.PI)), snapshot, "Snapshot is not correct");
	}

	@Test
	void testInvalidGets() {
		// given
		final DoubleGauge gauge = new DoubleGauge(CATEGORY, NAME, DESCRIPTION, FORMAT, Math.PI);

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
