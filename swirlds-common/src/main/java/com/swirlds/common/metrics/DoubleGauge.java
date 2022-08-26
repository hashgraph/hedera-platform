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

import com.swirlds.common.threading.AtomicDouble;

/**
 * A {@code DoubleGauge} stores a single {@code double} value.
 * <p>
 * Only the current value is stored, no history or distribution is kept.
 */
public class DoubleGauge extends Metric {

	private final AtomicDouble value = new AtomicDouble();

	/**
	 * Constructor of {@code DoubleGauge}
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
	public DoubleGauge(
			final String category,
			final String name,
			final String description,
			final String format) {
		this(category, name, description, format, 0.0);
	}

	/**
	 * Constructor of {@code DoubleGauge}
	 *
	 * @param category
	 * 		the kind of metric (metrics are grouped or filtered by this)
	 * @param name
	 * 		a short name for the metric
	 * @param description
	 * 		a one-sentence description of the metric
	 * @param format
	 * 		a string that can be passed to String.format() to format the metric
	 * @param initialValue
	 * 		the initial value of this {@code DoubleGauge}
	 * @throws IllegalArgumentException
	 * 		if one of the parameters is {@code null}
	 */
	public DoubleGauge(
			final String category,
			final String name,
			final String description,
			final String format,
			final double initialValue) {
		super(category, name, description, format);
		value.set(initialValue);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public Double getValue() {
		return get();
	}

	/**
	 * Get the current value
	 *
	 * @return the current value
	 */
	public double get() {
		return value.get();
	}

	/**
	 * Set the current value
	 *
	 * @param newValue
	 * 		the new value
	 */
	public void set(final double newValue) {
		this.value.set(newValue);
	}

}
