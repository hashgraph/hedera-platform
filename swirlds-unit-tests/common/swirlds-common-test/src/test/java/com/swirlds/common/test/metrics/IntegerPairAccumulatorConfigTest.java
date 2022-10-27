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

import com.swirlds.common.metrics.IntegerPairAccumulator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;
import java.util.function.IntBinaryOperator;

import static com.swirlds.common.metrics.IntegerPairAccumulator.AVERAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class IntegerPairAccumulatorConfigTest {

	private static final String DEFAULT_FORMAT = "%s";

	private static final String CATEGORY = "CaTeGoRy";
	private static final String NAME = "NaMe";
	private static final String DESCRIPTION = "DeScRiPtIoN";
	private static final String UNIT = "UnIt";
	private static final String FORMAT = "FoRmAt";

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("Constructor should store values")
	void testConstructor() {
		// when
		final BiFunction<Integer, Integer, Integer> resultFunction = mock(BiFunction.class);
		final IntegerPairAccumulator.Config<Integer> config = new IntegerPairAccumulator.Config<>(CATEGORY, NAME, resultFunction);

		// then
		assertThat(config.getCategory()).isEqualTo(CATEGORY);
		assertThat(config.getName()).isEqualTo(NAME);
		assertThat(config.getDescription()).isEqualTo(NAME);
		assertThat(config.getUnit()).isEmpty();
		assertThat(config.getFormat()).isEqualTo(DEFAULT_FORMAT);
		assertThat(config.getLeftAccumulator().applyAsInt(2, 3)).isEqualTo(2 + 3);
		assertThat(config.getRightAccumulator().applyAsInt(5, 7)).isEqualTo(5 + 7);
		assertThat(config.getResultFunction()).isEqualTo(resultFunction);
	}

	@Test
	@DisplayName("Constructor should throw IAE when passing illegal parameters")
	void testConstructorWithIllegalParameter() {
		assertThatThrownBy(() -> new IntegerPairAccumulator.Config<>(null, NAME, AVERAGE)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new IntegerPairAccumulator.Config<>("", NAME, AVERAGE)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new IntegerPairAccumulator.Config<>(" \t\n", NAME, AVERAGE)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new IntegerPairAccumulator.Config<>(CATEGORY, null, AVERAGE)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new IntegerPairAccumulator.Config<>(CATEGORY, "", AVERAGE)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new IntegerPairAccumulator.Config<>(CATEGORY, " \t\n", AVERAGE)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new IntegerPairAccumulator.Config<>(CATEGORY, CATEGORY, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@SuppressWarnings("unchecked")
	@Test
	void testSetters() {
		// given
		final IntBinaryOperator leftAccumulator = mock(IntBinaryOperator.class);
		final IntBinaryOperator rightAccumulator = mock(IntBinaryOperator.class);
		final BiFunction<Integer, Integer, Integer> resultFunction = mock(BiFunction.class);
		final IntegerPairAccumulator.Config<Integer> config = new IntegerPairAccumulator.Config<>(CATEGORY, NAME, resultFunction);

		// when
		final IntegerPairAccumulator.Config<Integer> result = config
				.withDescription(DESCRIPTION)
				.withUnit(UNIT)
				.withFormat(FORMAT)
				.withLeftAccumulator(leftAccumulator)
				.withRightAccumulator(rightAccumulator);

		// then
		assertThat(config.getCategory()).isEqualTo(CATEGORY);
		assertThat(config.getName()).isEqualTo(NAME);
		assertThat(config.getDescription()).isEqualTo(NAME);
		assertThat(config.getUnit()).isEmpty();
		assertThat(config.getFormat()).isEqualTo(DEFAULT_FORMAT);
		assertThat(config.getLeftAccumulator().applyAsInt(2, 3)).isEqualTo(2 + 3);
		assertThat(config.getRightAccumulator().applyAsInt(5, 7)).isEqualTo(5 + 7);
		assertThat(config.getResultFunction()).isEqualTo(resultFunction);

		assertThat(result.getCategory()).isEqualTo(CATEGORY);
		assertThat(result.getName()).isEqualTo(NAME);
		assertThat(result.getDescription()).isEqualTo(DESCRIPTION);
		assertThat(result.getUnit()).isEqualTo(UNIT);
		assertThat(result.getFormat()).isEqualTo(FORMAT);
		assertThat(result.getLeftAccumulator()).isEqualTo(leftAccumulator);
		assertThat(result.getRightAccumulator()).isEqualTo(rightAccumulator);
		assertThat(result.getResultFunction()).isEqualTo(resultFunction);
	}

	@SuppressWarnings("unchecked")
	@Test
	void testSettersWithIllegalParameters() {
		// given
		final BiFunction<Integer, Integer, Integer> resultFunction = mock(BiFunction.class);
		final IntegerPairAccumulator.Config<Integer> config = new IntegerPairAccumulator.Config<>(CATEGORY, NAME, resultFunction);
		final String longDescription =  DESCRIPTION.repeat(50);

		// then
		assertThatThrownBy(() -> config.withDescription(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> config.withDescription("")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> config.withDescription(" \t\n")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> config.withDescription(longDescription)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> config.withUnit(null)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> config.withFormat(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> config.withFormat("")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> config.withFormat(" \t\n")).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> config.withLeftAccumulator(null)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> config.withRightAccumulator(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@SuppressWarnings("unchecked")
	@Test
	void testToString() {
		// given
		final BiFunction<Integer, Integer, Integer> resultFunction = mock(BiFunction.class);
		final IntegerPairAccumulator.Config<Integer> config = new IntegerPairAccumulator.Config<>(CATEGORY, NAME, resultFunction)
				.withDescription(DESCRIPTION).withUnit(UNIT).withFormat(FORMAT);

		// then
		assertThat(config.toString()).contains(CATEGORY, NAME, DESCRIPTION, UNIT, FORMAT);
	}
}
