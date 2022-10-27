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

import com.swirlds.common.metrics.IntegerAccumulator;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotValue;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.List;
import java.util.function.IntBinaryOperator;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

/**
 * Platform-implementation of {@link IntegerAccumulator}
 */
public class PlatformIntegerAccumulator extends PlatformMetric implements IntegerAccumulator {

	private final java.util.concurrent.atomic.LongAccumulator container;
	private final IntBinaryOperator accumulator;
	private final int initialValue;

	public PlatformIntegerAccumulator(final IntegerAccumulator.Config config) {
		super(config);
		this.accumulator = config.getAccumulator();
		this.container = new java.util.concurrent.atomic.LongAccumulator(
				(op1, op2) -> accumulator.applyAsInt((int) op1, (int) op2),
				config.getInitialValue()
		);
		this.initialValue = config.getInitialValue();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getInitialValue() {
		return initialValue;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<SnapshotValue> takeSnapshot() {
		return List.of(new SnapshotValue(VALUE, (int) container.getThenReset()));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int get() {
		return (int) container.get();
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public void update(final int other) {
		container.accumulate(other);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("initialValue", initialValue)
				.append("value", get())
				.toString();
	}
}
