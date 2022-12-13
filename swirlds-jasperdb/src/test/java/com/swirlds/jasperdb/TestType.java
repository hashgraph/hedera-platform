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
package com.swirlds.jasperdb;

import static com.swirlds.jasperdb.JasperDbTestUtils.hash;
import static com.swirlds.jasperdb.files.DataFileCommon.deleteDirectoryAndContents;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualLongKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Supports parameterized testing of {@link VirtualDataSourceJasperDB} with both fixed- and
 * variable-size data.
 *
 * <p>Used with JUnit's {@link org.junit.jupiter.params.provider.EnumSource} annotation.
 */
public enum TestType {
    /** Parameterizes a test with fixed-size key and fixed-size data. */
    fixed_fixed(),
    /** Parameterizes a test with fixed-size key and variable-size data. */
    fixed_variable(),
    /** Parameterizes a test with fixed-size complex key and fixed-size data. */
    fixedComplex_fixed(),
    /** Parameterizes a test with fixed-size complex key and variable-size data. */
    fixedComplex_variable(),
    /** Parameterizes a test with variable-size key and fixed-size data. */
    variable_fixed(),
    /** Parameterizes a test with variable-size key and variable-size data. */
    variable_variable(),
    /** Parameterizes a test with variable-size complex key and fixed-size data. */
    variableComplex_fixed(),
    /** Parameterizes a test with variable-size complex key and variable-size data. */
    variableComplex_variable();

    public <K extends VirtualKey<? super K>, V extends VirtualValue>
            DataTypeConfig<K, V> dataType() {
        return new DataTypeConfig<>(this);
    }

    @SuppressWarnings({"unchecked", "rawtypes", "unused"})
    public static class DataTypeConfig<K extends VirtualKey<? super K>, V extends VirtualValue> {
        private final TestType testType;
        private final KeySerializer<?> keySerializer;
        private final SelfSerializableSupplier<?> keyConstructor;
        private final int valueSizeBytes;
        private final SelfSerializableSupplier<?> valueConstructor;

        public DataTypeConfig(TestType testType) {
            this.testType = testType;
            this.keySerializer = createKeySerializer();
            this.keyConstructor = createKeyConstructor();
            this.valueSizeBytes = createValueSize();
            this.valueConstructor = createValueConstructor();
            new ExampleLongLongKeyFixedSize.Serializer();
            new ExampleLongKeyVariableSize.Serializer();
            new ExampleLongLongKeyVariableSize.Serializer();
        }

        public KeySerializer<?> getKeySerializer() {
            return keySerializer;
        }

        private KeySerializer<?> createKeySerializer() {
            switch (testType) {
                default:
                case fixed_fixed:
                case fixed_variable:
                    return new ExampleLongKeyFixedSize.Serializer();
                case fixedComplex_fixed:
                case fixedComplex_variable:
                    return new ExampleLongLongKeyFixedSize.Serializer();
                case variable_fixed:
                case variable_variable:
                    return new ExampleLongKeyVariableSize.Serializer();
                case variableComplex_fixed:
                case variableComplex_variable:
                    return new ExampleLongLongKeyVariableSize.Serializer();
            }
        }

        public VirtualLongKey createVirtualLongKey(final int i) {
            switch (testType) {
                default:
                case fixed_fixed:
                case fixed_variable:
                    return new ExampleLongKeyFixedSize(i);
                case fixedComplex_fixed:
                case fixedComplex_variable:
                    return new ExampleLongLongKeyFixedSize(i);
                case variable_fixed:
                case variable_variable:
                    return new ExampleLongKeyVariableSize(i);
                case variableComplex_fixed:
                case variableComplex_variable:
                    return new ExampleLongLongKeyVariableSize(i);
            }
        }

        /**
         * Get the file size for a file created in DataFileLowLevelTest.createFile test. Values here
         * are measured values from a known good test run.
         */
        public long getDataFileLowLevelTestFileSize() {
            switch (testType) {
                default:
                case fixed_fixed:
                case fixed_variable:
                case fixedComplex_fixed:
                case fixedComplex_variable:
                case variable_fixed:
                case variable_variable:
                case variableComplex_fixed:
                case variableComplex_variable:
                    return 24576L;
            }
        }

        public SelfSerializableSupplier<?> getKeyConstructor() {
            return keyConstructor;
        }

        private SelfSerializableSupplier<?> createKeyConstructor() {
            switch (testType) {
                default:
                case fixed_fixed:
                case fixed_variable:
                    return new ExampleLongKeyFixedSize.Builder();
                case fixedComplex_fixed:
                case fixedComplex_variable:
                    return new ExampleLongLongKeyFixedSizeBuilder();
                case variable_fixed:
                case variable_variable:
                    return new ExampleLongKeyVariableSizeBuilder();
                case variableComplex_fixed:
                case variableComplex_variable:
                    return new ExampleLongLongKeyVariableSizeBuilder();
            }
        }

