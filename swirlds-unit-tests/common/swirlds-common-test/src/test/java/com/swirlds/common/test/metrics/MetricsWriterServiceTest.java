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
package com.swirlds.common.test.metrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.MetricsWriterService;
import com.swirlds.common.system.NodeId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsWriterServiceTest {

    private MetricsWriterService service;

    @BeforeEach
    void setupService() {
        // given
        final Metrics metrics = mock(Metrics.class);
        final NodeId selfId = NodeId.createMain(0L);
        final ScheduledExecutorService executorService =
                Executors.newSingleThreadScheduledExecutor();
        service = new MetricsWriterService(metrics, selfId, executorService);
    }

    @AfterEach
    void cleanupService() throws InterruptedException {
        service.shutdown();
    }

    @Test
    void testRunState() throws InterruptedException {
        // initial state
        assertFalse(service.isRunning(), "Service should not run right after initialization");
        assertThrows(
                IllegalStateException.class,
                () -> service.shutdown(),
                "Trying to shutdown the service before it was started should throw an"
                        + " IllegalStateException");

        // when
        service.start();

        // then
        assertTrue(service.isRunning(), "Service should run");
        assertThrows(
                IllegalStateException.class,
                () -> service.start(),
                "Trying to start the service again should throw an IllegalStateException");

        // when
        final boolean shutdown = service.shutdown();

        // then
        assertTrue(shutdown, "Shutdown should have been successful");
        assertFalse(service.isRunning(), "Service should run");
        assertThrows(
                IllegalStateException.class,
                () -> service.start(),
                "Trying to start the service after shutdown should throw an IllegalStateException");
        assertTrue(service.shutdown(), "Trying to shutdown the service again should be a no-op");
    }
}
