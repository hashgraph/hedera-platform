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
package com.swirlds.common.test.benchmark;

import com.swirlds.common.merkle.MerkleNode;
import java.util.Random;

/** Boiler plate logic for operations. */
public abstract class AbstractBenchmarkOperation<S extends MerkleNode, M extends BenchmarkMetadata>
        implements BenchmarkOperation<S, M> {

    private final double weight;

    private boolean abort;

    public AbstractBenchmarkOperation(final double weight) {
        this.weight = weight;
        abort = false;
    }

    /** {@inheritDoc} */
    @Override
    public double getWeight() {
        return weight;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return String.format("Operation %s (%.3f)", getName(), weight);
    }

    /**
     * Causes the operation to be aborted. Should only be called in {@link
     * #prepare(BenchmarkMetadata, Random)}.
     */
    protected void abort() {
        abort = true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean shouldAbort() {
        final boolean shouldAbort = abort;
        abort = false;
        return shouldAbort;
    }
}
