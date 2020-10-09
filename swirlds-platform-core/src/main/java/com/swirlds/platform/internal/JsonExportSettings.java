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

package com.swirlds.platform.internal;

import com.swirlds.common.internal.JsonExporterSettings;

public class JsonExportSettings extends SubSetting implements JsonExporterSettings {

	/**
	 *
	 */
	public boolean active = false;

	/**
	 *
	 */
	public boolean signedStateExportEnabled = true;

	/**
	 *
	 */
	public boolean swirldStateExportEnabled = false;

	/**
	 *
	 */
	public boolean hashgraphExportEnabled = true;

	/**
	 *
	 */
	public boolean writeWithSavedStateEnabled = false;

	/**
	 *
	 */
	public boolean writeContinuallyEnabled = false;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isActive() {
		return active;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isSignedStateExportEnabled() {
		return signedStateExportEnabled;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isSwirldStateExportEnabled() {
		return swirldStateExportEnabled;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isHashgraphExportEnabled() {
		return hashgraphExportEnabled;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isWriteWithSavedStateEnabled() {
		return writeWithSavedStateEnabled;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isWriteContinuallyEnabled() {
		return writeContinuallyEnabled;
	}

}
