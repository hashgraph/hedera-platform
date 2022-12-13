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
package com.swirlds.jasperdb.files;

import com.swirlds.jasperdb.ExampleLongKeyFixedSize;
import com.swirlds.jasperdb.ExampleLongKeyVariableSize;
import com.swirlds.jasperdb.ExampleLongKeyVariableSizeBuilder;
import com.swirlds.jasperdb.ExampleLongLongKeyFixedSize;
import com.swirlds.jasperdb.ExampleLongLongKeyFixedSizeBuilder;
import com.swirlds.jasperdb.ExampleLongLongKeyVariableSize;
import com.swirlds.jasperdb.ExampleLongLongKeyVariableSizeBuilder;
import com.swirlds.jasperdb.SelfSerializableSupplier;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualLongKey;

/**
 * Supports parameterized testing of {@link com.swirlds.jasperdb.VirtualDataSourceJasperDB} with
 * both fixed- and variable-size data.
 *
 * <p>Used with JUnit's {@link org.junit.jupiter.params.provider.EnumSource} annotation.
 */
public enum FilesTestType {
    /** Parameterizes a test with fixed-size data. */
    fixed(new ExampleFixedSizeDataSerializer(), new ExampleLongKeyFixedSize.Serializer()),
    /** Parameterizes a test with fixed-size data and a complex key. */
    fixedComplexKey(
            new ExampleFixedSizeDataSerializer(), new ExampleLongLongKeyFixedSize.Serializer()),
    /** Parameterizes a test with variable-size data. */
    variable(new ExampleVariableSizeDataSerializer(), new ExampleLongKeyVariableSize.Serializer()),
    /** Parameterizes a test with variable-size data and a complex key. */
    variableComplexKey(
            new ExampleVariableSizeDataSerializer(),
            new ExampleLongLongKeyVariableSize.Serializer());

    /** used by files package level tests */
    public final DataItemSerializer<long[]> dataItemSerializer;

    public final KeySerializer<? extends VirtualLongKey> keySerializer;

    FilesTestType(
            final DataItemSerializer<long[]> dataItemSerializer,
            KeySerializer<? extends VirtualLongKey> keySerializer) {
        this.dataItemSerializer = dataItemSerializer;
        this.keySerializer = keySerializer;
    }

    public VirtualLongKey createVirtualLongKey(final int i) {
        switch (this) {
            case fixed:
            default:
                return new ExampleLongKeyFixedSize(i);
            case fixedComplexKey:
                return new ExampleLongLongKeyFixedSize(i);
            case variable:
                return new ExampleLongKeyVariableSize(i);
            case variableComplexKey:
                return new ExampleLongLongKeyVariableSize(i);
        }
    }

    /**
     * Get the file size for a file created in DataFileLowLevelTest.createFile test. Values here are
     * measured values from a known good test run.
     */
    public long getDataFileLowLevelTestFileSize() {
        switch (this) {
            case fixed:
            default:
            case fixedComplexKey:
                return 24576L;
            case variable:
                return 102400L;
            case variableComplexKey:
                return 32768L;
        }
    }

    public SelfSerializableSupplier<?> getKeyConstructor() {
        switch (this) {
            case fixed:
            default:
                return new ExampleLongKeyFixedSize.Builder();
            case fixedComplexKey:
                return new ExampleLongLongKeyFixedSizeBuilder();
            case variable:
                return new ExampleLongKeyVariableSizeBuilder();
            case variableComplexKey:
                return new ExampleLongLongKeyVariableSizeBuilder();
        }
    }
}
