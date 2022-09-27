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

package com.swirlds.common.metrics;

import com.swirlds.common.utility.CommonUtils;

import java.util.function.Supplier;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

/**
 * A {@code FunctionGauge} maintains a single value.
 *
 * Unlike the other gauges, the value of a {@code FunctionGauge} is not explicitly set. Instead
 * a {@link java.util.function.Supplier} has to be specified, which reads the current value of this gauge.
 *
 * Only the current value is stored, no history or distribution is kept.
 *
 * @param <T> the type of the contained value
 */
public class FunctionGauge<T> extends AbstractGauge<T> {

	private final Supplier<T> supplier;

	/**
	 * Constructor of {@code FunctionGauge}
	 *
	 * @param category
	 * 		the kind of metric (metrics are grouped or filtered by this)
	 * @param name
	 * 		a short name for the metric
	 * @param description
	 * 		a one-sentence description of the metric
	 * @param format
	 * 		a string that can be passed to String.format() to format the metric
	 * @param supplier
	 * 		the {@code Supplier} of the value of this {@code Gauge}
	 * @throws IllegalArgumentException
	 * 		if {@code category}, {@code name}, {@code description} or {@code format} are {@code null}
	 */
	public FunctionGauge(
			final String category,
			final String name,
			final String description,
			final String format,
			final Supplier<T> supplier) {
		super(category, name, description, format);
		this.supplier = CommonUtils.throwArgNull(supplier, "supplier");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T get(final ValueType valueType) {
		if (valueType == VALUE) {
			return get();
		}
		throw new IllegalArgumentException("Unsupported ValueType");
	}

	/**
	 * Get the current value
	 *
	 * @return the current value
	 */
	public T get() {
		return supplier.get();
	}
}
