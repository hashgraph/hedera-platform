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

package com.swirlds.jasperdb.settings;

/**
 * Static holder of resolved {@code jasperDb.*} settings, via an instance of {@link JasperDbSettings}.
 */
public final class JasperDbSettingsFactory {
	private static JasperDbSettings jasperDbSettings;

	/**
	 * Hook that {@code Browser#populateSettingsCommon()} will use to attach the instance of
	 * {@link JasperDbSettings} obtained by parsing <i>settings.txt</i>.
	 */
	public static void configure(final JasperDbSettings jasperDbSettings) {
		JasperDbSettingsFactory.jasperDbSettings = jasperDbSettings;
	}

	/**
	 * Get the configured settings for JasperDB.
	 */
	public static JasperDbSettings get() {
		if (jasperDbSettings == null) {
			jasperDbSettings = new DefaultJasperDbSettings();
		}
		return jasperDbSettings;
	}

	private JasperDbSettingsFactory() {
		/* Utility class */
	}
}
