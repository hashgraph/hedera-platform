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
 * Represents future purging work after a certain map copy is deleted.
 *
 * @param key the key that requires purging
 * @param mutation the mutation that requires purging
 * @param <K> the type of the key
 * @param <V> the type of the value
 */
public record PurgingEvent<K, V>(K key, Mutation<V> mutation) {
    public String toString() {
        return "[key = " + key + ", version = " + mutation.getVersion() + "]";
    }
}
