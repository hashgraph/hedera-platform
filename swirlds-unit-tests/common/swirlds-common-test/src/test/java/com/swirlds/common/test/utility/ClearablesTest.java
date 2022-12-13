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
package com.swirlds.common.test.utility;

import com.swirlds.common.utility.Clearable;
import com.swirlds.common.utility.Clearables;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ClearablesTest {
    /** Tests whether all clearables have been cleared with a single method call */
    @Test
    void clearAllTest() {
        final List<AtomicBoolean> list = Stream.generate(AtomicBoolean::new).limit(10).toList();
        final Clearables clearables =
                Clearables.of(
                        list.stream()
                                .map(ab -> (Clearable) () -> ab.set(true))
                                .toArray(Clearable[]::new));
        clearables.clear();
        list.forEach(
                ab -> Assertions.assertTrue(ab.get(), "all booleans should have been set to true"));
    }
}
