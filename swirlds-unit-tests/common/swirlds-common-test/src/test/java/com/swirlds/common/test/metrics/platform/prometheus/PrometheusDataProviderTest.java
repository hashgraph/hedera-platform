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
package com.swirlds.common.test.metrics.platform.prometheus;

import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType.GLOBAL;
import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType.PLATFORM;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.DefaultCounter;
import com.swirlds.common.metrics.platform.Snapshot;
import com.swirlds.common.metrics.platform.prometheus.CounterAdapter;
import com.swirlds.common.metrics.platform.prometheus.PrometheusDataProvider;
import com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint;
import com.swirlds.common.system.NodeId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrometheusDataProviderTest {

    @Test
    void testConstructorWithNullParameter() {
        // given
        final PrometheusEndpoint endpoint = mock(PrometheusEndpoint.class);
        final NodeId nodeId = NodeId.createMain(1L);

        // then
        assertThatThrownBy(() -> new PrometheusDataProvider(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PrometheusDataProvider(null, nodeId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PrometheusDataProvider(endpoint, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInitGlobal() {
        // given
        final PrometheusEndpoint endPoint = mock(PrometheusEndpoint.class);
        final PrometheusDataProvider dataProvider = new PrometheusDataProvider(endPoint);
        final Metric metric = mock(Metric.class);

        // when
        dataProvider.init(List.of(metric));

        // then
        verify(endPoint).createAdapter(metric, GLOBAL);
    }

    @Test
    void testInitPlatform() {
        // given
        final PrometheusEndpoint endPoint = mock(PrometheusEndpoint.class);
        final PrometheusDataProvider dataProvider =
                new PrometheusDataProvider(endPoint, NodeId.createMain(1L));
        final Metric metric = mock(Metric.class);

        // when
        dataProvider.init(List.of(metric));

        // then
        verify(endPoint).createAdapter(metric, PLATFORM);
    }

    @Test
    void testFilteringOfTime() {
        // given
        final PrometheusEndpoint endPoint = mock(PrometheusEndpoint.class);
        final PrometheusDataProvider dataProvider =
                new PrometheusDataProvider(endPoint, NodeId.createMain(1L));
        final Metric metric = new DefaultCounter(new Counter.Config(Metrics.INFO_CATEGORY, "time"));

        // when
        dataProvider.init(List.of(metric));

        // then
        verify(endPoint, never()).createAdapter(any(), any());
    }

    @Test
    void testInitWithEmptyList() {
        // given
        final PrometheusEndpoint endPoint = mock(PrometheusEndpoint.class);
        final PrometheusDataProvider dataProvider = new PrometheusDataProvider(endPoint);

        // when
        dataProvider.init(List.of());

        // then
        verify(endPoint, never()).createAdapter(any(), any());
    }

    @Test
    void testInitWithNullParameter() {
        // given
        final PrometheusEndpoint endPoint = mock(PrometheusEndpoint.class);
        final PrometheusDataProvider dataProvider = new PrometheusDataProvider(endPoint);

        // then
        assertThatThrownBy(() -> dataProvider.init(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testHandleSnapshotsGlobal() {
        // given
        final DefaultCounter metric = mock(DefaultCounter.class);
        final Snapshot snapshot = Snapshot.of(metric);
        final PrometheusEndpoint endPoint = mock(PrometheusEndpoint.class);
        final CounterAdapter adapter = mock(CounterAdapter.class);
        when(endPoint.getAdapter(metric)).thenReturn(adapter);
        final PrometheusDataProvider dataProvider = new PrometheusDataProvider(endPoint);

        // when
        dataProvider.handleSnapshots(List.of(snapshot));

        // then
        verify(adapter).update(snapshot, null);
    }

    @Test
    void testHandleSnapshotsPlatform() {
        // given
        final DefaultCounter metric = mock(DefaultCounter.class);
        final Snapshot snapshot = Snapshot.of(metric);
        final PrometheusEndpoint endPoint = mock(PrometheusEndpoint.class);
        final CounterAdapter adapter = mock(CounterAdapter.class);
        when(endPoint.getAdapter(metric)).thenReturn(adapter);
        final NodeId nodeId = NodeId.createMain(1L);
        final PrometheusDataProvider dataProvider = new PrometheusDataProvider(endPoint, nodeId);

        // when
        dataProvider.handleSnapshots(List.of(snapshot));

        // then
        verify(adapter).update(snapshot, nodeId);
    }
}
