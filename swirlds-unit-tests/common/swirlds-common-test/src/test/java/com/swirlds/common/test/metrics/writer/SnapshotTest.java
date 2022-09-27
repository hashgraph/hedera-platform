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

package com.swirlds.common.test.metrics.writer;

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.writer.Snapshot;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SnapshotTest {

	@Test
	void testToString() {
		// given
		final Metric metric = mock(Metric.class);
		when(metric.takeSnapshot()).thenReturn(List.of(Pair.of(Metric.ValueType.VALUE, 42L)));
		final Snapshot snapshot = Snapshot.of(metric);

		// when
		final String result = snapshot.toString();

		// then
		assertTrue(result.contains("valueType=VALUE"));
		assertTrue(result.contains("value=42"));
	}
}
