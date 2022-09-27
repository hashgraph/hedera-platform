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
import com.swirlds.common.statistics.StatsSpeedometer;
import com.swirlds.common.utility.CommonUtils;

/**
 * This class measures how many times per second the cycle() method is called. It is recalculated every
 * period, where its period is 0.1 seconds by default. If instantiated with gamma=0.9, then half the
 * weighting comes from the last 7 periods. If 0.99 it's 70 periods, 0.999 is 700, etc.
 * <p>
 * The timer starts at instantiation, and can be reset with the reset() method.
 */
public class SpeedometerMetric extends AbstractDistributionMetric {

	private final Clock clock;

	@SuppressWarnings("removal")
	private StatsSpeedometer speedometer;

	/**
	 * Constructor of {@code SpeedometerMetric}
	 *
	 * @param category
	 * 		the kind of metric (metrics are grouped or filtered by this)
	 * @param name
	 * 		a short name for the metric
	 * @param description
	 * 		a one-sentence description of the metric
	 * @param format
	 * 		a string that can be passed to String.format() to format the metric
	 * @param halfLife
	 * 		the halfLife of the speedometer
	 * @throws IllegalArgumentException if one of the parameters is {@code null}
	 */
	@SuppressWarnings("removal")
	public SpeedometerMetric(
			final String category,
			final String name,
			final String description,
			final String format,
			final double halfLife) {
		this(category, name, description, format, halfLife, Clock.DEFAULT);
	}

	/**
	 * Constructor of {@code SpeedometerMetric} with a half-life as defined in {@link SettingsCommon#halfLife}.
	 *
	 * @param category
	 * 		the kind of metric (metrics are grouped or filtered by this)
	 * @param name
	 * 		a short name for the metric
	 * @param description
	 * 		a one-sentence description of the metric
	 * @param format
	 * 		a string that can be passed to String.format() to format the metric
	 * @throws IllegalArgumentException if one of the parameters is {@code null}
	 */
	@SuppressWarnings("removal")
	public SpeedometerMetric(
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
	public SpeedometerMetric(
			final String category,
			final String name,
			final String description,
			final String format,
			final double halfLife,
			final Clock clock) {
		super(category, name, description, format, halfLife);
		this.clock = CommonUtils.throwArgNull(clock, "clock");
		this.speedometer = new StatsSpeedometer(halfLife, clock);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public void init() {
		speedometer = new StatsSpeedometer(halfLife, clock);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public StatsBuffered getStatsBuffered() {
		return speedometer;
	}

	/**
	 * calling update(N) is equivalent to calling cycle() N times almost simultaneously. Calling cycle() is
	 * equivalent to calling update(1). Calling update(0) will update the estimate of the cycles per second
	 * by looking at the current time (to update the seconds) without incrementing the count of the cycles.
	 * So calling update(0) repeatedly with no calls to cycle() will cause the cycles per second estimate to
	 * go asymptotic to zero.
	 * <p>
	 * The speedometer initially keeps a simple, uniformly-weighted average of the number of calls to
	 * cycle() per second since the start of the run. Over time, that makes each new call to cycle() have
	 * less weight (because there are more of them). Eventually, the weight of a new call drops below the
	 * weight it would have under the exponentially-weighted average. At that point, it switches to the
	 * exponentially-weighted average.
	 *
	 * @param numCycles
	 * 		number of cycles to record
	 */
	public void update(final double numCycles) {
		speedometer.update(numCycles);
	}

	/**
	 * This is the method to call repeatedly. The average calls per second will be calculated.
	 */
	public void cycle() {
		update(1);
	}

	/**
	 * Get the average number of times per second the cycle() method was called. This is an
	 * exponentially-weighted average of recent timings.
	 *
	 * @return the estimated number of calls to cycle() per second, recently
	 */
	@Override
	public double get() {
		return speedometer.getCyclesPerSecond();
	}
}
