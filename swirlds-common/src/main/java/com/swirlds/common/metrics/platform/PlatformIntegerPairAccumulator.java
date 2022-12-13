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
package com.swirlds.common.metrics.platform;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.MetricConfig;
import com.swirlds.common.metrics.atomic.AtomicIntPair;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotValue;
import java.util.List;
import java.util.function.BiFunction;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Platform-implementation of {@link IntegerPairAccumulator} */
public class PlatformIntegerPairAccumulator<T> extends PlatformMetric
        implements IntegerPairAccumulator<T> {

    private final DataType dataType;
    private final AtomicIntPair container;
    private final BiFunction<Integer, Integer, T> resultFunction;

    public PlatformIntegerPairAccumulator(final Config<T> config) {
        super(config);
        this.dataType = MetricConfig.mapDataType(config.getType());
        this.container =
                new AtomicIntPair(config.getLeftAccumulator(), config.getRightAccumulator());
        this.resultFunction = config.getResultFunction();
    }

    /** {@inheritDoc} */
    @Override
    public DataType getDataType() {
        return dataType;
    }

    /** {@inheritDoc} */
    @Override
    public List<SnapshotValue> takeSnapshot() {
        return List.of(new SnapshotValue(VALUE, container.computeAndReset(resultFunction)));
    }

    /** {@inheritDoc} */
    @Override
    public T get() {
        return container.compute(resultFunction);
    }

    /** {@inheritDoc} */
    @Override
    public int getLeft() {
        return container.getLeft();
    }

    /** {@inheritDoc} */
    @Override
    public int getRight() {
        return container.getRight();
    }

    /** {@inheritDoc} */
    @Override
    public void update(final int leftValue, final int rightValue) {
        container.accumulate(leftValue, rightValue);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("value", get())
                .toString();
    }
}
