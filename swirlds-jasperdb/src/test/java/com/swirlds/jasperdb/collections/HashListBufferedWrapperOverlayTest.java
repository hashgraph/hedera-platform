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

import static com.swirlds.jasperdb.JasperDbTestUtils.hash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HashListBufferedWrapperOverlayTest extends HashListByteBufferTest {

    public HashList createHashList(
            final int numHashesPerBuffer, final long maxHashes, final boolean offHeap) {
        HashListBufferedWrapper hashListBufferedWrapper =
                new HashListBufferedWrapper(
                        new HashListByteBuffer(numHashesPerBuffer, maxHashes, offHeap));
        hashListBufferedWrapper.setUseOverlay(true);
        return hashListBufferedWrapper;
    }

    public HashList createHashList(final Path file) throws IOException {
        HashListBufferedWrapper hashListBufferedWrapper =
                new HashListBufferedWrapper(new HashListByteBuffer(file));
        hashListBufferedWrapper.setUseOverlay(true);
        return hashListBufferedWrapper;
    }

    // ------------------------------------------------------
    // Extra Tests
    // ------------------------------------------------------

    @Test
    void testOverlayWriteDown() throws IOException {
        HashListByteBuffer hashListByteBuffer = new HashListByteBuffer(20, 100, true);
        HashListBufferedWrapper hashListBufferedWrapper =
                new HashListBufferedWrapper(hashListByteBuffer);
        // create 5 hashes direct though to wrapped buffer
        for (int i = 0; i < 5; i++) {
            hashListBufferedWrapper.put(i, hash(i));
        }
        for (int i = 0; i < 5; i++) {
            assertEquals(
                    hash(i),
                    hashListBufferedWrapper.get(i),
                    "Unexpected value for hash(" + i + ")");
        }
        assertEquals(hash(0), hashListBufferedWrapper.get(6), "Unexpected value for hash(0)");
        // enable overlay
        hashListBufferedWrapper.setUseOverlay(true);
        // check all data
        assertEquals(
                5,
                hashListBufferedWrapper.size(),
                "Unexpected value for hashListBufferedWrapper.size()");
        for (int i = 0; i < 5; i++) {
            assertEquals(
                    hash(i),
                    hashListBufferedWrapper.get(i),
                    "Unexpected value for hashListBufferedWrapper");
        }
        assertEquals(
                hash(0),
                hashListBufferedWrapper.get(6),
                "Unexpected value for hashListBufferedWrapper(6)");
        // add some more data into overlay
        for (int i = 5; i < 95; i++) {
            hashListBufferedWrapper.put(i, hash(i));
        }
        // check all data
        assertEquals(
                95,
                hashListBufferedWrapper.size(),
                "Unexpected value for hashListBufferedWrapper.size()");
        for (int i = 0; i < 95; i++) {
            assertEquals(
                    hash(i),
                    hashListBufferedWrapper.get(i),
                    "Unexpected value for hashListBufferedWrapper.get()");
        }
        assertNull(
                hashListBufferedWrapper.get(96),
                "Non-null value for hashListBufferedWrapper.get(96)");
        // check none of the new data is in wrapped list
        assertEquals(
                5, hashListByteBuffer.size(), "Unexpected value for hashListByteBuffer.size()");
        for (int i = 0; i < 5; i++) {
            assertEquals(
                    hash(i),
                    hashListByteBuffer.get(i),
                    "Unexpected value for hashListByteBuffer.get()");
        }
        assertEquals(
                hash(0),
                hashListByteBuffer.get(6),
                "Unexpected value for hashListByteBuffer.get(6)");
        // now turn overlay off
        hashListBufferedWrapper.setUseOverlay(false);
        // check both lists have complete data
        assertEquals(
                95,
                hashListBufferedWrapper.size(),
                "Unexpected value for hashListBufferedWrapper.size()");
        assertEquals(
                95, hashListByteBuffer.size(), "Unexpected value for hashListByteBuffer.size()");
        for (int i = 0; i < 95; i++) {
            assertEquals(
                    hash(i),
                    hashListBufferedWrapper.get(i),
                    "Unexpected value for hashListBufferedWrapper.get()");
            assertEquals(
                    hash(i),
                    hashListByteBuffer.get(i),
                    "Unexpected value for hashListByteBuffer.get()");
        }
        assertEquals(
                hash(0),
                hashListBufferedWrapper.get(96),
                "Unexpected value for hashListBufferedWrapper.get(96)");
        assertEquals(
                hash(0),
                hashListByteBuffer.get(96),
                "Unexpected value for hashListByteBuffer.get(96)");
    }
}
