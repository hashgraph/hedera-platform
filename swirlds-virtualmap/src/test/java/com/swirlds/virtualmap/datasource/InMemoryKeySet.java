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
package com.swirlds.virtualmap.datasource;

import com.swirlds.virtualmap.VirtualKey;
import java.util.HashSet;
import java.util.Set;

/**
 * An in-memory implementation of {@link VirtualKeySet}.
 *
 * @param <K> the type of the key
 */
public class InMemoryKeySet<K extends VirtualKey<? super K>> implements VirtualKeySet<K> {

    private final Set<K> set = new HashSet<>();

    /** {@inheritDoc} */
    @Override
    public void add(final K key) {
        set.add(key);
    }

    /** {@inheritDoc} */
    @Override
    public boolean contains(final K key) {
        return set.contains(key);
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        // no-op
    }
}
