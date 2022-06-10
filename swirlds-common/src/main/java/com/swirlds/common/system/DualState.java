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

import java.time.Instant;

/**
 * Contains any data that is either read or written by the platform and the application,
 * and contains methods for reading or writing those data.
 * The methods are available to both the platform and the application.
 */
public interface DualState {
	/**
	 * Sets the instant after which the platform will enter maintenance status.
	 * When consensus timestamp of a signed state is after this instant,
	 * the platform will stop creating events and accepting transactions.
	 * This is used to safely shut down the platform for maintenance.
	 *
	 * @param freezeTime
	 * 		an Instant in UTC
	 */
	void setFreezeTime(Instant freezeTime);

	/**
	 * Gets the time when the freeze starts
	 *
	 * @return the time when the freeze starts
	 */
	Instant getFreezeTime();

	/**
	 * Gets the last freezeTime based on which the nodes were frozen
	 *
	 * @return the last freezeTime based on which the nodes were frozen
	 */
	Instant getLastFrozenTime();
}
