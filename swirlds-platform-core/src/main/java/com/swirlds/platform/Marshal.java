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

package com.swirlds.platform;

import com.swirlds.common.CommonUtils;
import com.swirlds.platform.internal.DatabaseBackupSettings;
import com.swirlds.platform.internal.DatabaseRestoreSettings;
import com.swirlds.platform.internal.DatabaseSettings;

import java.io.File;

/**
 * INTERNAL USE ONLY - Backdoor access to package private variables. Needs to be evaluated for better solution. Interim
 * fix for bigger potential changes.
 */
public class Marshal {
	public static final int HASH_SIZE_BYTES = Crypto.HASH_SIZE_BYTES;

	private Marshal() {

	}

	public static DatabaseSettings getDatabaseSettings() {
		return Settings.dbConnection;
	}

	public static DatabaseBackupSettings getDatabaseBackupSettings() {
		return Settings.dbBackup;
	}

	public static DatabaseRestoreSettings getDatabaseRestoreSettings() {
		return Settings.dbRestore;
	}

	public static File getSavedDirPath() {
		return Settings.savedDirPath;
	}

	public static File getDataDirPath() {
		return CommonUtils.canonicalFile("data");
	}
}
