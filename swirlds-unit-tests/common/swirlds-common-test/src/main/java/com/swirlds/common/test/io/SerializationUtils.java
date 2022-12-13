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
package com.swirlds.common.test.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.io.SelfSerializable;
import java.io.IOException;

public class SerializationUtils {

    public static <T extends SelfSerializable> T serializeDeserialize(T ss) throws IOException {
        try (InputOutputStream io = new InputOutputStream()) {
            io.getOutput().writeSerializable(ss, true);
            io.startReading();
            return io.getInput().readSerializable();
        }
    }

    public static <T extends SelfSerializable> void checkSerializeDeserializeEqual(T ss)
            throws IOException {
        assertEquals(ss, serializeDeserialize(ss));
    }
}
