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
package com.swirlds.platform.test.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.sync.SyncThrottle;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SyncThrottleTests {

    private static Stream<Arguments> shouldThrottleArgs() {
        return Stream.of(
                Arguments.of(4, 1.5, 0, 0, true),
                Arguments.of(4, 1.5, 5, 5, true),
                Arguments.of(4, 1.5, 6, 5, false),
                Arguments.of(4, 1.5, 5, 6, false),
                Arguments.of(10, 1.5, 14, 14, true),
                Arguments.of(10, 1.5, 15, 14, false),
                Arguments.of(10, 1.5, 14, 15, false),
                Arguments.of(10, 2, 19, 19, true),
                Arguments.of(10, 2, 20, 19, false),
                Arguments.of(10, 2, 19, 20, false));
    }

    @ParameterizedTest
    @MethodSource("shouldThrottleArgs")
    void testShouldThrottle(
            final int numNodes,
            final double throttle7Threshold,
            final int numRec,
            final int numSent,
            final boolean shouldThrottle) {
        SettingsProvider settings = mock(SettingsProvider.class);
        when(settings.getThrottle7Threshold()).thenReturn(throttle7Threshold);
        SyncThrottle throttle = new SyncThrottle(numNodes, settings);
        assertEquals(
                shouldThrottle,
                throttle.shouldThrottle(numRec, numSent),
                "shouldThrottle() should match the expected outcome for the parameters given.");
    }
}
