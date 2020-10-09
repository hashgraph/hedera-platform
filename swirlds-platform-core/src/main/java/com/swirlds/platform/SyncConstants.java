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

package com.swirlds.platform;

import java.io.DataInputStream;

public abstract class SyncConstants {

	/** periodically sent to the SyncListener to keep connections alive */
	final static byte heartbeat = 0x40 /* 64 */;
	/** a reply sent back when a heartbeat is received by the SyncListener */
	final static byte heartbeatAck = 0x41 /* 65 */;
	/** sent to request a sync */
	final static byte commSyncRequest = 0x42 /* 66 */;
	/** sent as a reply to commSyncFirst when accepting an incoming sync request */
	final static byte commSyncAck = 0x43 /* 67 */;
	/** sent as a reply to commSyncRequest when rejecting an incoming sync request (because too busy) */
	final static byte commSyncNack = 0x44 /* 68 */;
	/** sent at the end of a sync, to show it's done */
	final static byte commSyncDone = 0x45 /* 69 */;
	/** sent last while writing an event as part of a sync */
	final static byte commEventLast = 0x46 /* 70 */;
	/** sent after a new socket connection is made */
	final static byte commConnect = 0x47 /* 71 */;
	/** sent before sending each event */
	final static byte commEventNext = 0x48 /* 72 */;
	/** sent after all events have been sent for this sync */
	final static byte commEventDone = 0x4a /* 74 */;
	/** sent if a remote node asked for an event that has been discarded because its old */
	final static byte commEventDiscarded = 0x4b /* 75 */;
	/** sent if a node wants to get latest signed state */
	public final static byte commStateRequest = 0x4c /* 76 */;
	/** sent as a reply to commStateRequest when accepting to transfer the latest state */
	public final static byte commStateAck = 0x4d /* 77 */;
	/** sent as a reply to commStateRequest when NOT accepting to transfer the latest state */
	public final static byte commStateNack = 0x4e /* 78 */;
	/** returned by {@link DataInputStream#read()} to indicate that the end of the stream has been reached */
	final static byte commEndOfStream = -1;
}
