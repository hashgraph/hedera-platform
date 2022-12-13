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

import static com.swirlds.common.utility.Units.MEBIBYTES_TO_BYTES;
import static com.swirlds.jasperdb.collections.LongList.DEFAULT_MAX_LONGS_TO_STORE;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LongListOffHeapTest extends LongListHeapTest {
    @Override
    protected LongList createLongList() {
        return new LongListOffHeap();
    }

    @Override
    protected LongList createLongListWithChunkSizeInMb(final int chunkSizeInMb) {
        final int impliedLongsPerChunk =
                Math.toIntExact((((long) chunkSizeInMb * MEBIBYTES_TO_BYTES) / Long.BYTES));
        return new LongListOffHeap(impliedLongsPerChunk, DEFAULT_MAX_LONGS_TO_STORE);
    }

    @Override
    protected LongList createFullyParameterizedLongListWith(
            final int numLongsPerChunk, final long maxLongs) {
        return new LongListOffHeap(numLongsPerChunk, maxLongs);
    }

    @Override
    protected LongList createLongListFromFile(final Path file) throws IOException {
        return new LongListOffHeap(file);
    }

    @Test
    void addressRequiresIndirectBuffer() {
        final ByteBuffer heapBuffer = ByteBuffer.allocate(32);
        assertThrows(
                IllegalArgumentException.class,
                () -> LongListOffHeap.address(heapBuffer),
                "Only indirect buffers can be used with LongListOffHeap");
    }
}
