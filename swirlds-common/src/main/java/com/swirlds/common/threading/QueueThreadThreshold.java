/*
 * (c) 2016-2021 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.threading;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Describes a numeric threshold on the number of elements in a {@link QueueThread}.
 */
public class QueueThreadThreshold {

	/**
	 * A method that returns true if conditions are met.
	 */
	private final Predicate<Integer> threshold;

	/**
	 * A method that is run when the threshold is met.
	 */
	private final Consumer<Integer> action;

	/**
	 * The minimum amount of time that must pass between the action being triggered another time.
	 */
	private final Duration minimumPeriod;

	/**
	 * The previous time that the action was triggered.
	 */
	private Instant previousTime;

	/**
	 * Create a new {@link QueueThread} threshold.
	 *
	 * @param threshold
	 * 		a method that decides if a threshold has been crossed
	 * @param action
	 * 		a method to call if the threshold is crossed
	 * @param minimumPeriod
	 * 		the minimum amount of time that should pass between triggering of the action
	 */
	public QueueThreadThreshold(
			final Predicate<Integer> threshold,
			final Consumer<Integer> action,
			final Duration minimumPeriod) {
		this.threshold = threshold;
		this.action = action;
		this.minimumPeriod = minimumPeriod;
		previousTime = Instant.ofEpochMilli(0);
	}

	/**
	 * Check a value and take action if it crosses a threshold.
	 *
	 * @param now
	 * 		the current time
	 * @param value
	 * 		the current number of elements in the {@link QueueThread}
	 */
	public void checkValue(final Instant now, final int value) {
		if (Duration.between(previousTime, now).toMillis() > minimumPeriod.toMillis() && threshold.test(value)) {
			action.accept(value);
			previousTime = now;
		}
	}
}
