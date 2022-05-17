/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.jasperdb;

import java.nio.file.Path;

/**
 * Simple class for building and holding the set of sub-paths for data in a JasperDB datasource directory
 */
public class JasperDbPaths {
	public final Path storageDir;
	public final Path metadataFile;
	public final Path pathToDiskLocationInternalNodesFile;
	public final Path pathToDiskLocationLeafNodesFile;
	public final Path internalHashStoreRamFile;
	public final Path internalHashStoreDiskDirectory;
	public final Path longKeyToPathFile;
	public final Path objectKeyToPathDirectory;
	public final Path pathToHashKeyValueDirectory;

	/**
	 * Create a set of all the sub-paths for stored data in a JasperDB data source.
	 *
	 * @param storageDir
	 * 		directory to store data files in
	 */
	public JasperDbPaths(final Path storageDir) {
		this.storageDir = storageDir;
		metadataFile = storageDir.resolve("metadata.jdbm");
		pathToDiskLocationInternalNodesFile = storageDir.resolve("pathToDiskLocationInternalNodes.ll");
		pathToDiskLocationLeafNodesFile = storageDir.resolve("pathToDiskLocationLeafNodes.ll");
		internalHashStoreRamFile = storageDir.resolve("internalHashStoreRam.hl");
		internalHashStoreDiskDirectory = storageDir.resolve("internalHashStoreDisk");
		longKeyToPathFile = storageDir.resolve("longKeyToPath.ll");
		objectKeyToPathDirectory = storageDir.resolve("objectKeyToPath");
		pathToHashKeyValueDirectory = storageDir.resolve("pathToHashKeyValue");
	}
}
