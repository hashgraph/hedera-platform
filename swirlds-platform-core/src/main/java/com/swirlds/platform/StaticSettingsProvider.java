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
	public boolean shouldSendSyncDoneByte() {
		return Settings.sendSyncDoneByte;
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
}
