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

package com.swirlds.platform.sync;

import com.swirlds.common.locks.AcquiredOnTry;
import com.swirlds.common.locks.MaybeLocked;

import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

/**
 * A lock that prevents 2 nodes from doing 2 syncs at the same time
 */
public class SyncLock {
	private final Lock lock;
	private final Consumer<Boolean> onObtained;
	private final Consumer<Boolean> onClose;
	private final MaybeLocked outBoundObtained;
	private final MaybeLocked inBoundObtained;

	public SyncLock(
			final Lock lock,
			final Consumer<Boolean> onObtained,
			final Consumer<Boolean> onClose) {
		this.lock = lock;
		this.onObtained = onObtained;
		this.onClose = onClose;
		this.outBoundObtained = new AcquiredOnTry(this::closeOutbound);
		this.inBoundObtained = new AcquiredOnTry(this::closeInbound);
	}

	public Lock getLock() {
		return lock;
	}

	private void closeOutbound() {
		onClose.accept(true);
		lock.unlock();
	}

	private void closeInbound() {
		onClose.accept(false);
		lock.unlock();
	}

	public MaybeLocked tryLock(final boolean isOutbound) {
		if (lock.tryLock()) {
			onObtained.accept(isOutbound);
			return isOutbound ? outBoundObtained : inBoundObtained;
		}
		return MaybeLocked.NOT_ACQUIRED;
	}
}
