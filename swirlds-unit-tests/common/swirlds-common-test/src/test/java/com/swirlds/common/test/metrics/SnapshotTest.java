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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.platform.PlatformMetric;
import com.swirlds.common.metrics.platform.Snapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotTest {

    @Test
    void testToString() {
        // given
        final PlatformMetric metric = mock(PlatformMetric.class);
        when(metric.takeSnapshot())
                .thenReturn(List.of(new Snapshot.SnapshotValue(Metric.ValueType.VALUE, 42L)));
        final Snapshot snapshot = Snapshot.of(metric);

        // when
        final String result = snapshot.toString();

        // then
        assertTrue(result.contains("valueType=VALUE"));
        assertTrue(result.contains("value=42"));
    }
}
