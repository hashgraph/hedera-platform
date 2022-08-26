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

package com.swirlds.platform.chatter;

import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.platform.Connection;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.chatter.protocol.peer.CommunicationState;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.sync.ShadowGraphSynchronizer;
import com.swirlds.platform.sync.SyncException;

import java.io.IOException;

/**
 * Exchanges non-expired events with the peer and ensures all future events will be sent through {@link
 * com.swirlds.platform.chatter.communication.ChatterProtocol}
 */
public class ChatterSyncProtocol implements Protocol {
	private final CommunicationState state;
	private final MessageProvider messageProvider;
	private final ShadowGraphSynchronizer synchronizer;

	/**
	 * @param state
	 * 		the state that tracks the peer
	 * @param messageProvider
	 * 		keeps messages that need to be sent to the chatter peer
	 * @param synchronizer
	 * 		does a sync and enables chatter
	 */
	public ChatterSyncProtocol(
			final CommunicationState state,
			final MessageProvider messageProvider,
			final ShadowGraphSynchronizer synchronizer) {
		this.state = state;
		this.messageProvider = messageProvider;
		this.synchronizer = synchronizer;
	}

	@Override
	public boolean shouldInitiate() {
		return state.shouldPerformChatterSync();
	}

	@Override
	public boolean shouldAccept() {
		return state.shouldPerformChatterSync();
	}

	@Override
	public boolean acceptOnSimultaneousInitiate() {
		return true;
	}

	@Override
	public void runProtocol(final Connection connection)
			throws NetworkProtocolException, IOException, InterruptedException {
		state.chatterSyncStarted();
		try {
			synchronizer.synchronize(connection);
		} catch (final ParallelExecutionException | SyncException e) {
			state.chatterSyncFailed();
			messageProvider.clear();
			if (Utilities.isRootCauseSuppliedType(e, IOException.class)) {
				throw new IOException(e);
			}
			throw new NetworkProtocolException(e);
		} catch (final IOException | InterruptedException | RuntimeException e) {
			state.chatterSyncFailed();
			messageProvider.clear();
			throw e;
		}
		state.chatterSyncSucceeded();
	}
}
