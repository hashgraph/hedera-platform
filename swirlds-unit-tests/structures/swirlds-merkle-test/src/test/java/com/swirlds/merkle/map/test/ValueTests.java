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
package com.swirlds.merkle.map.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.dummy.Value;
import com.swirlds.common.test.io.InputOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ValueTests {

    @BeforeAll
    public static void setUp() throws ConstructableRegistryException {
        ConstructableRegistry.registerConstructables("com.swirlds.merkle.map");
        ConstructableRegistry.registerConstructables("com.swirlds.merkle.tree");
        ConstructableRegistry.registerConstructables("com.swirlds.common.test.dummy");
    }

    @Test
    void serializeAndDeserializeTest() throws IOException {
        final Value value = Value.buildRandomValue();
        try (final InputOutputStream io = new InputOutputStream()) {
            io.getOutput().writeSerializable(value, true);
            io.startReading();

            final Value deserializedValue = io.getInput().readSerializable();
            assertEquals(value, deserializedValue, "expected deserialized value to match original");
        }
    }
}
