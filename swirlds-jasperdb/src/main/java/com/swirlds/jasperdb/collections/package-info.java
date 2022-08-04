/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * This module exports collections specialized for use in the JasperDB
 * VirtualDataSource implementation. Except for the {@link com.swirlds.jasperdb.collections.ThreeLongsList},
 * the main design constraint is to maximize performance while preserving
 * safety given the concurrent usage patterns of JasperDB.
 *
 * Implementations of {@link com.swirlds.jasperdb.collections.HashList} and
 * {@link com.swirlds.jasperdb.collections.LongList} behave as simple maps
 * with {@code long} keys. The {@link com.swirlds.jasperdb.collections.ImmutableIndexedObjectList}
 * provides a copy-on-write list that maintains the self-reported order of
 * a collection of {@link com.swirlds.jasperdb.collections.IndexedObject}s.
 *
 * Since JasperDB typically only needs {@code long} keys in a contiguous
 * numeric range starting from some minimum value, there is also a theme of
 * reducing memory usage by not allocating storage for list prefixes.
 */
package com.swirlds.jasperdb.collections;
