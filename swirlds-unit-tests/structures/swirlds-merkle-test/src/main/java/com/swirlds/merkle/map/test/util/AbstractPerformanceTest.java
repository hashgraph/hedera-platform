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
package com.swirlds.merkle.map.test.util;

import java.util.Arrays;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractPerformanceTest {

    private static final Logger LOGGER = LogManager.getLogger();

    protected void measure(
            final Consumer<Void> methodToMeasure,
            final Consumer<Void> prepareMethod,
            final int numberOfTimesToExecute,
            final String measureName) {
        if (numberOfTimesToExecute < 3) {
            throw new IllegalArgumentException(
                    "Number of times to execute must be at least 3: " + numberOfTimesToExecute);
        }

        long start;
        long end;
        final long[] runningTimes = new long[numberOfTimesToExecute];
        for (int index = 0; index < numberOfTimesToExecute; index++) {
            prepareMethod.accept(null);
            start = System.currentTimeMillis();
            methodToMeasure.accept(null);
            end = System.currentTimeMillis();
            runningTimes[index] = end - start;
        }

        Arrays.sort(runningTimes);
        long sum = 0;
        final long limit = numberOfTimesToExecute - 1;
        for (int index = 1; index < limit; index++) {
            sum += runningTimes[index];
        }

        final long average = sum / (numberOfTimesToExecute - 2);
        LOGGER.info(
                "The test %s took on average %d, with min %d and max %d",
                measureName, average, runningTimes[1], runningTimes[runningTimes.length - 2]);
    }
}
