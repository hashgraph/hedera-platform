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
