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

package com.swirlds.common.threading.locks;

/**
 * Returned by the {@link AutoClosableLock} when the caller is not sure if the lock has been acquired or not.
 */
public interface MaybeLocked extends Locked {
	/** A convenience singleton to return when the lock has not been acquired */
	MaybeLocked NOT_ACQUIRED = new MaybeLocked() {
		@Override
		public boolean isLockAcquired() {
			return false;
		}

		@Override
		public void close() {
			// do nothing
		}
	};

	/**
	 * @return true if the lock has been acquired, false otherwise
	 */
	boolean isLockAcquired();
}
