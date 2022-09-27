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

import com.swirlds.common.io.settings.TemporaryFileSettings;
import com.swirlds.platform.internal.SubSetting;

import java.nio.file.Path;

/**
 * An implementation of {@link TemporaryFileSettings}.
 */
public class TemporaryFileSettingsImpl extends SubSetting implements TemporaryFileSettings {

	/**
	 * The directory where temporary files are created.
	 */
	public String temporaryFilePath = "swirlds-tmp";

	/**
	 * {@inheritDoc}
	 * <p>
	 * Always use the saved state directory as the base for the temp dir so that hard linked files used by virtual maps
	 * are kept on the same volume as the saved states.
	 */
	@Override
	public String getTemporaryFilePath() {
		return Path.of(Settings.state.savedStateDirectory, temporaryFilePath).toString();
	}
}
