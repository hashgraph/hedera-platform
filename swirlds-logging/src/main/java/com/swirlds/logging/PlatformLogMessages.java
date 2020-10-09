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

package com.swirlds.logging;

/**
 * @deprecated This is being used for the old (and fragile) style of String.contains log parsing.
 * 		Do not use this for any new logs.
 */
@Deprecated
public abstract class PlatformLogMessages {
	@Deprecated
	public static final String START_RECONNECT = "start reconnect";
	@Deprecated
	public static final String CHANGED_TO_ACTIVE = "Platform status changed to: ACTIVE";
	@Deprecated
	public static final String FINISHED_RECONNECT = "finished reconnect";
	@Deprecated
	public static final String RECV_STATE_HASH_MISMATCH = "Hash from received signed state does not match";
	@Deprecated
	public static final String RECV_STATE_ERROR = "Error while receiving a SignedState";
	@Deprecated
	public static final String RECV_STATE_IO_EXCEPTION = "IOException while receiving a SignedState";
	@Deprecated
	public static final String CHANGED_TO_BEHIND = "Platform status changed to: BEHIND";
	@Deprecated
	public static final String FALL_BEHIND_DO_NOT_RECONNECT = "has fallen behind, will die";

	@Deprecated
	public static final String PTD_SUCCESS = "TEST SUCCESS";
	@Deprecated
	public static final String PTD_FINISH = "TRANSACTIONS FINISHED";

	@Deprecated
	public static final String SYNC_STALE_COMPENSATION_SUCCESS = "Compensating for stale events during gossip";
	@Deprecated
	public static final String SYNC_STALE_COMPENSATION_FAILURE = "Failed to compensate for stale events during gossip" +
			 " due to delta exceeding threshold";
}
