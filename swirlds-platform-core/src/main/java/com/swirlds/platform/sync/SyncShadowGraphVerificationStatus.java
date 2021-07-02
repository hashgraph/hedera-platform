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

package com.swirlds.platform.sync;

/**
 * An enum type that defines every verification status for a shadow graph instance
 */
public enum SyncShadowGraphVerificationStatus {
	VERIFIED,
	EVENT_NOT_IN_SHADOW_GRAPH,
	MISMATCHED_EVENT_HASH,
	MISSING_SELF_PARENT,
	MISSING_OTHER_PARENT,
	EXPIRED_SELF_PARENT,
	EXPIRED_OTHER_PARENT,
	MISMATCHED_SELF_PARENT,
	MISMATCHED_OTHER_PARENT,
	MISSING_INBOUND_SELF_CHILD_POINTER,
	MISSING_INBOUND_OTHER_CHILD_POINTER,
	MISSING_INBOUND_SELF_PARENT_POINTER,
	MISSING_INBOUND_OTHER_PARENT_POINTER,
	MISMATCHED_INBOUND_SELF_PARENT_POINTER,
	MISMATCHED_INBOUND_OTHER_PARENT_POINTER,
	INVALID_TIP,
	MISSING_TIP,
	INVALID_STATUS
}
