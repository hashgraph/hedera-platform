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

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.statistics.StatsBuffered;
import com.swirlds.common.statistics.StatsRunningAverage;
import com.swirlds.common.utility.CommonUtils;

/**
 * This class maintains a running average of some numeric value. It is exponentially weighted in time, with
 * a given half life. If it is always given the same value, then that value will be the average, regardless
 * of the timing.
 */
public class RunningAverageMetric extends Metric {

	private final double halfLife;
	private final Clock clock;

	@SuppressWarnings("removal")
	private StatsRunningAverage runningAverage;

	/**
	 * Constructor of {@code RunningAverageMetric}
	 *
	 * @param category
	 * 		the kind of metric (stats are grouped or filtered by this)
	 * @param name
	 * 		a short name for the statistic
	 * @param description
	 * 		a one-sentence description of the statistic
	 * @param format
	 * 		a string that can be passed to String.format() to format the statistic
	 * @param halfLife
	 * 		the halfLife of the running average
	 * @throws IllegalArgumentException if one of the parameters is {@code null}
	 */
	@SuppressWarnings("removal")
	public RunningAverageMetric(
			final String category,
			final String name,
			final String description,
			final String format,
			final double halfLife) {
		this(category, name, description, format, halfLife, Clock.DEFAULT);
	}

	/**
	 * Constructor of {@code RunningAverageMetric} with a half-life as defined in {@link SettingsCommon#halfLife}.
	 *
	 * @param category
	 * 		the kind of metric (stats are grouped or filtered by this)
	 * @param name
	 * 		a short name for the statistic
	 * @param description
	 * 		a one-sentence description of the statistic
	 * @param format
	 * 		a string that can be passed to String.format() to format the statistic
	 * @throws IllegalArgumentException if one of the parameters is {@code null}
	 */
	@SuppressWarnings("removal")
	public RunningAverageMetric(
			final String category,
			final String name,
			final String description,
			final String format) {
		this(category, name, description, format, SettingsCommon.halfLife, Clock.DEFAULT);
	}

	/**
	 * This constructor should only be used for testing.
	 *
	 * @deprecated This constructor should only be used for testing and will become non-public at some point.
	 */
	@SuppressWarnings("removal")
	@Deprecated (forRemoval = true)
	public RunningAverageMetric(
			final String category,
			final String name,
			final String description,
			final String format,
			final double halfLife,
			final Clock clock) {
		super(category, name, description, format);
		this.halfLife = halfLife;
		this.clock = CommonUtils.throwArgNull(clock, "clock");
		this.runningAverage = new StatsRunningAverage(halfLife, clock);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public void init() {
		runningAverage = new StatsRunningAverage(halfLife, clock);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void reset() {
		runningAverage.reset(halfLife);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public Object getValue() {
		return getWeightedMean();
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public StatsBuffered getStatsBuffered() {
		return runningAverage;
	}

	/**
	 * Incorporate "value" into the running average. If it is the same on every call, then the average will
	 * equal it, no matter how those calls are timed. If it has various values on various calls, then the
	 * running average will weight the more recent ones more heavily, with a half life of halfLife seconds,
	 * where halfLife was passed in when this object was instantiated.
	 * <p>
	 * If this is called repeatedly with a value of X over a long period, then suddenly all calls start
	 * having a value of Y, then after halflife seconds, the average will have moved halfway from X to Y,
	 * regardless of how often update was called, as long as it is called at least once at the end of that
	 * period.
	 *
	 * @param value
	 * 		the value to incorporate into the running average
	 */
	public void recordValue(final double value) {
		runningAverage.recordValue(value);
	}

	/**
	 * Get the average of recent calls to recordValue(). This is an exponentially-weighted average of recent
	 * calls, with the weighting by time, not by number of calls to recordValue().
	 *
	 * @return the running average as of the last time recordValue was called
	 */
	public double getWeightedMean() {
		return runningAverage.getWeightedMean();
	}
}
