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
package com.swirlds.virtual.merkle;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;

public abstract class VirtualMerkleTestBase {

    protected VirtualDataSourceBuilder<TestKey, TestValue> createBuilder() {
        return new JasperDbBuilder<TestKey, TestValue>()
                .keySerializer(new TestKeySerializer())
                .virtualLeafRecordSerializer(
                        new VirtualLeafRecordSerializer<>(
                                (short) 1,
                                DigestType.SHA_384,
                                (short) 1,
                                TestKey.BYTES,
                                new TestKeySerializer(),
                                (short) 1,
                                DataFileCommon.VARIABLE_DATA_SIZE,
                                new TestValueSerializer(),
                                true));
    }

    protected VirtualMap<TestKey, TestValue> createMap(String label) {
        return new VirtualMap<>(label, createBuilder());
    }
}
