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

package com.swirlds.platform.stats.atomic;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntBinaryOperator;
import java.util.function.ToDoubleBiFunction;

/**
 * Holds two integers that can be updated atomically
 */
public class AtomicIntPair {
	public static final int INT_BITS = 32;
	private final AtomicLong pair;
	private final IntBinaryOperator leftAccumulator;
	private final IntBinaryOperator rightAccumulator;

	/**
	 * Uses default accumulator method {@link Integer#sum(int, int)}
	 */
	public AtomicIntPair() {
		this(Integer::sum, Integer::sum);
	}

	/**
	 * @param leftAccumulator
	 * 		the method that will be used to calculate the new value for the left integer when
	 *        {@link #accumulate(int, int)} is called
	 * @param rightAccumulator
	 * 		the method that will be used to calculate the new value for the right integer when
	 *        {@link #accumulate(int, int)} is called
	 */
	public AtomicIntPair(final IntBinaryOperator leftAccumulator, final IntBinaryOperator rightAccumulator) {
		this.pair = new AtomicLong();
		this.leftAccumulator = leftAccumulator;
		this.rightAccumulator = rightAccumulator;
	}

	/**
	 * Update the integers with the provided values. The update will be done by the accumulator method provided in the
	 * constructor
	 *
	 * @param leftValue
	 * 		the value provided to the left integer
	 * @param rightValue
	 * 		the value provided to the left integer
	 */
	public void accumulate(final int leftValue, final int rightValue) {
		pair.accumulateAndGet(
				combine(leftValue, rightValue),
				this::update
		);
	}

	private long update(final long current, final long supplied) {
		return combine(
				leftAccumulator.applyAsInt(extractLeft(current), extractLeft(supplied)),
				rightAccumulator.applyAsInt(extractRight(current), extractRight(supplied))
		);
	}

	/**
	 * @return the current value of the left integer
	 */
	public int getLeft() {
		return extractLeft(pair.get());
	}

	/**
	 * @return the current value of the right integer
	 */
	public int getRight() {
		return extractRight(pair.get());
	}

	/**
	 * Compute a double value based on the input of the integer pair
	 *
	 * @param compute
	 * 		the method to compute the double
	 * @return the double computed
	 */
	public double computeDouble(final ToDoubleBiFunction<Integer, Integer> compute) {
		final long twoInts = pair.get();
		return compute.applyAsDouble(extractLeft(twoInts), extractRight(twoInts));
	}

	/**
	 * Same as {@link #computeDouble(ToDoubleBiFunction)} but also atomically resets the integers to the initial value
	 */
	public double computeDoubleAndReset(final ToDoubleBiFunction<Integer, Integer> compute) {
		final long twoInts = pair.getAndSet(0);
		return compute.applyAsDouble(extractLeft(twoInts), extractRight(twoInts));
	}

	/**
	 * Resets the integers to the initial value
	 */
	public void reset() {
		pair.set(0);
	}

	private static int extractLeft(final long pair) {
		return (int) (pair >> INT_BITS);
	}

	private static int extractRight(final long pair) {
		return (int) pair;
	}

	private static long combine(final int left, final int right) {
		return (((long) left) << INT_BITS) | (right & 0xffffffffL);
	}
}
