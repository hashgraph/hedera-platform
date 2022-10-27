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
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.IntegerAccumulator;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.metrics.LongGauge;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.StatEntry;
import com.swirlds.common.metrics.platform.LegacyCsvWriter;
import com.swirlds.common.metrics.platform.PlatformMetric;
import com.swirlds.common.metrics.platform.PlatformMetricsFactory;
import com.swirlds.common.metrics.platform.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_3_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LegacyCsvWriterTest {

	private Metrics metrics;

	@BeforeEach
	void setStandardSettings() {
		SettingsCommon.csvAppend = false;
		SettingsCommon.showInternalStats = false;
		SettingsCommon.verboseStatistics = false;
		metrics = new Metrics(mock(ScheduledExecutorService.class), new PlatformMetricsFactory());
	}

	@Test
	void testToString(@TempDir Path tempDir) throws IOException {
		// given
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);

		// then
		assertThat(writer).hasToString("LegacyCsvWriter{csvFilePath=" + csvFilePath + '}');
	}

	@Test
	void testPrepareFileDefault(@TempDir Path tempDir) throws IOException {
		// given
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		Files.delete(csvFilePath);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createCompleteList();

		// when
		writer.prepareFile(metrics);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				filename:,.*,
				Counter:,Counter,
				DoubleGauge:,DoubleGauge,
				FunctionGauge:,FunctionGauge,
				IntegerAccumulator:,IntegerAccumulator,
				IntegerGauge:,IntegerGauge,
				IntegerPairAccumulator:,IntegerPairAccumulator,
				LongAccumulator:,LongAccumulator,
				LongGauge:,LongGauge,
				RunningAverageMetric:,RunningAverageMetric,
				SpeedometerMetric:,SpeedometerMetric,
				StatEntry:,StatEntry,

				,,platform,platform,platform,platform,platform,platform,platform,platform,platform,platform,platform,
				,,Counter,DoubleGauge,FunctionGauge,IntegerAccumulator,IntegerGauge,IntegerPairAccumulator,LongAccumulator,LongGauge,RunningAverageMetric,SpeedometerMetric,StatEntry,
				"""
		);
	}

	@Test
	void testPrepareFileWithExistingFile(@TempDir Path tempDir) throws IOException {
		// given
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		Files.writeString(csvFilePath, "Hello World");
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createShortList();

		// when
		writer.prepareFile(metrics);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				filename:,.*,
				Counter:,Counter,
				DoubleGauge:,DoubleGauge,

				,,platform,platform,
				,,Counter,DoubleGauge,
				"""
		);
	}

	@Test
	void testPrepareFileWithAppendedModeAndExistingFile(@TempDir Path tempDir) throws IOException {
		// given
		SettingsCommon.csvAppend = true;
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		Files.writeString(csvFilePath,
				"""
				filename:,/tmp/tempfile.tmp,
				Counter:,Counter,
				DoubleGauge:,DoubleGauge,

				,,platform,platform,
				,,Counter,DoubleGauge,
				,,1,2.0,
				,,11,12.0,
				"""
		);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createShortList();

		// when
		writer.prepareFile(metrics);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).isEqualTo(
				"""
				filename:,/tmp/tempfile.tmp,
				Counter:,Counter,
				DoubleGauge:,DoubleGauge,

				,,platform,platform,
				,,Counter,DoubleGauge,
				,,1,2.0,
				,,11,12.0,


				"""
		);
	}

	@Test
	void testPrepareFileWithAppendedModeAndNonExistingFile(@TempDir Path tempDir) throws IOException {
		// given
		SettingsCommon.csvAppend = true;
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		Files.delete(csvFilePath);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createShortList();

		// when
		writer.prepareFile(metrics);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				filename:,.*,
				Counter:,Counter,
				DoubleGauge:,DoubleGauge,

				,,platform,platform,
				,,Counter,DoubleGauge,
				"""
		);
	}

	@Test
	void testPrepareFileWithInternalIgnored(@TempDir Path tempDir) throws IOException {
		// given
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createListWithInternals();

		// when
		writer.prepareFile(metrics);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				filename:,.*,
				Public Counter:,Public Counter,
				Public DoubleGauge:,Public DoubleGauge,

				,,platform,platform,
				,,Public Counter,Public DoubleGauge,
				"""
		);
	}

	@Test
	void testPrepareFileWithInternalNotIgnored(@TempDir Path tempDir) throws IOException {
		// given
		SettingsCommon.showInternalStats = true;
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createListWithInternals();

		// when
		writer.prepareFile(metrics);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				filename:,.*,
				Internal Counter:,Internal Counter,
				Public Counter:,Public Counter,
				Internal DoubleGauge:,Internal DoubleGauge,
				Public DoubleGauge:,Public DoubleGauge,

				,,internal,platform,internal,platform,
				,,Internal Counter,Public Counter,Internal DoubleGauge,Public DoubleGauge,
				"""
		);
	}

	@Test
	void testPrepareFileWithSecondaryValuesNotIncluded(@TempDir Path tempDir) throws IOException {
		// given
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createListWithSecondaryValues();

		// when
		writer.prepareFile(metrics);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				filename:,.*,
				RunningAverageMetric:,RunningAverageMetric,
				SpeedometerMetric:,SpeedometerMetric,
				RunningAverageMetric Info:,RunningAverageMetric Info,
				SpeedometerMetric Info:,SpeedometerMetric Info,

				,,platform,platform,platform\\.info,platform\\.info,
				,,RunningAverageMetric,SpeedometerMetric,RunningAverageMetric Info,SpeedometerMetric Info,
				"""
		);
	}

	@Test
	void testPrepareFileWithSecondaryValuesIncluded(@TempDir Path tempDir) throws IOException {
		// given
		SettingsCommon.verboseStatistics = true;
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createListWithSecondaryValues();

		// when
		writer.prepareFile(metrics);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				filename:,.*,
				RunningAverageMetric:,RunningAverageMetric,
				SpeedometerMetric:,SpeedometerMetric,
				RunningAverageMetric Info:,RunningAverageMetric Info,
				SpeedometerMetric Info:,SpeedometerMetric Info,

				,,platform,platform,platform,platform,platform,platform,platform,platform,platform\\.info,platform\\.info,
				,,RunningAverageMetric,RunningAverageMetricMax,RunningAverageMetricMin,RunningAverageMetricStd,SpeedometerMetric,SpeedometerMetricMax,SpeedometerMetricMin,SpeedometerMetricStd,RunningAverageMetric Info,SpeedometerMetric Info,
				"""
		);
	}

	@Test
	void testParentFolderCreation(@TempDir Path tempDir) throws IOException {
		// given
		final Path grandParentPath = Files.createTempDirectory(tempDir, null);
		final Path parentPath = Files.createTempDirectory(grandParentPath, null);
		final Path csvFilePath = Files.createTempFile(parentPath, null, null);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		Files.delete(csvFilePath);
		Files.delete(parentPath);
		Files.delete(grandParentPath);
		final List<Metric> metrics = createShortList();

		// when
		writer.prepareFile(metrics);

		// then
		assertThat(grandParentPath).exists();
		assertThat(parentPath).exists();
		assertThat(csvFilePath).exists();
	}

	@Test
	void testWriteDefault(@TempDir Path tempDir) throws IOException {
		// given
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createCompleteList();
		final List<Snapshot> snapshots1 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();

		// when
		writer.prepareFile(metrics);
		writer.writeMetrics(snapshots1);

		// update metrics
		((Counter)metrics.get(0)).increment();
		((DoubleGauge)metrics.get(1)).set(Math.PI);
		((IntegerAccumulator)metrics.get(3)).update(42);
		((IntegerGauge)metrics.get(4)).set(42);
		((IntegerPairAccumulator<Double>)metrics.get(5)).update(4711, 42);
		((LongAccumulator)metrics.get(6)).update(42L);
		((LongGauge)metrics.get(7)).set(4711L);
		((RunningAverageMetric)metrics.get(8)).update(1000.0);
		((SpeedometerMetric)metrics.get(9)).update(10);
		final List<Snapshot> snapshots2 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();

		// when
		writer.writeMetrics(snapshots2);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				(.*\\n){14}.*
				,,0,0\\.0,Hello FunctionGauge,0,0,0.0,0,0,0\\.0,0\\.0,Hello StatEntry,
				,,1,3\\.1,Hello FunctionGauge,42,42,112\\.2,42,4711,1000\\.0,\\d*\\.\\d,Hello StatEntry,
				"""
		);
	}

	@Test
	void testWritingOfSpecialValues(@TempDir Path tempDir) throws IOException {
		// given
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createShortList();
		final DoubleGauge gauge = (DoubleGauge) metrics.get(1);
		writer.prepareFile(metrics);

		// when
		gauge.set(Double.NaN);
		final List<Snapshot> snapshots1 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();
		writer.writeMetrics(snapshots1);
		gauge.set(Double.POSITIVE_INFINITY);
		final List<Snapshot> snapshots2 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();
		writer.writeMetrics(snapshots2);
		gauge.set(Double.NEGATIVE_INFINITY);
		final List<Snapshot> snapshots3 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();
		writer.writeMetrics(snapshots3);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				(.*\\n){5}.*
				,,0,0.0,
				,,0,0.0,
				,,0,0.0,
				"""
		);
	}

	@Test
	void testWriteWithInternalIgnored(@TempDir Path tempDir) throws IOException {
		// given
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createListWithInternals();
		final List<Snapshot> snapshots1 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();

		// when
		writer.prepareFile(metrics);
		writer.writeMetrics(snapshots1);

		// update metrics
		((Counter)metrics.get(0)).add(2);
		((Counter)metrics.get(1)).add(3);
		((DoubleGauge)metrics.get(2)).set(Math.PI);
		((DoubleGauge)metrics.get(3)).set(Math.E);
		final List<Snapshot> snapshots2 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();

		// when
		writer.writeMetrics(snapshots2);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				(.*\\n){5}.*
				,,0,0.0,
				,,3,2.7,
				"""
		);
	}

	@Test
	void testWriteWithInternalNotIgnored(@TempDir Path tempDir) throws IOException {
		// given
		SettingsCommon.showInternalStats = true;
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createListWithInternals();
		final List<Snapshot> snapshots1 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();

		// when
		writer.prepareFile(metrics);
		writer.writeMetrics(snapshots1);

		// update metrics
		((Counter)metrics.get(0)).add(2);
		((Counter)metrics.get(1)).add(3);
		((DoubleGauge)metrics.get(2)).set(Math.PI);
		((DoubleGauge)metrics.get(3)).set(Math.E);
		final List<Snapshot> snapshots2 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();

		// when
		writer.writeMetrics(snapshots2);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				(.*\\n){7}.*
				,,0,0,0.0,0.0,
				,,2,3,3.1,2.7,
				"""
		);
	}


	@Test
	void testWriteWithSecondaryValuesNotIncluded(@TempDir Path tempDir) throws IOException {
		// given
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createListWithSecondaryValues();
		final List<Snapshot> snapshots1 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();

		// when
		writer.prepareFile(metrics);
		writer.writeMetrics(snapshots1);

		// update metrics
		((RunningAverageMetric)metrics.get(0)).update(1000.0);
		((SpeedometerMetric)metrics.get(1)).update(2000.0);
		((RunningAverageMetric)metrics.get(2)).update(3000.0);
		((SpeedometerMetric)metrics.get(3)).update(4000.0);
		final List<Snapshot> snapshots2 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();

		// when
		writer.writeMetrics(snapshots2);


		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				(.*\\n){7}.*
				,,0\\.0,0\\.0,0\\.0,0\\.0,
				,,1000\\.0,\\d*\\.\\d,3000\\.0,\\d*\\.\\d,
				"""
		);
	}

	@Test
	void testWriteWithSecondaryValuesIncluded(@TempDir Path tempDir) throws IOException {
		// given
		SettingsCommon.verboseStatistics = true;
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createListWithSecondaryValues();
		final List<Snapshot> snapshots1 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();

		// when
		writer.prepareFile(metrics);
		writer.writeMetrics(snapshots1);

		// update metrics
		((RunningAverageMetric)metrics.get(0)).update(1000.0);
		((SpeedometerMetric)metrics.get(1)).update(2000.0);
		((RunningAverageMetric)metrics.get(2)).update(3000.0);
		((SpeedometerMetric)metrics.get(3)).update(4000.0);
		final List<Snapshot> snapshots2 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();

		// when
		writer.writeMetrics(snapshots2);


		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				(.*\\n){7}.*
				,,0\\.0,0\\.0,0\\.0,0\\.0,0\\.0,0\\.0,0\\.0,0\\.0,0\\.0,0\\.0,
				,,1000\\.0,1000\\.0,1000\\.0,0\\.0,\\d*\\.\\d,\\d*\\.\\d,\\d*\\.\\d,0\\.0,3000\\.0,\\d*\\.\\d,
				"""
		);
	}


	@Test
	void testBrokenFormatString(@TempDir Path tempDir) throws IOException {
		// given
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final DoubleGauge gauge = metrics.getOrCreate(new DoubleGauge.Config(Metrics.PLATFORM_CATEGORY, "DoubleGauge").withFormat("%d").withInitialValue(Math.PI));
		final Snapshot snapshot = Snapshot.of((PlatformMetric) gauge);

		// when
		writer.prepareFile(List.of(gauge));
		writer.writeMetrics(List.of(snapshot));

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				(.*\\n){4}.*
				,,,
				"""
		);
	}

	@Test
	void testChangedEntriesWithSimpleMetrics(@TempDir Path tempDir) throws IOException {
		// given
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		Files.delete(csvFilePath);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createSimpleList();

		// when
		writer.prepareFile(metrics);

		// first row
		((Counter)metrics.get(1)).add(1L);
		((Counter)metrics.get(2)).add(2L);
		((Counter)metrics.get(3)).add(3L);
		((Counter)metrics.get(4)).add(4L);
		final List<Snapshot> snapshots1 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();
		writer.writeMetrics(snapshots1);

		// second row
		((Counter)metrics.get(1)).add(10L);
		((Counter)metrics.get(3)).add(30L);
		final List<Snapshot> snapshots2 = List.of(
				Snapshot.of((PlatformMetric) metrics.get(3)),
				Snapshot.of((PlatformMetric) metrics.get(1))
		);
		writer.writeMetrics(snapshots2);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				filename:,.*,
				Counter 1:,Counter 1,
				Counter 2:,Counter 2,
				Counter 3:,Counter 3,
				Counter 4:,Counter 4,
				Counter 5:,Counter 5,

				,,platform,platform,platform,platform,platform,
				,,Counter 1,Counter 2,Counter 3,Counter 4,Counter 5,
				,,0,1,2,3,4,
				,,,11,,33,,
				"""
		);
	}

	@Test
	void testChangedEntriesWithComplexMetricsAndNoSecondaryValues(@TempDir Path tempDir) throws IOException {
		// given
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		Files.delete(csvFilePath);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createComplexList();

		// when
		writer.prepareFile(metrics);

		// first row
		((RunningAverageMetric)metrics.get(1)).update(1000.0);
		((RunningAverageMetric)metrics.get(2)).update(2000.0);
		((RunningAverageMetric)metrics.get(3)).update(3000.0);
		((RunningAverageMetric)metrics.get(4)).update(4000.0);
		final List<Snapshot> snapshots1 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();
		writer.writeMetrics(snapshots1);

		// second row
		((RunningAverageMetric)metrics.get(1)).update(10000.0);
		((RunningAverageMetric)metrics.get(3)).update(30000.0);
		final List<Snapshot> snapshots2 = List.of(
				Snapshot.of((PlatformMetric) metrics.get(3)),
				Snapshot.of((PlatformMetric) metrics.get(1))
		);
		writer.writeMetrics(snapshots2);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				filename:,.*,
				RunningAverageMetric 1:,RunningAverageMetric 1,
				RunningAverageMetric 2:,RunningAverageMetric 2,
				RunningAverageMetric 3:,RunningAverageMetric 3,
				RunningAverageMetric 4:,RunningAverageMetric 4,
				RunningAverageMetric 5:,RunningAverageMetric 5,

				,,platform,platform,platform,platform,platform,
				,,RunningAverageMetric 1,RunningAverageMetric 2,RunningAverageMetric 3,RunningAverageMetric 4,RunningAverageMetric 5,
				,,0\\.0,1000\\.0,2000\\.0,3000\\.0,4000\\.0,
				,,,5\\d*\\.\\d,,16\\d*\\.\\d,,
				"""
		);
	}

	@Test
	void testChangedEntriesWithComplexMetricsAndSecondaryValues(@TempDir Path tempDir) throws IOException {
		// given
		SettingsCommon.verboseStatistics = true;
		final Path csvFilePath = Files.createTempFile(tempDir, null, null);
		Files.delete(csvFilePath);
		final LegacyCsvWriter writer = new LegacyCsvWriter(csvFilePath);
		final List<Metric> metrics = createComplexList();

		// when
		writer.prepareFile(metrics);

		// first row
		((RunningAverageMetric)metrics.get(1)).update(1000.0);
		((RunningAverageMetric)metrics.get(2)).update(2000.0);
		((RunningAverageMetric)metrics.get(3)).update(3000.0);
		((RunningAverageMetric)metrics.get(4)).update(4000.0);
		final List<Snapshot> snapshots1 = metrics.stream().map(PlatformMetric.class::cast).map(Snapshot::of).toList();
		writer.writeMetrics(snapshots1);

		// second row
		((RunningAverageMetric)metrics.get(1)).update(10000.0);
		((RunningAverageMetric)metrics.get(3)).update(30000.0);
		final List<Snapshot> snapshots2 = List.of(
				Snapshot.of((PlatformMetric) metrics.get(3)),
				Snapshot.of((PlatformMetric) metrics.get(1))
		);
		writer.writeMetrics(snapshots2);

		// then
		final String content = Files.readString(csvFilePath);
		assertThat(content).matches(
				"""
				filename:,.*,
				RunningAverageMetric 1:,RunningAverageMetric 1,
				RunningAverageMetric 2:,RunningAverageMetric 2,
				RunningAverageMetric 3:,RunningAverageMetric 3,
				RunningAverageMetric 4:,RunningAverageMetric 4,
				RunningAverageMetric 5:,RunningAverageMetric 5,

				,,platform,platform,platform,platform,platform,platform,platform,platform,platform,platform,platform,platform,platform,platform,platform,platform,platform,platform,platform,platform,
				,,RunningAverageMetric 1,RunningAverageMetric 1Max,RunningAverageMetric 1Min,RunningAverageMetric 1Std,RunningAverageMetric 2,RunningAverageMetric 2Max,RunningAverageMetric 2Min,RunningAverageMetric 2Std,RunningAverageMetric 3,RunningAverageMetric 3Max,RunningAverageMetric 3Min,RunningAverageMetric 3Std,RunningAverageMetric 4,RunningAverageMetric 4Max,RunningAverageMetric 4Min,RunningAverageMetric 4Std,RunningAverageMetric 5,RunningAverageMetric 5Max,RunningAverageMetric 5Min,RunningAverageMetric 5Std,
				,,0\\.0,0\\.0,0\\.0,0\\.0,1000\\.0,1000\\.0,1000\\.0,0\\.0,2000\\.0,2000\\.0,2000\\.0,0\\.0,3000\\.0,3000\\.0,3000\\.0,0\\.0,4000\\.0,4000\\.0,4000\\.0,0\\.0,
				,,,,,,5\\d*\\.\\d,5\\d*\\.\\d,1000.0,\\d*\\.\\d,,,,,16\\d*\\.\\d,16\\d*\\.\\d,3000.0,\\d*\\.\\d,,,,,
				"""
		);
	}

	private List<Metric> createCompleteList() {
		return List.of(
				metrics.getOrCreate(new Counter.Config(Metrics.PLATFORM_CATEGORY, "Counter")),
				metrics.getOrCreate(new DoubleGauge.Config(Metrics.PLATFORM_CATEGORY, "DoubleGauge").withFormat(FORMAT_3_1)),
				metrics.getOrCreate(new FunctionGauge.Config<>(Metrics.PLATFORM_CATEGORY, "FunctionGauge", () -> "Hello FunctionGauge").withFormat("%s")),
				metrics.getOrCreate(new IntegerAccumulator.Config(Metrics.PLATFORM_CATEGORY, "IntegerAccumulator")),
				metrics.getOrCreate(new IntegerGauge.Config(Metrics.PLATFORM_CATEGORY, "IntegerGauge")),
				metrics.getOrCreate(new IntegerPairAccumulator.Config<>(Metrics.PLATFORM_CATEGORY, "IntegerPairAccumulator", IntegerPairAccumulator.AVERAGE).withFormat(FORMAT_3_1)),
				metrics.getOrCreate(new LongAccumulator.Config(Metrics.PLATFORM_CATEGORY, "LongAccumulator")),
				metrics.getOrCreate(new LongGauge.Config(Metrics.PLATFORM_CATEGORY, "LongGauge")),
				metrics.getOrCreate(new RunningAverageMetric.Config(Metrics.PLATFORM_CATEGORY, "RunningAverageMetric").withFormat(FORMAT_3_1)),
				metrics.getOrCreate(new SpeedometerMetric.Config(Metrics.PLATFORM_CATEGORY, "SpeedometerMetric").withFormat(FORMAT_3_1)),
				metrics.getOrCreate(new StatEntry.Config(Metrics.PLATFORM_CATEGORY, "StatEntry", () -> "Hello StatEntry").withFormat("%s"))
		);
	}

	private List<Metric> createShortList() {
		return List.of(
				metrics.getOrCreate(new Counter.Config(Metrics.PLATFORM_CATEGORY, "Counter")),
				metrics.getOrCreate(new DoubleGauge.Config(Metrics.PLATFORM_CATEGORY, "DoubleGauge").withFormat(FORMAT_3_1))
		);
	}

	private List<Metric> createListWithInternals() {
		return List.of(
				metrics.getOrCreate(new Counter.Config(Metrics.INTERNAL_CATEGORY, "Internal Counter")),
				metrics.getOrCreate(new Counter.Config(Metrics.PLATFORM_CATEGORY, "Public Counter")),
				metrics.getOrCreate(new DoubleGauge.Config(Metrics.INTERNAL_CATEGORY, "Internal DoubleGauge").withFormat(FORMAT_3_1)),
				metrics.getOrCreate(new DoubleGauge.Config(Metrics.PLATFORM_CATEGORY, "Public DoubleGauge").withFormat(FORMAT_3_1))
		);
	}

	private List<Metric> createListWithSecondaryValues() {
		return List.of(
				metrics.getOrCreate(new RunningAverageMetric.Config(Metrics.PLATFORM_CATEGORY, "RunningAverageMetric").withFormat(FORMAT_3_1)),
				metrics.getOrCreate(new SpeedometerMetric.Config(Metrics.PLATFORM_CATEGORY, "SpeedometerMetric").withFormat(FORMAT_3_1)),
				metrics.getOrCreate(new RunningAverageMetric.Config(Metrics.INFO_CATEGORY, "RunningAverageMetric Info").withFormat(FORMAT_3_1)),
				metrics.getOrCreate(new SpeedometerMetric.Config(Metrics.INFO_CATEGORY, "SpeedometerMetric Info").withFormat(FORMAT_3_1))
		);
	}

	private List<Metric> createSimpleList() {
		return List.of(
				metrics.getOrCreate(new Counter.Config(Metrics.PLATFORM_CATEGORY, "Counter 1")),
				metrics.getOrCreate(new Counter.Config(Metrics.PLATFORM_CATEGORY, "Counter 2")),
				metrics.getOrCreate(new Counter.Config(Metrics.PLATFORM_CATEGORY, "Counter 3")),
				metrics.getOrCreate(new Counter.Config(Metrics.PLATFORM_CATEGORY, "Counter 4")),
				metrics.getOrCreate(new Counter.Config(Metrics.PLATFORM_CATEGORY, "Counter 5"))
		);
	}

	private List<Metric> createComplexList() {
		return List.of(
				metrics.getOrCreate(new RunningAverageMetric.Config(Metrics.PLATFORM_CATEGORY, "RunningAverageMetric 1").withFormat(FORMAT_3_1)),
				metrics.getOrCreate(new RunningAverageMetric.Config(Metrics.PLATFORM_CATEGORY, "RunningAverageMetric 2").withFormat(FORMAT_3_1)),
				metrics.getOrCreate(new RunningAverageMetric.Config(Metrics.PLATFORM_CATEGORY, "RunningAverageMetric 3").withFormat(FORMAT_3_1)),
				metrics.getOrCreate(new RunningAverageMetric.Config(Metrics.PLATFORM_CATEGORY, "RunningAverageMetric 4").withFormat(FORMAT_3_1)),
				metrics.getOrCreate(new RunningAverageMetric.Config(Metrics.PLATFORM_CATEGORY, "RunningAverageMetric 5").withFormat(FORMAT_3_1))
		);
	}
}
