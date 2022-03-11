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

package com.swirlds.platform;

import com.swirlds.common.threading.CachedPoolParallelExecutor;
import com.swirlds.common.threading.ParallelExecutor;
import com.swirlds.common.threading.QueueThread;
import com.swirlds.common.threading.QueueThreadConfiguration;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.crypto.SocketFactory;
import com.swirlds.platform.crypto.TcpFactory;
import com.swirlds.platform.crypto.TlsFactory;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.SignedStateManager;
import com.swirlds.platform.state.StateHasherSigner;
import com.swirlds.platform.stats.EventFlowStats;
import com.swirlds.platform.system.PlatformConstructionException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

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
		} catch (NoSuchAlgorithmException | UnrecoverableKeyException
				| KeyStoreException | KeyManagementException
				| CertificateException | IOException e) {
			throw new PlatformConstructionException("A problem occurred while creating the SocketFactory", e);
		}
	}

	static PlatformSigner platformSigner(final KeysAndCerts keysAndCerts) {
		try {
			return new PlatformSigner(keysAndCerts);
		} catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException e) {
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
			final EventFlowStats stats) {
		final StateHasherSigner stateHasherSigner = new StateHasherSigner(signedStateManager, stats);

		return new QueueThreadConfiguration<SignedState>()
				.setNodeId(selfId)
				.setComponent(PLATFORM_THREAD_POOL_NAME)
				.setThreadName("state-hash-sign")
				.setHandler(stateHasherSigner::hashAndCollectSignatures)
				.setCapacity(STATE_HASH_QUEUE_MAX)
				.build();
	}
}
