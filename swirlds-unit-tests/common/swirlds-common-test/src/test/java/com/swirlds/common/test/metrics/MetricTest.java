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

import com.swirlds.common.metrics.Metric;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricTest {

	private static final String CATEGORY = "CaTeGoRy";
	private static final String NAME = "NaMe";
	private static final String DESCRIPTION = "DeScRiPtIoN";
	private static final String FORMAT = "FoRmAt";


	@Test
	void testConstructor() {
		final Metric metric = new DummyMetric(CATEGORY, NAME, DESCRIPTION, FORMAT);

		assertEquals(CATEGORY, metric.getCategory(), "The category was not set correctly in the constructor");
		assertEquals(NAME, metric.getName(), "The name was not set correctly in the constructor");
		assertEquals(DESCRIPTION, metric.getDescription(), "The description was not set correctly in the constructor");
		assertEquals(FORMAT, metric.getFormat(), "The format was not set correctly in the constructor");
	}

	@Test
	void testConstructorWithNullParameter() {
		assertThrows(IllegalArgumentException.class, () -> new DummyMetric(null, NAME, DESCRIPTION, FORMAT),
				"Calling the constructor without a category should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new DummyMetric(CATEGORY, null, DESCRIPTION, FORMAT),
				"Calling the constructor without a name should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new DummyMetric(CATEGORY, NAME, null, FORMAT),
				"Calling the constructor without a description should throw an IAE");
		assertThrows(IllegalArgumentException.class, () -> new DummyMetric(CATEGORY, NAME, DESCRIPTION, null),
				"Calling the constructor without a format should throw an IAE");
	}

	private static class DummyMetric extends Metric {

		protected DummyMetric(String category, String name, String description, String format) {
			super(category, name, description, format);
		}

		@SuppressWarnings("removal")
		@Override
		public Object getValue() {
			return null;
		}
	}
}
