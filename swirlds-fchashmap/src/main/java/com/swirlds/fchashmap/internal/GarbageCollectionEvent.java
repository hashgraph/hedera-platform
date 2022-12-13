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
package com.swirlds.fchashmap.internal;

/**
 * Represents an action that the garbage collector will take after a certain copy version is
 * deleted.
 *
 * @param <K> the type of the key
 */
public class GarbageCollectionEvent<K> {

    private final K key;
    private final long version;

    /**
     * Create a new garbage collection event.
     *
     * @param key the key that requires garbage colleciton
     * @param version the version which triggers garbage collection when it is deleted
     */
    public GarbageCollectionEvent(final K key, final long version) {
        this.key = key;
        this.version = version;
    }

    /** Get the key that requires cleanup. */
    public K getKey() {
        return key;
    }

    /** Get the version which, after deletion, requires cleanup. */
    public long getVersion() {
        return version;
    }

    public String toString() {
        return "[" + key + "@" + version + "]";
    }
}
