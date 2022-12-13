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
package com.swirlds.platform.util.router;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.test.RandomUtils;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class BinaryRouterTests {

    @Test
    void testConstructor() {
        assertDoesNotThrow(
                () -> new BinaryRouter<Boolean>(b -> false, null, null, () -> false, () -> null),
                "Constructor should not throw on null consumers");

        assertThrows(
                IllegalArgumentException.class,
                () -> new BinaryRouter<Boolean>(null, null, null, () -> false, () -> null),
                "Constructor should throw on null test");

        assertThrows(
                IllegalArgumentException.class,
                () -> new BinaryRouter<Boolean>(b -> false, null, null, null, () -> null),
                "Constructor should throw on null continueSupplier");

        assertThrows(
                IllegalArgumentException.class,
                () -> new BinaryRouter<Boolean>(b -> false, null, null, () -> false, null),
                "Constructor should throw on null nextSupplier");
    }

    @Test
    void routeEventsTest() {
        final Random r = RandomUtils.getRandom();
        int numTrue = 0;
        int numFalse = 0;
        final List<Boolean> randomBooleans = new LinkedList<>();
        final double nullRatio = 0.1;

        for (int i = 0; i < 1_000; i++) {
            if (r.nextDouble() < nullRatio) {
                randomBooleans.add(null);
                continue;
            }
            final boolean b = r.nextBoolean();
            if (b) {
                numTrue++;
            } else {
                numFalse++;
            }
            randomBooleans.add(b);
        }

        final AtomicInteger trueCounter = new AtomicInteger();
        final AtomicInteger falseCounter = new AtomicInteger();

        final Iterator<Boolean> it = randomBooleans.iterator();

        final BinaryRouter<Boolean> router =
                new BinaryRouter<>(
                        b -> b,
                        b -> trueCounter.incrementAndGet(),
                        b -> falseCounter.incrementAndGet(),
                        it::hasNext,
                        it::next);

        router.route();

        assertEquals(numTrue, trueCounter.get(), "Incorrect number of true values");
        assertEquals(numFalse, falseCounter.get(), "Incorrect number of false values");
        assertEquals(numTrue, router.getNumPassed(), "Num passed is incorrect.");
        assertEquals(numFalse, router.getNumFailed(), "Num failed is incorrect.");
    }
}
