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
package com.swirlds.common.metrics;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

import java.util.EnumSet;

/**
 * A helper interface that contains functionality common to all metrics, that contain a single value
 * {@code long}.
 */
public interface BaseLongMetric extends Metric {

    /** {@inheritDoc} */
    @Override
    default DataType getDataType() {
        return DataType.INT;
    }

    /** {@inheritDoc} */
    @Override
    default EnumSet<ValueType> getValueTypes() {
        return EnumSet.of(VALUE);
    }

    /** {@inheritDoc} */
    @Override
    default Long get(final ValueType valueType) {
        if (valueType == VALUE) {
            return get();
        }
        throw new IllegalArgumentException("Unsupported ValueType: " + valueType);
    }

    /**
     * Get the current value
     *
     * @return the current value
     */
    long get();
}
