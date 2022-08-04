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

package com.swirlds.platform;

import com.swirlds.common.system.AddressBook;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.platform.components.SignatureExpander;
import com.swirlds.platform.components.SystemTransactionHandler;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.PreConsensusEventHandler;
import com.swirlds.platform.network.connectivity.SocketFactory;
import com.swirlds.platform.network.connectivity.TcpFactory;
import com.swirlds.platform.network.connectivity.TlsFactory;
import com.swirlds.platform.state.SignatureExpanderImpl;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.SignedStateManager;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.StateHasherSigner;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.SwirldStateManagerDouble;
import com.swirlds.platform.state.SwirldStateManagerSingle;
import com.swirlds.platform.stats.ConsensusHandlingStats;
import com.swirlds.platform.stats.SignedStateStats;
import com.swirlds.platform.stats.SwirldStateStats;
import com.swirlds.platform.system.PlatformConstructionException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;

import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

/**
 * Used to construct platform components that use DI
 */
final class PlatformConstructor {

	/** The maximum size of the queue holding signed states ready to be hashed and signed by others. */
	private static final int STATE_HASH_QUEUE_MAX = 1;

	/**
	 * Private constructor so that this class is never instantiated
	 */
	private PlatformConstructor() {
	}

	static ParallelExecutor parallelExecutor() {
		return new CachedPoolParallelExecutor("node-sync");
	}

	static SettingsProvider settingsProvider() {
		return StaticSettingsProvider.getSingleton();
	}

	static SocketFactory socketFactory(final KeysAndCerts keysAndCerts) {
		if (!Settings.useTLS) {
			return new TcpFactory(PlatformConstructor.settingsProvider());
		}
		try {
			return new TlsFactory(keysAndCerts, PlatformConstructor.settingsProvider());
		} catch (final NoSuchAlgorithmException | UnrecoverableKeyException
				| KeyStoreException | KeyManagementException
				| CertificateException | IOException e) {
			throw new PlatformConstructionException("A problem occurred while creating the SocketFactory", e);
		}
	}

	static PlatformSigner platformSigner(final KeysAndCerts keysAndCerts) {
		try {
			return new PlatformSigner(keysAndCerts);
		} catch (final NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException e) {
			throw new PlatformConstructionException(e);
		}
	}

	/**
	 * Creates the {@link QueueThread} that stores and handles signed states that need to be hashed and have signatures
	 * collected.
	 *
	 * @param selfId
	 * 		this node's id
	 * @param signedStateManager
	 * 		the signed state manager that collects signatures
	 * @param stats
	 * 		the class that records stats for signed state signing and hashing
	 * @return
	 */
	static QueueThread<SignedState> stateHashSignQueue(final long selfId, final SignedStateManager signedStateManager,
			final SignedStateStats stats) {
		final StateHasherSigner stateHasherSigner = new StateHasherSigner(signedStateManager, stats);

		return new QueueThreadConfiguration<SignedState>()
				.setNodeId(selfId)
				.setComponent(PLATFORM_THREAD_POOL_NAME)
				.setThreadName("state-hash-sign")
				.setHandler(stateHasherSigner::hashAndCollectSignatures)
				.setCapacity(STATE_HASH_QUEUE_MAX)
				.build();
	}

	/**
	 * Creates a new instance of {@link SwirldStateManager}.
	 *
	 * @param selfId
	 * 		this node's id
	 * @param systemTransactionHandler
	 * 		the handler of system transactions
	 * @param stats
	 * 		the class that records stats relating to {@link SwirldStateManager}
	 * @param settings
	 * 		static settings provider
	 * @param consEstimateSupplier
	 * 		supplier of an estimated consensus time for transactions
	 * @param initialState
	 * 		the initial state
	 * @return the newly constructed instance of {@link SwirldStateManager}
	 */
	public static SwirldStateManager swirldStateManager(final NodeId selfId,
			final SystemTransactionHandler systemTransactionHandler, final SwirldStateStats stats,
			final SettingsProvider settings, final Supplier<Instant> consEstimateSupplier,
			final State initialState) {

		if (initialState.getSwirldState() instanceof SwirldState.SwirldState2) {
			return new SwirldStateManagerDouble(
					selfId,
					systemTransactionHandler,
					stats,
					settings,
					signatureExpander(),
					initialState);
		} else {
			final SwirldStateManagerSingle swirldStateManagerSingle = new SwirldStateManagerSingle(
					selfId,
					systemTransactionHandler,
					stats,
					settings,
					consEstimateSupplier,
					signatureExpander(),
					initialState);
			NotificationFactory.getEngine().register(ReconnectCompleteListener.class, swirldStateManagerSingle);
			return swirldStateManagerSingle;
		}
	}

	private static SignatureExpander signatureExpander() {
		return new SignatureExpanderImpl();
	}

	/**
	 * Constructs a new {@link PreConsensusEventHandler}.
	 *
	 * @param selfId
	 * 		this node's id
	 * @param swirldStateManager
	 * 		the instance of {@link SwirldStateManager}
	 * @param stats
	 * 		the class that records stats relating to {@link SwirldStateManager}
	 * @return the newly constructed instance of {@link PreConsensusEventHandler}
	 */
	public static PreConsensusEventHandler preConsensusEventHandler(final NodeId selfId,
			final SwirldStateManager swirldStateManager, final SwirldStateStats stats) {

		return new PreConsensusEventHandler(
				selfId,
				swirldStateManager,
				stats
		);
	}

	/**
	 * Constructs a new {@link ConsensusRoundHandler}.
	 *
	 * @param selfId
	 * 		this node's id
	 * @param settingsProvider
	 * 		a static settings provider
	 * @param swirldStateManager
	 * 		the instance of {@link SwirldStateManager}
	 * @param stats
	 * 		the class that records stats relating to {@link SwirldStateManager}
	 * @param eventStreamManager
	 * 		the instance that streams consensus events to disk
	 * @param addressBook
	 * 		the address book of the network
	 * @param stateHashSignQueue
	 * 		the queue for signed states that need signatures collected
	 * @return the newly constructed instance of {@link ConsensusRoundHandler}
	 */
	public static ConsensusRoundHandler consensusHandler(final long selfId, final SettingsProvider settingsProvider,
			final SwirldStateManager swirldStateManager, final ConsensusHandlingStats stats,
			final EventStreamManager<EventImpl> eventStreamManager, final AddressBook addressBook,
			final BlockingQueue<SignedState> stateHashSignQueue) {

		return new ConsensusRoundHandler(
				selfId,
				settingsProvider,
				swirldStateManager,
				stats,
				eventStreamManager,
				addressBook,
				stateHashSignQueue
		);
	}
}
