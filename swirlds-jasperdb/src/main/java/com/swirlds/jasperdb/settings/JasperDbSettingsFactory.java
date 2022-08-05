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
