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
package com.swirlds.platform.reconnect.emergency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.state.EmergencyRecoveryFile;
import com.swirlds.platform.state.signed.SignedStateManager;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class EmergencyReconnectProtocolTests {

    private static Stream<Arguments> initiateParams() {
        return Stream.of(
                Arguments.of(true, true, true),
                Arguments.of(false, true, false),
                Arguments.of(true, false, false),
                Arguments.of(false, false, false));
    }

    @DisplayName("Test the conditions under which the protocol should and should not be initiated")
    @ParameterizedTest
    @MethodSource("initiateParams")
    void shouldInitiateTest(
            final boolean hasFile, final boolean getsPermit, final boolean shouldInitiate) {
        final AtomicReference<EmergencyRecoveryFile> file = new AtomicReference<>();
        if (hasFile) {
            file.set(new EmergencyRecoveryFile(1L, RandomUtils.randomHash()));
        }

        final ReconnectController reconnectController = mock(ReconnectController.class);
        when(reconnectController.acquireLearnerPermit()).thenReturn(getsPermit);

        final EmergencyReconnectProtocol protocol =
                new EmergencyReconnectProtocol(
                        new NodeId(false, 1L),
                        file,
                        mock(ReconnectThrottle.class),
                        mock(SignedStateManager.class),
                        100,
                        mock(ReconnectMetrics.class),
                        reconnectController,
                        mock(Crypto.class));

        assertEquals(shouldInitiate, protocol.shouldInitiate(), "unexpected initiation result");
    }
}
