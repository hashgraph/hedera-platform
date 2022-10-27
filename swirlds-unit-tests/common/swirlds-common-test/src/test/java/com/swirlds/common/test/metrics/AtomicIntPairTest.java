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

package com.swirlds.common.test.metrics;

import com.swirlds.common.metrics.atomic.AtomicIntPair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AtomicIntPairTest {

	@Test
	void testConstructor() {
		// when
		final AtomicIntPair pair = new AtomicIntPair();
		pair.accumulate(2, 3);
		pair.accumulate(5, 7);

		// then
		assertThat(pair.getLeft()).isEqualTo(2 + 5);
		assertThat(pair.getRight()).isEqualTo(3 + 7);
	}

	@Test
	void testConstructorWithAccumulators() {
		// when
		final AtomicIntPair pair = new AtomicIntPair(Math::subtractExact, Math::max);
		pair.accumulate(2, 3);
		pair.accumulate(5, 7);

		// then
		assertThat(pair.getLeft()).isEqualTo(-2 - 5);
		assertThat(pair.getRight()).isEqualTo(7);
	}

	@Test
	void testOverflow() {
		// given
		final AtomicIntPair pair1 = new AtomicIntPair();
		pair1.accumulate(Integer.MAX_VALUE, 0);
		final AtomicIntPair pair2 = new AtomicIntPair();
		pair2.accumulate(0, Integer.MAX_VALUE);
		final AtomicIntPair pair3 = new AtomicIntPair();
		pair3.accumulate(Integer.MIN_VALUE, 0);
		final AtomicIntPair pair4 = new AtomicIntPair();
		pair4.accumulate(0, Integer.MIN_VALUE);

		// when
		pair1.accumulate(1, 1);
		pair2.accumulate(1, 1);
		pair3.accumulate(-1, -1);
		pair4.accumulate(-1, -1);

		// then
		final int positiveOverflow = Integer.MAX_VALUE + 1;
		final int negativeOverflow = Integer.MIN_VALUE - 1;
		assertThat(pair1.getLeft()).isEqualTo(positiveOverflow);
		assertThat(pair1.getRight()).isEqualTo(1);
		assertThat(pair2.getLeft()).isEqualTo(1);
		assertThat(pair2.getRight()).isEqualTo(positiveOverflow);
		assertThat(pair3.getLeft()).isEqualTo(negativeOverflow);
		assertThat(pair3.getRight()).isEqualTo(-1);
		assertThat(pair4.getLeft()).isEqualTo(-1);
		assertThat(pair4.getRight()).isEqualTo(negativeOverflow);
	}

	@Test
	void testComputeDouble() {
		// given
		final AtomicIntPair pair = new AtomicIntPair();
		pair.accumulate(2, 3);

		// when
		final double result = pair.computeDouble(Math::subtractExact);

		// then
		assertThat(result).isEqualTo(2 - 3);
		assertThat(pair.getLeft()).isEqualTo(2);
		assertThat(pair.getRight()).isEqualTo(3);
	}

	@Test
	void testComputeDoubleAndReset() {
		// given
		final AtomicIntPair pair = new AtomicIntPair();
		pair.accumulate(2, 3);

		// when
		final double result = pair.computeDoubleAndReset(Math::subtractExact);

		// then
		assertThat(result).isEqualTo(2 - 3);
		assertThat(pair.getLeft()).isZero();
		assertThat(pair.getRight()).isZero();
	}

	@Test
	void testCompute() {
		// given
		final AtomicIntPair pair = new AtomicIntPair();
		pair.accumulate(2, 3);

		// when
		final Pair<Integer, Integer> result = pair.compute(Pair::of);

		// then
		assertThat(result).isEqualTo(Pair.of(2, 3));
		assertThat(pair.getLeft()).isEqualTo(2);
		assertThat(pair.getRight()).isEqualTo(3);
	}

	@Test
	void testComputeAndReset() {
		// given
		final AtomicIntPair pair = new AtomicIntPair();
		pair.accumulate(2, 3);

		// when
		final Pair<Integer, Integer> result = pair.computeAndReset(Pair::of);

		// then
		assertThat(result).isEqualTo(Pair.of(2, 3));
		assertThat(pair.getLeft()).isZero();
		assertThat(pair.getRight()).isZero();
	}
}
