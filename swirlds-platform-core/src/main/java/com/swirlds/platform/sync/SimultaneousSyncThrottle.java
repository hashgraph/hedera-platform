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

import com.swirlds.common.threading.locks.MaybeLocked;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Controls simultaneous syncs:
 * - prevents 2 simultaneous syncs from occurring with the same member
 * - prevents too many inbound syncs occurring at the same time
 */
public class SimultaneousSyncThrottle {
	/** number of listener threads currently in a sync */
	private final AtomicInteger numListenerSyncs = new AtomicInteger(0);
	/** number of syncs currently happening (both caller and listener) */
	private final AtomicInteger numSyncs = new AtomicInteger(0);
	private final int maxListenerSyncs;
	/** lock per each other member, each one is used by all caller threads and the listener thread */
	private final Map<Long, SyncLock> simSyncThrottleLock;

	public SimultaneousSyncThrottle(final int maxListenerSyncs) {
		this.maxListenerSyncs = maxListenerSyncs;
		simSyncThrottleLock = new ConcurrentHashMap<>();
	}

	/**
	 * Try to acquire the lock for syncing with the given node. The lock will be acquired if no syncs are ongoing with
	 * that node.
	 *
	 * @param nodeId
	 * 		the ID of the node we wish to sync with
	 * @param isOutbound
	 * 		is the sync initiated by this platform
	 * @return an Autocloseable lock that provides information on whether the lock was acquired or not, and unlocks if
	 * 		previously locked on close()
	 */
	public MaybeLocked trySync(final long nodeId, final boolean isOutbound) {
		// if trying to do an inbound sync, check the max value
		if (!isOutbound && numListenerSyncs.get() > maxListenerSyncs) {
			return MaybeLocked.NOT_ACQUIRED;
		}

		return simSyncThrottleLock
				.computeIfAbsent(nodeId, this::newSyncLock)
				.tryLock(isOutbound);
	}

	private SyncLock newSyncLock(final long nodeId) {
		return new SyncLock(
				new ReentrantLock(),
				this::incrementSyncCount,
				this::decrementSyncCount
		);
	}

	private void incrementSyncCount(final boolean isOutbound) {
		numSyncs.incrementAndGet();
		if (!isOutbound) {
			numListenerSyncs.incrementAndGet();
		}
	}

	private void decrementSyncCount(final boolean isOutbound) {
		numSyncs.decrementAndGet();
		if (!isOutbound) {
			numListenerSyncs.decrementAndGet();
		}
	}

	/**
	 * Waits for all sync locks to be released sequentially. It does not prevent these locks from being acquired again.
	 */
	public void waitForAllSyncsToFinish() {
		for (final SyncLock lock : simSyncThrottleLock.values()) {
			lock.getLock().lock();
			lock.getLock().unlock();
		}
	}

	public int getNumListenerSyncs() {
		return numListenerSyncs.get();
	}

	public int getNumSyncs() {
		return numSyncs.get();
	}
}