        private SelfSerializableSupplier<?> createValueConstructor() {
            switch (testType) {
                default:
                case fixed_fixed:
                case fixedComplex_fixed:
                case variable_fixed:
                case variableComplex_fixed:
                    return new ExampleFixedSizeVirtualValue.Builder();
                case fixed_variable:
                case fixedComplex_variable:
                case variable_variable:
                case variableComplex_variable:
                    return new ExampleVariableSizeVirtualValue.Builder();
            }
        }

        private int createValueSize() {
            switch (testType) {
                default:
                case fixed_fixed:
                case fixedComplex_fixed:
                case variable_fixed:
                case variableComplex_fixed:
                    return ExampleFixedSizeVirtualValue.SIZE_BYTES;
                case fixed_variable:
                case fixedComplex_variable:
                case variable_variable:
                case variableComplex_variable:
                    return DataFileCommon.VARIABLE_DATA_SIZE;
            }
        }

        public boolean hasKeyToPathStore() {
            return (keySerializer.getSerializedSize() != Long.BYTES);
        }

        public VirtualLeafRecordSerializer<VirtualLongKey, ExampleByteArrayVirtualValue>
                createVirtualLeafRecordSerializer() {
            return new VirtualLeafRecordSerializer(
                    (short) 1,
                    DigestType.SHA_384,
                    (short) keySerializer.getCurrentDataVersion(),
                    keySerializer.getSerializedSize(),
                    keyConstructor,
                    (valueSizeBytes == DataFileCommon.VARIABLE_DATA_SIZE)
                            ? (short) ExampleVariableSizeVirtualValue.SERIALIZATION_VERSION
                            : (short) ExampleFixedSizeVirtualValue.SERIALIZATION_VERSION,
                    valueSizeBytes,
                    valueConstructor,
                    false);
        }

        public VirtualDataSourceJasperDB<VirtualLongKey, ExampleByteArrayVirtualValue>
                createDataSource(
                        Path storePath,
                        boolean deleteIfExists,
                        final int size,
                        final long internalHashesRamToDiskThreshold,
                        final boolean enableMerging,
                        boolean preferDiskBasedIndexes)
                        throws IOException {
            //			final KeySerializer<?> keySerializer = getKeySerializer();
            //			final SelfSerializableSupplier<VirtualLongKey> keyConstructor =
            //					(SelfSerializableSupplier<VirtualLongKey>) getKeyConstructor();
            //
            //			final VirtualLeafRecordSerializer<VirtualLongKey, ExampleFixedSizeVirtualValue>
            // virtualLeafRecordSerializer =
            //					new VirtualLeafRecordSerializer<>(
            //							(short) 1,
            //							DigestType.SHA_384,
            //							(short) keySerializer.getCurrentDataVersion(),
            //							keySerializer.getSerializedSize(),
            //							keyConstructor,
            //							(short) 1,
            //							ExampleFixedSizeVirtualValue.SIZE_BYTES,
            //							new ExampleFixedSizeVirtualValue.Builder(),
            //							false);

            // clean folder first if it has old data in it
            if (deleteIfExists) deleteDirectoryAndContents(storePath);

            return new VirtualDataSourceJasperDB(
                    createVirtualLeafRecordSerializer(),
                    new VirtualInternalRecordSerializer(),
                    getKeySerializer(),
                    storePath,
                    "test",
                    size * 10L,
                    enableMerging,
                    internalHashesRamToDiskThreshold,
                    preferDiskBasedIndexes);
        }

        public VirtualLeafRecord<VirtualLongKey, ExampleByteArrayVirtualValue>
                createVirtualLeafRecord(final int i) {
            return createVirtualLeafRecord(i, i, i);
        }

        public VirtualLeafRecord<VirtualLongKey, ExampleByteArrayVirtualValue>
                createVirtualLeafRecord(final long path, final int i, final int valueIndex) {

            switch (testType) {
                default:
                case fixed_fixed:
                case fixedComplex_fixed:
                case variable_fixed:
                case variableComplex_fixed:
                    return new VirtualLeafRecord<>(
                            path,
                            hash(i),
                            createVirtualLongKey(i),
                            new ExampleFixedSizeVirtualValue(valueIndex));
                case fixed_variable:
                case fixedComplex_variable:
                case variable_variable:
                case variableComplex_variable:
                    return new VirtualLeafRecord<>(
                            path,
                            hash(i),
                            createVirtualLongKey(i),
                            new ExampleVariableSizeVirtualValue(valueIndex));
            }
        }
    }
}
