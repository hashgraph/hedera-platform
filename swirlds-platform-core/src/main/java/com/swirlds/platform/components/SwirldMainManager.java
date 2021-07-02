/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.platform.components;

/**
 * Manages the interaction with {@link com.swirlds.common.SwirldMain}
 */
public interface SwirldMainManager {
	/**
	 * Announce that an event is about to be created.
	 *
	 * This is called just before an event is created, to give the Platform a chance to create any system
	 * transactions that should be sent out immediately. It is similar to SwirldMain.preEvent, except that
	 * the "app" is the Platform itself.
	 */
	void preEvent();
}
