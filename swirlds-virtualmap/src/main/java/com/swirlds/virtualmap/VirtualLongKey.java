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

package com.swirlds.virtualmap;

/**
 * Special form of a VirtualKey which is a simple long. This allows much faster special paths in data stores.
 */
public interface VirtualLongKey extends VirtualKey<VirtualLongKey> {

	/**
	 * Direct access to the value of this key in its raw long format
	 *
	 * @return the long value of this key
	 */
	long getKeyAsLong();

	/**
	 * {@inheritDoc}
	 */
	@Override
	default int compareTo(final VirtualLongKey other) {
		return Long.compare(getKeyAsLong(), other.getKeyAsLong());
	}
}
