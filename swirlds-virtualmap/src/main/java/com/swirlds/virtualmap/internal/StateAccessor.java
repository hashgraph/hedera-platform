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
