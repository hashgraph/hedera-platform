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
package com.swirlds.jasperdb.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HashListBufferedWrapperTest extends HashListByteBufferTest {

    public HashList createHashList(
            final int numHashesPerBuffer, final long maxHashes, final boolean offHeap) {
        return new HashListBufferedWrapper(
                new HashListByteBuffer(numHashesPerBuffer, maxHashes, offHeap));
    }

    public HashList createHashList(final Path file) throws IOException {
        return new HashListBufferedWrapper(new HashListByteBuffer(file));
    }

    @Test
    @DisplayName("Creating an instance default constructor")
    void createInstanceDefaultConstructor() {
        // If this is created with no exceptions, then we will declare victory
        final HashList hashList = new HashListByteBuffer();
        assertEquals(10_000_000_000L, hashList.maxHashes(), "Capacity should match maxHashes arg");
        assertEquals(0, hashList.size(), "Capacity should match maxHashes arg");
        assertEquals(10_000_000_000L, hashList.capacity(), "Capacity should match maxHashes arg");
    }
}
