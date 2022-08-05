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

package com.swirlds.platform.sync;

import com.swirlds.common.threading.locks.MaybeLocked;
import com.swirlds.common.threading.pool.ParallelExecutionException;
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
