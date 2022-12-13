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
package com.swirlds.common.merkle.utility;

/**
 * Describes an object that contains a key.
 *
 * <p>If a Keyed object implements {@link com.swirlds.common.FastCopyable FastCopyable}, a {@link
 * com.swirlds.common.FastCopyable#copy() copy()} operation is expected to copy the key.
 *
 * <p>If a Keyed object implements {@link com.swirlds.common.crypto.Hashable Hashable} then the key
 * is expected to be hashed.
 *
 * <p>If a Keyed object is capable of being serialized and deserialized then the key is expected to
 * be serialized and deserialized as well.
 *
 * @param <K> the type of the key, each instance of this type must be effectively immutable (that
 *     is, no operation after initial construction should be capable of changing the behavior of
 *     {@link Object#hashCode()} or {@link Object#equals(Object)}.
 */
public interface Keyed<K> {

    /**
     * Get the key that corresponds to this object. Must always return the last object supplied by
     * {@link #setKey(Object)}, or null if {@link #setKey(Object)} has never been called.
     *
     * @return the key that corresponds to this object
     */
    K getKey();

    /**
     * Set the key that corresponds to this object.
     *
     * @param key the new key for this object
     */
    void setKey(K key);
}
