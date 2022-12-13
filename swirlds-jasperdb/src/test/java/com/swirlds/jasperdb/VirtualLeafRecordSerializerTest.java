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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings("unchecked")
class VirtualLeafRecordSerializerTest {

    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        ConstructableRegistry.registerConstructables("com.swirlds.jasperdb");
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    void testSerializeDeserialize(final TestType testType) throws IOException {
        final KeySerializer<?> keySerializer = testType.dataType().getKeySerializer();
        final SelfSerializableSupplier<VirtualLongKey> keyConstructor =
                (SelfSerializableSupplier<VirtualLongKey>) testType.dataType().getKeyConstructor();

        final VirtualLeafRecordSerializer<VirtualLongKey, ExampleFixedSizeVirtualValue>
                virtualLeafRecordSerializer1 =
                        new VirtualLeafRecordSerializer<>(
                                (short) 1,
                                DigestType.SHA_384,
                                (short) keySerializer.getCurrentDataVersion(),
                                keySerializer.getSerializedSize(),
                                keyConstructor,
                                (short) 1,
                                ExampleFixedSizeVirtualValue.SIZE_BYTES,
                                new ExampleFixedSizeVirtualValue.Builder(),
                                false);

        final VirtualLeafRecordSerializer<VirtualLongKey, ExampleFixedSizeVirtualValue>
                virtualLeafRecordSerializer2 =
                        new VirtualLeafRecordSerializer<>(
                                (short) 1,
                                DigestType.SHA_384,
                                (short) keySerializer.getCurrentDataVersion(),
                                keySerializer.getSerializedSize(),
                                keyConstructor,
                                (short) 1,
                                ExampleFixedSizeVirtualValue.SIZE_BYTES,
                                new ExampleFixedSizeVirtualValue.Builder(),
                                false);

        assertEquals(
                virtualLeafRecordSerializer1,
                virtualLeafRecordSerializer2,
                "Two identical VirtualLeafRecordSerializers did not equal each other");

        // serialize
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final SerializableDataOutputStream sdout = new SerializableDataOutputStream(bout);
        sdout.writeSerializable(virtualLeafRecordSerializer1, true);
        sdout.flush();
        sdout.close();
        final byte[] serializedData = bout.toByteArray();
        // deserialize
        final ByteArrayInputStream bin = new ByteArrayInputStream(serializedData);
        final SerializableDataInputStream sdin = new SerializableDataInputStream(bin);
        VirtualLeafRecordSerializer<VirtualLongKey, ExampleFixedSizeVirtualValue>
                virtualLeafRecordSerializer3 = sdin.readSerializable();
        sdin.close();
        assertEquals(
                virtualLeafRecordSerializer1,
                virtualLeafRecordSerializer3,
                "Deserialized did not equal");
    }
}
