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
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.MetricConfig;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.MetricsFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsTest {

	private static final String CATEGORY_1 = "CaTeGoRy1";
	private static final String CATEGORY_1a = "CaTeGoRy1.a";
	private static final String CATEGORY_1b = "CaTeGoRy1.b";
	private static final String CATEGORY_2 = "CaTeGoRy2";
	private static final String CATEGORY_11 = "CaTeGoRy11";
	private static final String NAME_1 = "NaMe1";
	private static final String NAME_2 = "NaMe2";

	private ScheduledExecutorService executor;
	private MetricsFactory factory;
	private Metrics metrics;

	private Counter counter_1_1;
	private Counter counter_1a_1;
	private Counter counter_1b_1;
	private Counter counter_1_2;
	private Counter counter_2_1;
	private Counter counter_11_1;

	@BeforeEach
	void setupService() {
		SettingsCommon.metricsUpdatePeriodMillis = -1;
		executor = mock(ScheduledExecutorService.class);
		factory = mock(MetricsFactory.class);
		metrics = new Metrics(executor, factory);
		setupFactory();
	}

	private void setupFactory() {
		counter_1_1 = mock(Counter.class);
		when(counter_1_1.getCategory()).thenReturn(CATEGORY_1);
		when(counter_1_1.getName()).thenReturn(NAME_1);

		counter_1a_1 = mock(Counter.class);
		when(counter_1a_1.getCategory()).thenReturn(CATEGORY_1a);
		when(counter_1a_1.getName()).thenReturn(NAME_1);

		counter_1b_1 = mock(Counter.class);
		when(counter_1b_1.getCategory()).thenReturn(CATEGORY_1b);
		when(counter_1b_1.getName()).thenReturn(NAME_1);

		counter_1_2 = mock(Counter.class);
		when(counter_1_2.getCategory()).thenReturn(CATEGORY_1);
		when(counter_1_2.getName()).thenReturn(NAME_2);

		counter_2_1 = mock(Counter.class);
		when(counter_2_1.getCategory()).thenReturn(CATEGORY_2);
		when(counter_2_1.getName()).thenReturn(NAME_1);

		counter_11_1 = mock(Counter.class);
		when(counter_11_1.getCategory()).thenReturn(CATEGORY_11);
		when(counter_11_1.getName()).thenReturn(NAME_1);

		when(factory.createCounter(any())).thenReturn(counter_1_1, counter_1a_1, counter_1b_1, counter_1_2, counter_2_1, counter_11_1);
		final Counter created_1_1 = metrics.getOrCreate(new Counter.Config(CATEGORY_1, NAME_1));
		final Counter created_1a_1 = metrics.getOrCreate(new Counter.Config(CATEGORY_1a, NAME_1));
		final Counter created_1b_1 = metrics.getOrCreate(new Counter.Config(CATEGORY_1b, NAME_1));
		final Counter created_1_2 = metrics.getOrCreate(new Counter.Config(CATEGORY_1, NAME_2));
		final Counter created_2_1 = metrics.getOrCreate(new Counter.Config(CATEGORY_2, NAME_1));
		final Counter created_11_1 = metrics.getOrCreate(new Counter.Config(CATEGORY_11, NAME_1));
		assertThat(created_1_1).isSameAs(counter_1_1);
		assertThat(created_1a_1).isSameAs(counter_1a_1);
		assertThat(created_1b_1).isSameAs(counter_1b_1);
		assertThat(created_1_2).isSameAs(counter_1_2);
		assertThat(created_2_1).isSameAs(counter_2_1);
		assertThat(created_11_1).isSameAs(counter_11_1);
	}

	@Test
	void testConstructorWithNullParameter() {
		SettingsCommon.metricsUpdatePeriodMillis = 3000;
		assertThatThrownBy(() -> new Metrics(null, factory)).isInstanceOf(IllegalArgumentException.class);
		SettingsCommon.metricsUpdatePeriodMillis = -1;
		assertThatCode(() -> new Metrics(null, factory)).doesNotThrowAnyException();
		assertThatThrownBy(() -> new Metrics(executor, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testGetSingleMetricThatExists() {
		// when
		final Metric actual = metrics.getMetric(CATEGORY_1, NAME_1);

		// then
		assertThat(actual).isEqualTo(counter_1_1);
	}

	@Test
	void testGetSingleMetricThatDoesNotExists() {
		// when
		final Metric actual = metrics.getMetric(CATEGORY_2, NAME_2);

		// then
		assertThat(actual).isNull();
	}

	@Test
	void testGetSingleMetricWithNullParameter() {
		assertThatThrownBy(() -> metrics.getMetric(null, NAME_1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> metrics.getMetric(CATEGORY_1, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testGetMetricsOfCategory() {
		// when
		final Collection<Metric> actual = metrics.findMetricsByCategory(CATEGORY_1);

		// then
		assertThat(actual).containsExactly(counter_1_1, counter_1_2, counter_1a_1, counter_1b_1);
	}

	@Test
	void testGetMetricsOfNonExistingCategory() {
		// when
		final Collection<Metric> actual = metrics.findMetricsByCategory("NonExistingCategory");

		// then
		assertThat(actual).isEmpty();
	}

	@Test
	void testGetMetricsOfCategoryAfterMetricWasAdded() {
		// given
		final Collection<Metric> actual = metrics.findMetricsByCategory(CATEGORY_1);
		final Counter newCounter = mock(Counter.class);
		when(newCounter.getCategory()).thenReturn(CATEGORY_1);
		when(newCounter.getName()).thenReturn("New Counter");
		when(factory.createCounter(any())).thenReturn(newCounter);

		// when
		final Counter newCreated = metrics.getOrCreate(new Counter.Config(CATEGORY_1, "New Counter"));

		// then
		assertThat(newCreated).isSameAs(newCounter);
		assertThat(actual).containsExactly(counter_1_1, counter_1_2, newCounter, counter_1a_1, counter_1b_1);
	}


	@Test
	void testGetMetricsOfCategoryAfterMetricWasRemoved() {
		// given
		final Collection<Metric> actual = metrics.findMetricsByCategory(CATEGORY_1);

		// when
		metrics.remove(counter_1_1);

		// then
		assertThat(actual).containsExactly(counter_1_2, counter_1a_1, counter_1b_1);
	}

	@Test
	void testGetMetricsOfCategoryWithNullParameter() {
		assertThatThrownBy(() -> metrics.findMetricsByCategory(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testGetAllMetrics() {
		// when
		final Collection<Metric> actual = metrics.getAll();

		// then
		assertThat(actual).containsExactly(counter_1_1, counter_1_2, counter_1a_1, counter_1b_1, counter_11_1, counter_2_1);
	}

	@Test
	void testGetAllMetricsAfterMetricWasAdded() {
		// given
		final Collection<Metric> actual = metrics.getAll();
		final Counter newCounter = mock(Counter.class);
		when(newCounter.getCategory()).thenReturn(CATEGORY_1);
		when(newCounter.getName()).thenReturn("New Counter");
		when(factory.createCounter(any())).thenReturn(newCounter);

		// when
		final Counter newCreated = metrics.getOrCreate(new Counter.Config(CATEGORY_1, "New Counter"));

		// then
		assertThat(newCreated).isSameAs(newCounter);
		assertThat(actual).containsExactly(counter_1_1, counter_1_2, newCounter, counter_1a_1, counter_1b_1, counter_11_1, counter_2_1);
	}


	@Test
	void testGetAllMetricsAfterMetricWasRemoved() {
		// given
		final Collection<Metric> actual = metrics.getAll();

		// when
		metrics.remove(counter_1_1);

		// then
		assertThat(actual).containsExactly(counter_1_2, counter_1a_1, counter_1b_1, counter_11_1, counter_2_1);
	}

	@Test
	void testReset() {
		// when
		metrics.resetAll();

		// then
		verify(counter_1_1, atLeastOnce()).reset();
	}

	@Test
	void testCreateDuplicateMetric() {
		// when
		final Counter actual = metrics.getOrCreate(new Counter.Config(CATEGORY_1, NAME_1));

		// then
		assertThat(actual).isSameAs(counter_1_1);
	}

	@Test
	void testCreateDuplicateMetricWithWrongType() {
		final IntegerGauge.Config config = new IntegerGauge.Config(CATEGORY_1, NAME_1);
		assertThatThrownBy(() -> metrics.getOrCreate(config)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void testCreateMetricWithNullParameter() {
		assertThatThrownBy(() -> metrics.getOrCreate(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testRemoveByNameAndCategory() {
		// when
		metrics.remove(CATEGORY_1, NAME_1);

		// then
		final Collection<Metric> remaining = metrics.getAll();
		assertThat(remaining).containsExactly(counter_1_2, counter_1a_1, counter_1b_1, counter_11_1, counter_2_1);
	}

	@Test
	void testRemoveNonExistingByNameAndCategory() {
		// when
		metrics.remove(CATEGORY_2, NAME_2);

		// then
		final Collection<Metric> remaining = metrics.getAll();
		assertThat(remaining).containsExactly(counter_1_1, counter_1_2, counter_1a_1, counter_1b_1, counter_11_1, counter_2_1);
	}

	@Test
	void testRemoveByNameAndCategoryWithNullParameter() {
		assertThatThrownBy(() -> metrics.remove(null, NAME_1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> metrics.remove(CATEGORY_1, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testRemoveByMetric() {
		// when
		metrics.remove(counter_1_1);

		// then
		final Collection<Metric> remaining = metrics.getAll();
		assertThat(remaining).containsExactly(counter_1_2, counter_1a_1, counter_1b_1, counter_11_1, counter_2_1);
	}

	@Test
	void testRemoveByMetricWithWrongClass() {
		// given
		final IntegerGauge gauge = mock(IntegerGauge.class);
		when(gauge.getCategory()).thenReturn(CATEGORY_1);
		when(gauge.getName()).thenReturn(NAME_1);

		// when
		metrics.remove(gauge);

		// then
		final Collection<Metric> remaining = metrics.getAll();
		assertThat(remaining).containsExactly(counter_1_1, counter_1_2, counter_1a_1, counter_1b_1, counter_11_1, counter_2_1);
	}

	@Test
	void testRemoveNonExistingByMetric() {
		// given
		final Counter counter = mock(Counter.class);
		when(counter.getCategory()).thenReturn(CATEGORY_2);
		when(counter.getName()).thenReturn(NAME_2);

		// when
		metrics.remove(counter);

		// then
		final Collection<Metric> remaining = metrics.getAll();
		assertThat(remaining).containsExactly(counter_1_1, counter_1_2, counter_1a_1, counter_1b_1, counter_11_1, counter_2_1);
	}

	@Test
	void testRemoveByMetricWithNullParameter() {
		assertThatThrownBy(() -> metrics.remove((Metric) null)).isInstanceOf(IllegalArgumentException.class);
	}


	@Test
	void testRemoveByConfig() {
		// when
		metrics.remove(new Counter.Config(CATEGORY_1, NAME_1));

		// then
		final Collection<Metric> remaining = metrics.getAll();
		assertThat(remaining).containsExactly(counter_1_2, counter_1a_1, counter_1b_1, counter_11_1, counter_2_1);
	}

	@Test
	void testRemoveByConfigWithWrongClass() {
		// when
		metrics.remove(new IntegerGauge.Config(CATEGORY_1, NAME_1));

		// then
		final Collection<Metric> remaining = metrics.getAll();
		assertThat(remaining).containsExactly(counter_1_1, counter_1_2, counter_1a_1, counter_1b_1, counter_11_1, counter_2_1);
	}

	@Test
	void testRemoveNonExistingByConfig() {
		// when
		metrics.remove(new Counter.Config(CATEGORY_2, NAME_2));

		// then
		final Collection<Metric> remaining = metrics.getAll();
		assertThat(remaining).containsExactly(counter_1_1, counter_1_2, counter_1a_1, counter_1b_1, counter_11_1, counter_2_1);
	}

	@Test
	void testRemoveByConfigWithNullParameter() {
		assertThatThrownBy(() -> metrics.remove((MetricConfig<?, ?>) null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testUpdater() {
		// given
		SettingsCommon.metricsUpdatePeriodMillis = 10;
		final ScheduledExecutorService executor1 = Executors.newSingleThreadScheduledExecutor();
		final Metrics metrics = new Metrics(executor1, factory);
		final Runnable updater = mock(Runnable.class);
		metrics.addUpdater(updater);

		// then
		verify(updater, Mockito.after(100).never()).run();

		// when
		metrics.startUpdaters();

		// then
		verify(updater, timeout(100).atLeastOnce()).run();
	}

	@Test
	void testDisabledUpdater() {
		// given
		SettingsCommon.metricsUpdatePeriodMillis = 0;
		final ScheduledExecutorService executor1 = Executors.newSingleThreadScheduledExecutor();
		final Metrics metrics = new Metrics(executor1, factory);
		final Runnable updater = mock(Runnable.class);
		metrics.addUpdater(updater);

		// when
		metrics.startUpdaters();

		// then
		verify(updater, Mockito.after(100).never()).run();
	}

	@Test
	void testUpdaterWithNullParameter() {
		assertThatThrownBy(() -> metrics.addUpdater(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testUpdaterAddedAfterStart() {
		// given
		SettingsCommon.metricsUpdatePeriodMillis = 10;
		final ScheduledExecutorService executor1 = Executors.newSingleThreadScheduledExecutor();
		final Metrics metrics = new Metrics(executor1, factory);
		final Runnable updater = mock(Runnable.class);
		metrics.startUpdaters();

		// when
		metrics.addUpdater(updater);

		// then
		verify(updater, timeout(100).atLeastOnce()).run();
	}



}
