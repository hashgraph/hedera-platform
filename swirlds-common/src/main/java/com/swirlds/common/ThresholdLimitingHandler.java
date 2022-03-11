/*
 * (c) 2016-2022 Swirlds, Inc.
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

package com.swirlds.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Provides a simple reusable thresholding mechanism to effectively apply rate limiting on arbitrary events. This
 * implementation groups the events by the underlying {@link Class} of the supplied elements.
 *
 * @param <E>
 * 		the type of element to be rate limited
 */
public class ThresholdLimitingHandler<E> {

	/** the threshold at which to begin suppression */
	private final long threshold;

	/** the state of each threshold counter for each element type */
	private final Map<Class<E>, Long> state;

	/**
	 * Constructor that initializes the underlying threshold.
	 *
	 * @param threshold
	 * 		the threshold at which events are discarded
	 */
	public ThresholdLimitingHandler(final long threshold) {
		this.threshold = threshold;
		this.state = new ConcurrentHashMap<>();
	}

	/**
	 * Applies thresholding to a given {@code element} based on it's {@link Class} and invokes the {@code callback}
	 * method if the number of times we have seen this element is less than or equal to the threshold limit.
	 *
	 * @param element
	 * 		the item for which thresholding should be applied
	 * @param callback
	 * 		the {@link Consumer} to notify if the supplied element is within the threshold limits
	 * @throws IllegalArgumentException
	 * 		if the {@code element} parameter is {@code null}
	 */
	public void handle(final E element, final Consumer<E> callback) {
		if (element == null) {
			throw new IllegalArgumentException("The element argument may not be a null value");
		}

		final Class<E> elClass = resolveElementClass(element);

		final long counter = state.compute(elClass, (k, oldValue) -> {
			if (oldValue == null) {
				return 1L;
			}

			return oldValue + 1;
		});

		if (counter <= threshold && callback != null) {
			callback.accept(element);
		}
	}

	/**
	 * Returns the current counter for the given element. Uses the {@link Class} of the element to locate the counter.
	 *
	 * @param element
	 * 		the element for which to retrieve the counter
	 * @return the number of times we have seen elements with the same {@link Class} as the one provided
	 * @throws IllegalArgumentException
	 * 		if the {@code element} parameter is {@code null}
	 */
	public long getCurrentThreshold(final E element) {
		if (element == null) {
			throw new IllegalArgumentException("The element argument may not be a null value");
		}

		final Class<E> elClass = resolveElementClass(element);

		state.putIfAbsent(elClass, 0L);
		return state.get(elClass);
	}

	/**
	 * Clears all counters.
	 */
	public void reset() {
		state.clear();
	}

	/**
	 * Clears the counter for the provided element. Uses the {@link Class} of the element to locate the counter.
	 *
	 * @param element
	 * 		the element for which the counter should be cleared
	 */
	public void reset(final E element) {
		final Class<E> elClass = resolveElementClass(element);

		state.remove(elClass);
	}

	private Class<E> resolveElementClass(final E element) {
		@SuppressWarnings("unchecked") final Class<E> elClass = (element != null) ? (Class<E>) element.getClass() :
				null;

		return elClass;
	}
}
