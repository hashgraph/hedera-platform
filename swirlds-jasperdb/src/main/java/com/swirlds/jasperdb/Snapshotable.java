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

package com.swirlds.jasperdb;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for classes that can be snapshotted.
 * <p><b>
 * Only one snapshot can happen at a time!
 * </b></p>
 * <p><b>
 * IMPORTANT, after this is completed the caller owns the directory. It is responsible for deleting it when it
 * is no longer needed.
 * </b></p>
 */
public interface Snapshotable {
	/**
	 * Start snapshot, this is called while saving is blocked. It is expected to complete as fast as possible and only
	 * do the minimum needed to capture/write state that could be changed by saving.
	 *
	 * @param snapshotDirectory
	 * 		Directory to put snapshot into, it will be created if it doesn't exist.
	 * @throws IOException
	 * 		If there was a problem snapshotting
	 */
	void startSnapshot(Path snapshotDirectory) throws IOException;

	/**
	 * Do the bulk of snapshot work, as much as possible. Saving is not blocked while this method is running, and it is
	 * expected that saving can happen concurrently without problems. This will block till the snapshot is completely
	 * created.
	 *
	 * @param snapshotDirectory
	 * 		Directory to put snapshot into, it will be created if it doesn't exist.
	 * @throws IOException
	 * 		If there was a problem snapshotting
	 */
	void middleSnapshot(Path snapshotDirectory) throws IOException;

	/**
	 * End snapshot, this is called while saving is blocked. It is expected to complete as fast as possible and only do
	 * the minimum needed to finish any work and return state after snapshotting.
	 *
	 * @param snapshotDirectory
	 * 		Directory to put snapshot into, it will be created if it doesn't exist.
	 * @throws IOException
	 * 		If there was a problem snapshotting
	 */
	void endSnapshot(Path snapshotDirectory) throws IOException;
}
