/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */
package com.swirlds.jasperdb.files.hashmap;

/**
 * The type of index that the database should use for a key.
 */
public enum KeyIndexType {
	/**
	 * Index that can handle any key type and uses disk. See {@link com.swirlds.jasperdb.files.hashmap.HalfDiskHashMap}
	 */
	GENERIC,
	/**
	 * Index that assumes the keys are sequential longs without any gaps and implement {@link com.swirlds.virtualmap.VirtualLongKey}.
	 * This index is 100% in memory, so use with care.
	 */
	SEQUENTIAL_INCREMENTING_LONGS
}
