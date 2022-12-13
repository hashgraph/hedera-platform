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
package com.swirlds.platform.test.eventhandling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.swirlds.platform.eventhandling.SignedStateEventsAndGenerations;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.test.framework.TestQualifierTags;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SignedStateEventsAndGenerationsTest {
    /**
     * A test created to simulate the situation where round generation number might decrease. This
     * can happen after a restart because non-consensus events are thrown away at restart.
     */
    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void roundGenerationsOutOfOrder() {
        final SignedStateEventsAndGenerations sseag =
                new SignedStateEventsAndGenerations(new StateSettings());
        for (long i = 1; i < 1000; i++) {
            long randomDiff = new Random().longs(1, -15, +15).findFirst().orElseThrow();
            final long roundGeneration = Math.max(1, (i * 10) + randomDiff);

            final EventImpl e1 = Mockito.mock(EventImpl.class);
            Mockito.when(e1.getRoundCreated()).thenReturn(i);
            Mockito.when(e1.getRoundReceived()).thenReturn(i + 2);
            Mockito.when(e1.getGeneration()).thenReturn(i * 10);

            sseag.addEvent(e1);
            sseag.addRoundGeneration(i, roundGeneration);

            System.out.printf("r:%4d g:%5d\n", i, roundGeneration);
            assertDoesNotThrow(sseag::expire, "Expire should never throw an exception");
        }
    }
}
