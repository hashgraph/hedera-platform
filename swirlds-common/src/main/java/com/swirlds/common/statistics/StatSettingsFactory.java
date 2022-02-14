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

package com.swirlds.common.statistics;

/**
 * This object is used to configure general statistics settings.
 */
public final class StatSettingsFactory {

	private static StatSettings settings;

	private StatSettingsFactory() {

	}

	/**
	 * Specify the settings that should be used for the statistics.
	 */
	public static synchronized void configure(StatSettings settings) {
		StatSettingsFactory.settings = settings;
	}

	/**
	 * Get the settings for statistics.
	 */
	public static synchronized StatSettings get() {
		if (settings == null) {
			settings = getDefaultSettings();
		}
		return settings;
	}

	/**
	 * Get default statistic settings. Useful for testing.
	 */
	private static StatSettings getDefaultSettings() {
		return new StatSettings() {

			@Override
			public int getBufferSize() {
				return 100;
			}

			@Override
			public double getRecentSeconds() {
				return 63;
			}

			@Override
			public double getSkipSeconds() {
				return 60;
			}
		};
	}
}
