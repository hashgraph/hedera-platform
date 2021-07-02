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

package com.swirlds.platform;

import java.io.DataInputStream;

public abstract class SyncConstants {

	/** periodically sent to the SyncListener to keep connections alive */
	protected final static byte HEARTBEAT = 0x40 /* 64 */;
	/** a reply sent back when a heartbeat is received by the SyncListener */
	protected final static byte HEARTBEAT_ACK = 0x41 /* 65 */;
	/** sent to request a sync */
	protected final static byte COMM_SYNC_REQUEST = 0x42 /* 66 */;
	/** sent as a reply to COMM_SYNC_REQUEST when accepting an incoming sync request */
	protected final static byte COMM_SYNC_ACK = 0x43 /* 67 */;
	/** sent as a reply to COMM_SYNC_REQUEST when rejecting an incoming sync request (because too busy) */
	protected final static byte COMM_SYNC_NACK = 0x44 /* 68 */;
	/** sent at the end of a sync, to show it's done */
	protected final static byte COMM_SYNC_DONE = 0x45 /* 69 */;
	/** sent last while writing an event as part of a sync */
	protected final static byte COMM_EVENT_LAST = 0x46 /* 70 */;
	/** sent after a new socket connection is made */
	protected final static byte COMM_CONNECT = 0x47 /* 71 */;
	/** sent before sending each event */
	protected final static byte COMM_EVENT_NEXT = 0x48 /* 72 */;
	/** sent after all events have been sent for this sync */
	protected final static byte COMM_EVENT_DONE = 0x4a /* 74 */;
	/** sent if a remote node asked for an event that has been discarded because its old */
	protected final static byte COMM_EVENT_DISCARDED = 0x4b /* 75 */;
	/** sent if a node wants to get latest signed state */
	public final static byte COMM_STATE_REQUEST = 0x4c /* 76 */;
	/** sent as a reply to COMM_STATE_REQUEST when accepting to transfer the latest state */
	public final static byte COMM_STATE_ACK = 0x4d /* 77 */;
	/** sent as a reply to COMM_STATE_REQUEST when NOT accepting to transfer the latest state */
	public final static byte COMM_STATE_NACK = 0x4e /* 78 */;
	/** returned by {@link DataInputStream#read()} to indicate that the end of the stream has been reached */
	protected final static byte COMM_END_OF_STREAM = -1;
}
