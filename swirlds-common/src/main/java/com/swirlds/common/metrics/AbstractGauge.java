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

import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

/**
 * An abstract class, that contains functionality shared by all gauges.
 */
abstract class AbstractGauge<T> extends Metric {

	/**
	 * Constructor of {@code SingleValueMetric}
	 *
	 * @param category
	 * 		the kind of metric (metrics are grouped or filtered by this)
	 * @param name
	 * 		a short name for the metric
	 * @param description
	 * 		a one-sentence description of the metric
	 * @param format
	 * 		a string that can be passed to String.format() to format the metric
	 * @throws IllegalArgumentException
	 * 		if one of the parameters is {@code null}
	 */
	protected AbstractGauge(final String category, final String name, final String description, final String format) {
		super(category, name, description, format);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<ValueType> getValueTypes() {
		return Metric.VALUE_TYPE;
	}

	/**
	 * Returns the value of this {@code Metric}.
	 *
	 * @return the current value
	 */
	@Override
	public abstract T get(final ValueType valueType);

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public List<Pair<ValueType, Object>> takeSnapshot() {
		return List.of(Pair.of(VALUE, get(VALUE)));
	}
}
