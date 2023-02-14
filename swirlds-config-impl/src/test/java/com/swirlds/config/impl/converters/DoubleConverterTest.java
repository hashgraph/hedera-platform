/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.config.impl.converters;

import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DoubleConverterTest {

    @Test
    public void convertNull() {
        // given
        final DoubleConverter converter = new DoubleConverter();

        // then
        Assertions.assertThrows(
                NullPointerException.class,
                () -> converter.convert(null),
                "Null values must always throw a NPE");
    }

    @ParameterizedTest
    @MethodSource("provideConversionChecks")
    public void conversionCheck(final String rawValue, final double expectedValue) {
        // given
        final DoubleConverter converter = new DoubleConverter();

        // when
        final double value = converter.convert(rawValue);

        // then
        Assertions.assertEquals(expectedValue, value, "All valid double values must be supported");
    }

    @Test
    public void convertInvalid() {
        // given
        final DoubleConverter converter = new DoubleConverter();
        final String rawValue = "Hello";

        // then
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> converter.convert(rawValue),
                "Only valid int values must be supported");
    }

    private static Stream<Arguments> provideConversionChecks() {
        return Stream.of(
                Arguments.of("-7.5", -7.5d),
                Arguments.of("2.1", 2.1d),
                Arguments.of("0", 0),
                Arguments.of(Double.MAX_VALUE + "", Double.MAX_VALUE),
                Arguments.of(Double.MIN_VALUE + "", Double.MIN_VALUE),
                Arguments.of(Double.NaN + "", Double.NaN),
                Arguments.of(Double.NEGATIVE_INFINITY + "", Double.NEGATIVE_INFINITY),
                Arguments.of(Double.POSITIVE_INFINITY + "", Double.POSITIVE_INFINITY));
    }
}
