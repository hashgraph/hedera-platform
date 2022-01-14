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

package com.swirlds.platform.event;

public final class EventConstants {

	/**
	 * Private constructor so that this class is never instantiated
	 */
	private EventConstants() {
	}

	/** the sequence number used to represent that sequence is not defined */
	public static final long SEQUENCE_UNDEFINED = -1;
	/**
	 * the generation number used to represent that the generation is not defined.
	 * an event's computed generation number is always non-negative.
	 * in case it is used as a parent generation, it means there is no parent event
	 */
	public static final long GENERATION_UNDEFINED = -1;
	/** the ID number used to represent that the ID is undefined */
	public static final long CREATOR_ID_UNDEFINED = -1;
	/** the smallest round an event can belong to */
	public static final long MINIMUM_ROUND_CREATED = 1;
}
