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

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.MetricConfig;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotValue;
import java.util.List;
import java.util.function.Supplier;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Platform-implementation of {@link FunctionGauge} */
public class PlatformFunctionGauge<T> extends PlatformMetric implements FunctionGauge<T> {

    private final DataType dataType;
    private final Supplier<T> supplier;

    public PlatformFunctionGauge(final FunctionGauge.Config<T> config) {
        super(config);
        this.dataType = MetricConfig.mapDataType(config.getType());
        this.supplier = config.getSupplier();
    }

    /** {@inheritDoc} */
    @Override
    public DataType getDataType() {
        return dataType;
    }

    /** {@inheritDoc} */
    @Override
    public List<SnapshotValue> takeSnapshot() {
        return List.of(new SnapshotValue(VALUE, get()));
    }

    /** {@inheritDoc} */
    @Override
    public T get() {
        return supplier.get();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("value", supplier.get())
                .toString();
    }
}
