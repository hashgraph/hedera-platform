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
package virtual.merkle.map;

import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import virtual.merkle.TestKey;
import virtual.merkle.TestValue;
import virtual.merkle.VirtualMerkleTestBase;

class MapTest extends VirtualMerkleTestBase {

    @Test
    @Tag(TIME_CONSUMING)
    @Tags({@Tag("VirtualMerkle"), @Tag("VMAP-019")})
    @DisplayName("Insert one million elements with same key but different value")
    void insertRemoveAndModifyOneMillion() throws InterruptedException {
        final int changesPerBatch = 15_432; // Some unexpected size just to be crazy
        final int max = 1_000_000;
        VirtualMap<TestKey, TestValue> map = createMap("insertRemoveAndModifyOneMillion");
        try {
            for (int i = 0; i < max; i++) {
                if (i > 0 && i % changesPerBatch == 0) {
                    VirtualMap<TestKey, TestValue> older = map;
                    map = map.copy();
                    older.release();
                }

                map.put(new TestKey(i), new TestValue(i));
            }

            for (int i = 0; i < max; i++) {
                assertEquals(new TestValue(i), map.get(new TestKey(i)), "Expected same");
            }

            for (int i = 0; i < max; i++) {
                if (i > 0 && i % changesPerBatch == 0) {
                    VirtualMap<TestKey, TestValue> older = map;
                    map = map.copy();
                    older.release();
                }

                map.remove(new TestKey(i));
            }

            assertTrue(map.isEmpty(), "Map should be empty");

            for (int i = 0; i < max; i++) {
                if (i > 0 && i % changesPerBatch == 0) {
                    VirtualMap<TestKey, TestValue> older = map;
                    map = map.copy();
                    older.release();
                }

                map.put(new TestKey(i + max), new TestValue(i + max));
            }

            for (int i = 0; i < max; i++) {
                assertEquals(
                        new TestValue(i + max), map.get(new TestKey(i + max)), "Expected same");
                assertNull(map.get(new TestKey(i)), "The old value should not exist anymore");
            }
        } finally {
            map.release();
        }
    }
}
