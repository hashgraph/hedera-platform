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
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import static com.swirlds.common.metrics.Metric.ValueType.COUNTER;
import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

/**
 * A {@code Counter} can be used to count events and similar things.
 *
 * A {@code Counter} can be initialized in one of two modes: {@link Mode#INCREASE_ONLY} and
 * {@link  Mode#INCREASE_AND_DECREASE}. In {@code INCREASE_ONLY}-mode, the value of the {@code Counter}
 * can only increase, and therefore it can never become negative. In {@code INCREASE_AND_DECREASE}-mode,
 * the value can increase and decrease and also become negative
 */
public class Counter extends Metric {

	private static final String INCREASE_ONLY_ERROR_MESSAGE = "The value of a this Counter can only be increased";

	/**
	 * Possible modes of a {@code Counter}
	 *
	 * A {@code Counter} can be strictly increasing only, or it can also be allowed to decrease
	 */
	public enum Mode { INCREASE_ONLY, INCREASE_AND_DECREASE }

	private final LongAdder adder = new LongAdder();
	private final boolean increaseOnly;

	/**
	 * Constructor of {@code Counter}. The mode of the {@code Counter} will be {@link Mode#INCREASE_ONLY}.
	 *
	 * @param category
	 * 		the kind of metric (metrics are grouped or filtered by this)
	 * @param name
	 * 		a short name for the metric
	 * @param description
	 * 		a one-sentence description of the metric
	 * @throws IllegalArgumentException
	 * 		if one of the parameters is {@code null}
	 */
	public Counter(final String category, final String name, final String description) {
		this(category, name, description, Mode.INCREASE_ONLY);
	}

	/**
	 * Constructor of {@code Counter}
	 *
	 * @param category
	 * 		the kind of metric (metrics are grouped or filtered by this)
	 * @param name
	 * 		a short name for the metric
	 * @param description
	 * 		a one-sentence description of the metric
	 * @param mode
	 * 		the mode of this {@code Counter}
	 * @throws IllegalArgumentException
	 * 		if one of the parameters is {@code null}
	 */
	public Counter(final String category, final String name, final String description, final Mode mode) {
		super(category, name, description, "%d");
		increaseOnly = CommonUtils.throwArgNull(mode, "mode") == Mode.INCREASE_ONLY;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<ValueType> getValueTypes() {
		return increaseOnly? Metric.COUNTER_TYPE : Metric.VALUE_TYPE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Long get(final ValueType valueType) {
		CommonUtils.throwArgNull(valueType, "valueType");
		if ((valueType == COUNTER) || (valueType == VALUE)) {
			return get();
		}
		throw new IllegalArgumentException("Unsupported ValueType");
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public List<Pair<ValueType, Object>> takeSnapshot() {
		return List.of(Pair.of(increaseOnly? COUNTER : VALUE, get()));
	}

	/**
	 * Return the current value of the {@code Counter}
	 *
	 * @return the current value
	 */
	public long get() {
		return adder.sum();
	}

	/**
	 * Return the {@code mode} of this {@code Counter}
	 *
	 * @return the mode of this counter
	 */
	public Mode getMode() {
		return increaseOnly? Mode.INCREASE_ONLY : Mode.INCREASE_AND_DECREASE;
	}

	/**
	 * Add a value to the {@code Counter}.
	 * <p>
	 * The value of a {@code Counter} can only increase, thus only non-negative numbers can be added.
	 *
	 * @param value
	 * 		the value that needs to be added
	 * @throws IllegalArgumentException
	 * 		if this {@code Counter} is in {@code INCREASE_ONLY}-mode and {@code value <= 0}
	 */
	public void add(final long value) {
		if (increaseOnly && value <= 0) {
			throw new IllegalArgumentException(INCREASE_ONLY_ERROR_MESSAGE);
		}
		adder.add(value);
	}

	/**
	 * Subtract a value from the {@code Counter}.
	 *
	 * @param value
	 * 		the value that needs to be subtracted
	 * @throws UnsupportedOperationException
	 * 		if this {@code Counter} is in {@code INCREASE_ONLY}-mode
	 */
	public void subtract(final long value) {
		if (increaseOnly) {
			throw new UnsupportedOperationException(INCREASE_ONLY_ERROR_MESSAGE);
		}
		adder.add(-value);
	}

	/**
	 * Increase the {@code Counter} by {@code 1}.
	 */
	public void increment() {
		adder.increment();
	}

	/**
	 * Decrease the {@code Counter} by {@code 1}.
	 *
	 * @throws UnsupportedOperationException
	 * 		if this {@code Counter} is in {@code INCREASE_ONLY}-mode
	 */
	public void decrement() {
		if (increaseOnly) {
			throw new UnsupportedOperationException(INCREASE_ONLY_ERROR_MESSAGE);
		}
		adder.decrement();
	}

}
