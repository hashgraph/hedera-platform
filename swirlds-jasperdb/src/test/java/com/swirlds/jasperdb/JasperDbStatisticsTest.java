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
package com.swirlds.jasperdb;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static com.swirlds.jasperdb.JasperDbStatistics.STAT_CATEGORY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.PlatformMetricsFactory;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

class JasperDbStatisticsTest {

    private static final String LABEL = "LaBeL";

    @Test
    void testInitialState() {
        // given
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, true);

        // then
        assertDoesNotThrow(statistics::cycleInternalNodeWritesPerSecond);
        assertDoesNotThrow(statistics::cycleInternalNodeReadsPerSecond);
        assertDoesNotThrow(statistics::cycleLeafWritesPerSecond);
        assertDoesNotThrow(statistics::cycleLeafByKeyReadsPerSecond);
        assertDoesNotThrow(statistics::cycleLeafByPathReadsPerSecond);
        assertDoesNotThrow(() -> statistics.setInternalHashesStoreFileCount(42));
        assertDoesNotThrow(() -> statistics.setInternalHashesStoreTotalFileSizeInMB(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreFileCount(42));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreTotalFileSizeInMB(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafPathToHashKeyValueStoreFileCount(42));
        assertDoesNotThrow(
                () -> statistics.setLeafPathToHashKeyValueStoreTotalFileSizeInMB(Math.PI));
        assertDoesNotThrow(() -> statistics.setInternalHashesStoreSmallMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setInternalHashesStoreMediumMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setInternalHashesStoreLargeMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreSmallMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreMediumMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreLargeMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafPathToHashKeyValueStoreSmallMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafPathToHashKeyValueStoreMediumMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafPathToHashKeyValueStoreLargeMergeTime(Math.PI));
    }

    @Test
    void testNonLongKeyMode() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);

        // then
        assertNull(metrics.getMetric(STAT_CATEGORY, "leafKeyToPathFileCount_" + LABEL));
        assertNull(metrics.getMetric(STAT_CATEGORY, "leafKeyToPathFileSizeMb_" + LABEL));
        assertNull(metrics.getMetric(STAT_CATEGORY, "leafKeyToPathSmallMergeTime_" + LABEL));
        assertNull(metrics.getMetric(STAT_CATEGORY, "leafKeyToPathMediumMergeTime_" + LABEL));
        assertNull(metrics.getMetric(STAT_CATEGORY, "leafKeyToPathLargeMergeTime_" + LABEL));

        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreFileCount(42));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreTotalFileSizeInMB(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreSmallMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreMediumMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreLargeMergeTime(Math.PI));
    }

    @Test
    void testConstructorWithNullParameter() {
        assertThrows(IllegalArgumentException.class, () -> new JasperDbStatistics(null, true));
    }

    @Test
    void testRegisterWithNullParameter() {
        // given
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);

        // then
        assertThrows(IllegalArgumentException.class, () -> statistics.registerMetrics(null));
    }

    @Test
    void testCycleInternalNodeWritesPerSecond() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalNodeWrites/s_" + LABEL);

        // when
        statistics.cycleInternalNodeWritesPerSecond();

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testCycleInternalNodeReadsPerSecond() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalNodeReads/s_" + LABEL);

        // when
        statistics.cycleInternalNodeReadsPerSecond();

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testCycleLeafWritesPerSecond() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafWrites/s_" + LABEL);

        // when
        statistics.cycleLeafWritesPerSecond();

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testCycleLeafByKeyReadsPerSecond() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafByKeyReads/s_" + LABEL);

        // when
        statistics.cycleLeafByKeyReadsPerSecond();

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testCycleLeafByPathReadsPerSecond() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafByPathReads/s_" + LABEL);

        // when
        statistics.cycleLeafByPathReadsPerSecond();

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetInternalHashesStoreFileCount() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalHashFileCount_" + LABEL);

        // when
        statistics.setInternalHashesStoreFileCount(42);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetInternalHashesStoreTotalFileSizeInMB() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalHashFileSizeMb_" + LABEL);

        // when
        statistics.setInternalHashesStoreTotalFileSizeInMB(42);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetLeafKeyToPathStoreFileCount() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, true);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafKeyToPathFileCount_" + LABEL);

        // when
        statistics.setLeafKeyToPathStoreFileCount(42);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetLeafKeyToPathStoreTotalFileSizeInMB() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, true);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafKeyToPathFileSizeMb_" + LABEL);

        // when
        statistics.setLeafKeyToPathStoreTotalFileSizeInMB(Math.PI);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetLeafPathToHashKeyValueStoreFileCount() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafHKVFileCount_" + LABEL);

        // when
        statistics.setLeafPathToHashKeyValueStoreFileCount(42);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetLeafPathToHashKeyValueStoreTotalFileSizeInMB() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafHKVFileSizeMb_" + LABEL);

        // when
        statistics.setLeafPathToHashKeyValueStoreTotalFileSizeInMB(Math.PI);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetInternalHashesStoreSmallMergeTime() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);
        final Metric metric =
                metrics.getMetric(STAT_CATEGORY, "internalHashSmallMergeTime_" + LABEL);

        // when
        statistics.setInternalHashesStoreSmallMergeTime(Math.PI);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetInternalHashesStoreMediumMergeTime() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);
        final Metric metric =
                metrics.getMetric(STAT_CATEGORY, "internalHashMediumMergeTime_" + LABEL);

        // when
        statistics.setInternalHashesStoreMediumMergeTime(Math.PI);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetInternalHashesStoreLargeMergeTime() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);
        final Metric metric =
                metrics.getMetric(STAT_CATEGORY, "internalHashLargeMergeTime_" + LABEL);

        // when
        statistics.setInternalHashesStoreLargeMergeTime(Math.PI);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetLeafKeyToPathStoreSmallMergeTime() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, true);
        statistics.registerMetrics(metrics);
        final Metric metric =
                metrics.getMetric(STAT_CATEGORY, "leafKeyToPathSmallMergeTime_" + LABEL);

        // when
        statistics.setLeafKeyToPathStoreSmallMergeTime(Math.PI);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetLeafKeyToPathStoreMediumMergeTime() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, true);
        statistics.registerMetrics(metrics);
        final Metric metric =
                metrics.getMetric(STAT_CATEGORY, "leafKeyToPathMediumMergeTime_" + LABEL);

        // when
        statistics.setLeafKeyToPathStoreMediumMergeTime(Math.PI);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetLeafKeyToPathStoreLargeMergeTime() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, true);
        statistics.registerMetrics(metrics);
        final Metric metric =
                metrics.getMetric(STAT_CATEGORY, "leafKeyToPathLargeMergeTime_" + LABEL);

        // when
        statistics.setLeafKeyToPathStoreLargeMergeTime(Math.PI);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetLeafPathToHashKeyValueStoreSmallMergeTime() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, true);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafHKVSmallMergeTime_" + LABEL);

        // when
        statistics.setLeafPathToHashKeyValueStoreSmallMergeTime(Math.PI);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetLeafPathToHashKeyValueStoreMediumMergeTime() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, true);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafHKVMediumMergeTime_" + LABEL);

        // when
        statistics.setLeafPathToHashKeyValueStoreMediumMergeTime(Math.PI);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testSetLeafPathToHashKeyValueStoreLargeMergeTime() {
        // given
        final Metrics metrics =
                new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, true);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafHKVLargeMergeTime_" + LABEL);

        // when
        statistics.setLeafPathToHashKeyValueStoreLargeMergeTime(Math.PI);

        // then
        assertNotEquals(0.0, metric.get(VALUE));
    }
}
