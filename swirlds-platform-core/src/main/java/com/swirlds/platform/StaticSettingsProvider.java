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

package com.swirlds.platform;

import com.swirlds.platform.state.StateSettings;

/**
 * A temporary class to bridge circumvent the fact that the Settings class is package private
 */
public final class StaticSettingsProvider implements SettingsProvider {
	private static final StaticSettingsProvider SINGLETON = new StaticSettingsProvider();

	public static StaticSettingsProvider getSingleton() {
		return SINGLETON;
	}

	private StaticSettingsProvider() {
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEnableBetaMirror() {
		return Settings.enableBetaMirror;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getRescueChildlessInverseProbability() {
		return Settings.rescueChildlessInverseProbability;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getRandomEventProbability() {
		return Settings.randomEventProbability;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getThrottle7Threshold() {
		return Settings.throttle7threshold;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getThrottle7Extra() {
		return Settings.throttle7extra;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getThrottle7MaxBytes() {
		return Settings.throttle7maxBytes;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isThrottle7Enabled() {
		return Settings.throttle7;
	}

	@Override
	public int getMaxEventQueueForCons() {
		return Settings.maxEventQueueForCons;
	}

	@Override
	public int getTransactionMaxBytes() {
		return Settings.transactionMaxBytes;
	}

	@Override
	public int getSignedStateKeep() {
		return Settings.state.getSignedStateKeep();
	}

	@Override
	public int getSignedStateFreq() {
		return Settings.signedStateFreq;
	}

	@Override
	public long getDelayShuffle() {
		return Settings.delayShuffle;
	}

	@Override
	public int getSocketIpTos() {
		return Settings.socketIpTos;
	}

	@Override
	public int getTimeoutSyncClientSocket() {
		return Settings.timeoutSyncClientSocket;
	}

	@Override
	public int getTimeoutSyncClientConnect() {
		return Settings.timeoutSyncClientConnect;
	}

	@Override
	public int getTimeoutServerAcceptConnect() {
		return Settings.timeoutServerAcceptConnect;
	}

	@Override
	public boolean isTcpNoDelay() {
		return Settings.tcpNoDelay;
	}

	@Override
	public String getKeystorePassword() {
		return Settings.crypto.getKeystorePassword();
	}

	@Override
	public boolean isEnableStateRecovery() {
		return Settings.enableStateRecovery;
	}

	@Override
	public StateSettings getStateSettings() {
		return Settings.state;
	}

	/**
	 * @see Settings#throttleTransactionQueueSize
	 */
	@Override
	public int getThrottleTransactionQueueSize() {
		return Settings.throttleTransactionQueueSize;
	}

	@Override
	public int getMaxTransactionBytesPerEvent() {
		return Settings.maxTransactionBytesPerEvent;
	}

	@Override
	public boolean useLoopbackIp() {
		return Settings.useLoopbackIp;
	}

	@Override
	public int connectionStreamBufferSize() {
		return Settings.bufferSize;
	}

	@Override
	public int sleepHeartbeatMillis() {
		return Settings.sleepHeartbeat;
	}
}
