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

package com.swirlds.virtualmap.internal;

/**
 * Provides access to virtual merkle state, such as the name of the instance, the first
 * and last leaf paths, and the size. It also provides access to some statistics
 * machinery (although, when statistics is refactored, this should change).
 */
public interface StateAccessor {
	/**
	 * Gets the label of the instance.
	 *
	 * @return The label. This will never be null.
	 */
	String getLabel();

	/**
	 * Gets the first leaf path. This may be {@link Path#INVALID_PATH} if there are no leaves,
	 * but otherwise will be a value greater than zero and less than {@link #getLastLeafPath()}.
	 *
	 * @return The first leaf path.
	 */
	long getFirstLeafPath();

	/**
	 * Gets the last leaf path. This may be {@link Path#INVALID_PATH} if there are no leaves,
	 * but otherwise will be a value greater than or equal to {@link #getFirstLeafPath()}. It
	 * is only equal to {@link #getFirstLeafPath()} when there is a single leaf in the tree.
	 *
	 * @return The last leaf path.
	 */
	long getLastLeafPath();

	/**
	 * Sets the first leaf path.
	 *
	 * @param path
	 * 		Sets the first leaf path. The path must be greater than zero, or {@link Path#INVALID_PATH}, and
	 * 		strictly less than or equal to {@link #getLastLeafPath()}.
	 */
	void setFirstLeafPath(long path);

	/**
	 * Sets the last leaf path.
	 *
	 * @param path
	 * 		Sets the last leaf path. The path must be greater than zero, or {@link Path#INVALID_PATH}, and
	 * 		strictly greater than or equal to {@link #getFirstLeafPath()}.
	 */
	void setLastLeafPath(long path);

	/**
	 * Gets the size. This is computed based on the first and last leaf path values.
	 *
	 * @return The size.
	 */
	long size();
}
