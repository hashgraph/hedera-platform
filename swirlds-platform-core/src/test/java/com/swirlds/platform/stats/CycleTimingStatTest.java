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
package com.swirlds.platform.stats;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.platform.stats.cycle.CycleDefinition;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CycleTimingStatTest {

    private static Stream<Arguments> validConstructorArgs() {
        return Stream.of(
                Arguments.of("stat1", true, List.of("1", "2", "3"), List.of("d1", "d2", "d3")),
                Arguments.of(
                        "stat1",
                        true,
                        List.of("1", "2", "3", "4"),
                        List.of("d1", "d2", "d3", "d4")),
                Arguments.of("stat1", true, List.of("1", "2"), List.of("d1", "d2")));
    }

    private static Stream<Arguments> invalidConstructorArgs() {
        return Stream.of(
                Arguments.of(
                        "stat1", false, List.of("1", "2", "3", "4"), List.of("d1", "d2", "d3")),
                Arguments.of("stat1", false, List.of("1", "2"), List.of("d1", "d2", "d3")));
    }

    @ParameterizedTest
    @MethodSource({"validConstructorArgs", "invalidConstructorArgs"})
    void testConstructor(
            final String name,
            final boolean validArgs,
            final List<String> detailedNames,
            final List<String> descList) {
        final Metrics metrics = mock(Metrics.class);
        final Runnable constructor =
                () ->
                        new CycleTimingStat(
                                metrics,
                                ChronoUnit.MICROS,
                                new CycleDefinition("cat", name, detailedNames, descList));
        if (validArgs) {
            assertDoesNotThrow(constructor::run);
        } else {
            assertThrows(IllegalArgumentException.class, constructor::run);
        }
    }
}
