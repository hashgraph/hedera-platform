/*
 * (c) 2016-2021 Swirlds, Inc.
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

	@Override
	public boolean isEnableBetaMirror() {
		return Settings.enableBetaMirror;
	}

	@Override
	public int getRescueChildlessInverseProbability() {
		return Settings.rescueChildlessInverseProbability;
	}
}
