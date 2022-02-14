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

package com.swirlds.common.merkle.copy;

/**
 * Trees are initialized differently depending on the scenario. This enum describes those different scenarios.
 */
public enum InitializationType {
	/**
	 * After tree has been deserialized from a stream that does not support external data.
	 */
	DESERIALIZATION,
	/**
	 * After tree has been deserialized from a stream that supports external data.
	 */
	EXTERNAL_DESERIALIZATION,
	/**
	 * After tree has been reconstructed during a reconnect.
	 */
	RECONNECT,
	/**
	 * After a tree has been copied by a method in {@link MerkleCopy}.
	 */
	COPY
}
