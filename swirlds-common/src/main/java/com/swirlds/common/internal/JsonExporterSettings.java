/*
 * (c) 2016-2020 Swirlds, Inc.
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

package com.swirlds.common.internal;

import com.swirlds.common.Platform;

/**
 * Settings that control the behavior of the {@code SignedStateManager::jsonifySignedState()} diagnostic utilities.
 */
public interface JsonExporterSettings {

	/**
	 * Provides a {@link JsonExporterSettings} with a reasonable set of defaults.
	 *
	 * @return the default settings
	 */
	static JsonExporterSettings getDefaultSettings() {
		return new JsonExporterSettings() {
			@Override
			public boolean isActive() {
				return false;
			}

			@Override
			public boolean isSignedStateExportEnabled() {
				return true;
			}

			@Override
			public boolean isSwirldStateExportEnabled() {
				return false;
			}

			@Override
			public boolean isHashgraphExportEnabled() {
				return true;
			}

			@Override
			public boolean isWriteWithSavedStateEnabled() {
				return false;
			}

			@Override
			public boolean isWriteContinuallyEnabled() {
				return false;
			}
		};
	}


	/**
	 * Enables the {@code SignedStateManager::jsonifySignedState()} feature if set to {@code true}. If disabled the all
	 * other settings are also disabled.
	 *
	 * <em>Disabled by default.</em>
	 *
	 * <p>
	 * If enabled, the default behavior is to write {@code SignedState} and {@code Hashgraph} JSON files every time an
	 * invalid signed state error occurs or when a reconnect is executed on either the teacher or learner roles.
	 * </p>
	 *
	 * @return true if this entire feature is enabled; otherwise false is returned
	 */
	boolean isActive();

	/**
	 * Independently enables the top-level {@code SignedState} instance to be written out as a JSON file.
	 * This is enabled by default.
	 *
	 * <p>
	 * NOTE: If the {@link #isSignedStateExportEnabled()}, {@link #isHashgraphExportEnabled()}, {@link
	 * #isSwirldStateExportEnabled()} are all {@code false} then nothing will be written to disk.
	 * </p>
	 *
	 * @return true if this feature is enabled; otherwise false is returned
	 */
	boolean isSignedStateExportEnabled();

	/**
	 * Independently enables the {@link com.swirlds.common.SwirldState} instance contained in the {@code SignedState} to
	 * be written out as a JSON file.
	 *
	 * <p>
	 * NOTE: If the {@link #isSignedStateExportEnabled()}, {@link #isHashgraphExportEnabled()}, {@link
	 * #isSwirldStateExportEnabled()} are all {@code false} then nothing will be written to disk.
	 * </p>
	 *
	 * @return true if this feature is enabled; otherwise false is returned
	 */
	boolean isSwirldStateExportEnabled();

	/**
	 * Independently enables the {@link Platform#getAllEvents()} array to be written out as a JSON file.
	 * This is enabled by default.
	 *
	 * <p>
	 * NOTE: If the {@link #isSignedStateExportEnabled()}, {@link #isHashgraphExportEnabled()}, {@link
	 * #isSwirldStateExportEnabled()} are all {@code false} then nothing will be written to disk.
	 * </p>
	 *
	 * @return true if this feature is enabled; otherwise false is returned
	 */
	boolean isHashgraphExportEnabled();

	/**
	 * Enables the writing of JSON files every time a {@code SignedState} is written to disk.
	 *
	 * @return true if this feature is enabled; otherwise false is returned
	 */
	boolean isWriteWithSavedStateEnabled();

	/**
	 * Enables the continual writing of JSON files every time a reconnect occurs. Both the teacher and learner will
	 * begin writing the JSON files indefinitely or until an invalid signed state error occurs.
	 *
	 * @return true if this feature is enabled; otherwise false is returned
	 */
	boolean isWriteContinuallyEnabled();

}
