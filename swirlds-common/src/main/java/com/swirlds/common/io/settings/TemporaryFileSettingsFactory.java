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

package com.swirlds.common.io.settings;

/**
 * Provides an instance of the temporary file settings.
 */
public final class TemporaryFileSettingsFactory {

	private static TemporaryFileSettings temporaryFileSettings;

	private TemporaryFileSettingsFactory() {

	}

	/**
	 * Hook that {@code Browser#populateSettingsCommon()} will use to attach the instance of
	 * {@link TemporaryFileSettings} obtained by parsing <i>settings.txt</i>.
	 */
	public static void configure(final TemporaryFileSettings temporaryFileSettings) {
		TemporaryFileSettingsFactory.temporaryFileSettings = temporaryFileSettings;
	}

	/**
	 * Get default settings. This is useful for unit tests.
	 */
	private static TemporaryFileSettings getDefaultSettings() {
		return () -> "swirlds-tmp";
	}

	/**
	 * Get the configured settings for temporary files.
	 */
	public static TemporaryFileSettings get() {
		if (temporaryFileSettings == null) {
			temporaryFileSettings = getDefaultSettings();
		}
		return temporaryFileSettings;
	}
}
