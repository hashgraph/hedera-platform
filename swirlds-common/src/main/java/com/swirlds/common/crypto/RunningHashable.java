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

package com.swirlds.common.crypto;

/**
 * Each RunningHashable instance contains a RunningHash instance, which encapsulates a Hash object
 * which denotes a running Hash calculated from all RunningHashable in history up to this RunningHashable instance
 */
public interface RunningHashable extends Hashable {

	/**
	 * Gets the current {@link RunningHash} instance associated with this object. This method should always return an
	 * instance of the {@link RunningHash} class and should never return a {@code null} value.
	 *
	 * @return the attached {@code RunningHash} instance.
	 */
	RunningHash getRunningHash();
}
