/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.system;

/**
 * A class that holds all the user readable names
 */
public abstract class PlatformStatNames {
	/** number of consensus transactions per second handled by SwirldState.handleTransaction() */
	public static final String TRANSACTIONS_HANDLED_PER_SECOND = "transH/sec";
	/** time from creating an event to knowing its consensus (in seconds) */
	public static final String CREATION_TO_CONSENSUS_SEC = "secC2C";
	/** number of consensus events in queue waiting to be handled */
	public static final String CONSENSUS_QUEUE_SIZE = "consEvents";
	/** number of pre-consensus events in queue waiting to be handled */
	public static final String PRE_CONSENSUS_QUEUE_SIZE = "preConsEvents";
	/** average number of rounds per second */
	public static final String ROUNDS_PER_SEC = "rounds/sec";
	/** average time it takes to hash a new SignedState (in seconds) */
	public static final String SIGNED_STATE_HASHING_TIME = "sigStateHash";
	/** bytes of free memory (which can increase after a garbage collection) */
	public static final String FREE_MEMORY = "memFree";
	/** maximum bytes that the JVM might use */
	public static final String MAXIMUM_MEMORY = "memMax";
	/** total bytes in the Java Virtual Machine */
	public static final String TOTAL_MEMORY_USED = "memTot";
	/** disk space free for use by the node */
	public static final String DISK_SPACE_FREE = "DiskspaceFree";
	/** disk space being used right now */
	public static final String DISK_SPACE_USED = "DiskspaceUsed";
	/** number of app transactions received per second (from unique events created by self and others) */
	public static final String TRANSACTIONS_PER_SECOND = "trans/sec";
	/** average time for a round trip message between 2 computers (in milliseconds) */
	public static final String PING_DELAY = "ping";
	/** the number of creators that have more than one tip at the start of each sync */
	public static final String MULTI_TIPS_PER_SYNC = "multiTips/sync";
	/** the number of tips per sync at the start of each sync */
	public static final String TIPS_PER_SYNC = "tips/sync";
	/** the average number of generations that should be expired but cannot yet due to reservations. */
	public static final String GENS_WAITING_FOR_EXPIRY = "gensWaitingForExpiry";
	/** the ratio of rejected sync to accepted syncs over time. */
	public static final String REJECTED_SYNC_RATIO = "rejectedSyncRatio";

	public static final String TRANS_SUBMIT_MICROS = "avgTransSubmitMicros";
}
