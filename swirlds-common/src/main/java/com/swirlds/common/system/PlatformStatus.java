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

package com.swirlds.common.system;

import com.swirlds.common.UniqueId;

/**
 * The status of the Platform, indicating whether all is normal, or if it has some problem, such as being disconnected,
 * or not having the latest state.
 */
public enum PlatformStatus implements UniqueId {
	/**
	 * The Platform is still starting up. This is the default state before ACTIVE
	 */
	STARTING_UP(1),
	/**
	 * All is normal: the Platform is running, connected, and syncing properly
	 */
	ACTIVE(2),
	/**
	 * The Platform is not currently connected to any other computers on the network
	 */
	DISCONNECTED(3),
	/**
	 * The Platform does not have the latest state, and needs to reconnect
	 */
	BEHIND(4),
	/**
	 * The Platform is undergoing maintenance
	 */
	MAINTENANCE(5);

	/** unique ID */
	private final int id;

	/**
	 * Constructs an enum instance
	 *
	 * @param id
	 * 		unique ID of the instance
	 */
	PlatformStatus(final int id) {
		this.id = id;
	}

	@Override
	public int getId() {
		return id;
	}
}
