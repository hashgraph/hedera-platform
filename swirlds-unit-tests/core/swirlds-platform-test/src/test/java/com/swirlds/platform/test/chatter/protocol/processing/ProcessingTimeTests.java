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
package com.swirlds.platform.test.chatter.protocol.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.PlatformMetricsFactory;
import com.swirlds.common.test.TestClock;
import com.swirlds.platform.chatter.protocol.processing.ProcessingTimeMessage;
import com.swirlds.platform.chatter.protocol.processing.ProcessingTimeSendReceive;
import com.swirlds.platform.chatter.protocol.processing.ProcessingTimes;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProcessingTimeTests {

    private final long peerId = 1;
    private final Duration procTimeInterval = Duration.ofSeconds(1);
    private final TestClock clock = new TestClock();
    private final ProcessingTimes procTimes =
            new ProcessingTimes(
                    new Metrics(
                            Executors.newSingleThreadScheduledExecutor(),
                            new PlatformMetricsFactory()));

    @BeforeEach
    void reset() {
        procTimes.clear();
    }

    @Test
    void testHandle() {
        final ProcessingTimeSendReceive procTimeSendReceive =
                new ProcessingTimeSendReceive(peerId, procTimeInterval, procTimes, clock::nanoTime);
        assertNull(
                procTimes.getPeerProcessingTime(peerId),
                "no processing time for this peer should be available");

        final long processingTime = 1000;
        procTimeSendReceive.handleMessage(new ProcessingTimeMessage(processingTime));

        assertNotNull(
                procTimes.getPeerProcessingTime(peerId),
                "processing time for this peer should be available");
        assertEquals(
                processingTime,
                procTimes.getPeerProcessingTime(peerId),
                "unexpected processing time for peer");
    }

    @Test
    void testGetMessage() {
        final ProcessingTimeSendReceive procTimeSendReceive =
                new ProcessingTimeSendReceive(peerId, procTimeInterval, procTimes, clock::nanoTime);

        clock.advanceClock(Duration.ofSeconds(2));

        ProcessingTimeMessage message = (ProcessingTimeMessage) procTimeSendReceive.getMessage();
        assertNull(message, "no message should be sent if we have no processing time available");

        final Duration selfProcessingTime = Duration.ofNanos(500);
        procTimes.recordSelfProcessingTime(selfProcessingTime);
        message = (ProcessingTimeMessage) procTimeSendReceive.getMessage();
        assertNotNull(
                message,
                "message should be sent when there is a value to send and the interval has"
                        + " elapsed");

        clock.advanceClock(Duration.ofMillis(500));
        message = (ProcessingTimeMessage) procTimeSendReceive.getMessage();
        assertNull(
                message,
                "message should NOT be sent when the interval since the last sent message has not"
                        + " elapsed");
    }
}
