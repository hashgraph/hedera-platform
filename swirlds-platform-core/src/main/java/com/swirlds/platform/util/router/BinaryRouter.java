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

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Routes objects to one of two consumers based on a predicate test. */
public class BinaryRouter<T> {

    private final Predicate<T> test;
    private final Consumer<T> trueConsumer;
    private final Consumer<T> falseConsumer;
    private final BooleanSupplier continueSupplier;
    private final Supplier<T> nextSupplier;
    private long trueCounter;
    private long falseCounter;

    /**
     * @param test determines which consumer each object is routed to
     * @param trueConsumer consumer of objects that pass the {@code test}
     * @param falseConsumer consumer of objects that fail the {@code test}
     * @param continueSupplier determines if more objects should be retrieved and routed
     * @param nextSupplier supplied the next object to be routed
     */
    public BinaryRouter(
            final Predicate<T> test,
            final Consumer<T> trueConsumer,
            final Consumer<T> falseConsumer,
            final BooleanSupplier continueSupplier,
            final Supplier<T> nextSupplier) {
        throwArgNull(test, "test");
        throwArgNull(continueSupplier, "continueSupplier");
        throwArgNull(nextSupplier, "nextSupplier");
        this.test = test;
        final Consumer<T> noop = x -> {};
        this.trueConsumer = trueConsumer == null ? noop : trueConsumer;
        this.falseConsumer = falseConsumer == null ? noop : falseConsumer;
        this.continueSupplier = continueSupplier;
        this.nextSupplier = nextSupplier;
    }

    /**
     * Read all objects from the supplier and route them to the appropriate consumer. Null objects
     * are discarded.
     */
    public void route() {
        while (continueSupplier.getAsBoolean()) {
            final T t = nextSupplier.get();
            if (t == null) {
                continue;
            }
            if (test.test(t)) {
                trueCounter++;
                trueConsumer.accept(t);
            } else {
                falseCounter++;
                falseConsumer.accept(t);
            }
        }
    }

    /** Returns the number of objects sent to the {@code trueConsumer}. */
    public long getNumPassed() {
        return trueCounter;
    }

    /** Returns the number of objects sent to the {@code falseConsumer}. */
    public long getNumFailed() {
        return falseCounter;
    }
}
