/*
 * (c) 2016-2022 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
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
