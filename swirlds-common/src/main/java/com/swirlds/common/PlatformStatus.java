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

package com.swirlds.common;

/**
 * The status of the Platform, indicating whether all is normal, or if it has some problem, such as being disconnected,
 * or not having the latest state.
 */
public enum PlatformStatus {
	/**
	 * The Platform is still starting up. This is the default state before ACTIVE
	 */
	STARTING_UP,
	/**
	 * All is normal: the Platform is running, connected, and syncing properly
	 */
	ACTIVE,
	/**
	 * The Platform is not currently connected to any other computers on the network
	 */
	DISCONNECTED,
	/**
	 * The Platform does not have the latest state, and needs to reconnect
	 */
	BEHIND,
	/**
	 * The Platform is undergoing maintenance
	 */
	MAINTENANCE
}
