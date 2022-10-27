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

package com.swirlds.common.metrics.platform;

import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotValue;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

/**
 * Platform-implementation of {@link IntegerGauge}
 */
public class PlatformIntegerGauge extends PlatformMetric implements IntegerGauge {

	private final AtomicInteger value;

	public PlatformIntegerGauge(final IntegerGauge.Config config) {
		super(config);
		this.value = new AtomicInteger(config.getInitialValue());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<SnapshotValue> takeSnapshot() {
		return List.of(new SnapshotValue(VALUE, get()));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int get() {
		return value.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void set(final int newValue) {
		this.value.set(newValue);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("value", value.get())
				.toString();
	}
}
