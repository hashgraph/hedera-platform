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

import com.swirlds.common.locks.MaybeLocked;
import com.swirlds.common.threading.ParallelExecutionException;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.unidirectional.NetworkProtocolResponder;
import com.swirlds.platform.stats.SyncStats;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/**
 * Responds to sync requests and starts the sync protocol if the response is a yes.
 * An instance of this class is thread safe.
 */
public class SyncProtocolResponder implements NetworkProtocolResponder {
	private final SimultaneousSyncThrottle syncThrottle;
	private final ShadowGraphSynchronizer synchronizer;
	private final FallenBehindManager fallenBehindManager;
	/** returns true if the sync should be accepted */
	private final BooleanSupplier otherSyncThrottle;
	private final SyncStats stats;

	public SyncProtocolResponder(
			final SimultaneousSyncThrottle syncThrottle,
			final ShadowGraphSynchronizer synchronizer,
			final FallenBehindManager fallenBehindManager,
			final BooleanSupplier otherSyncThrottle,
			final SyncStats stats) {
		this.syncThrottle = syncThrottle;
		this.synchronizer = synchronizer;
		this.fallenBehindManager = fallenBehindManager;
		this.otherSyncThrottle = otherSyncThrottle;
		this.stats = stats;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void protocolInitiated(final byte initialByte, final SyncConnection connection)
			throws IOException, NetworkProtocolException {
		if (fallenBehindManager.hasFallenBehind() || Boolean.FALSE.equals(otherSyncThrottle.getAsBoolean())) {
			// if we have fallen behind, dont accept any syncs
			// or if the other throttle says so
			rejectSync(connection);
		} else {
			try (final MaybeLocked lock = syncThrottle.trySync(connection.getOtherId().getId(), false)) {
				if (!lock.isLockAcquired()) {
					// we should not be syncing, so reply NACK
					rejectSync(connection);
					return;
				}

				stats.updateRejectedSyncRatio(false);
				synchronizer.synchronize(connection);
			} catch (ParallelExecutionException | SyncException e) {
				throw new NetworkProtocolException(e);
			}
		}
	}

	private void rejectSync(final SyncConnection connection) throws IOException {
		stats.updateRejectedSyncRatio(true);
		synchronizer.rejectSync(connection);
	}
}
