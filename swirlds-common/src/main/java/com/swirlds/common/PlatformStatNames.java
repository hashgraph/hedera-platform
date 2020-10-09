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
 * A class that holds all the user readable names
 */
public abstract class PlatformStatNames {
	/** number of consensus transactions per second handled by SwirldState.handleTransaction() */
	public static final String TRANSACTIONS_HANDLED_PER_SECOND = "transH/sec";
	/** time from creating an event to knowing its consensus (in seconds) */
	public static final String CREATION_TO_CONSENSUS_SEC = "secC2C";
	/** number of consensus events in queue waiting to be handled */
	public static final String CONSENSUS_QUEUE_SIZE = "q2";
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
	/** latest round with state signed by a supermajority */
	public static final String ROUND_SUPER_MAJORITY = "roundSup";
	/** number of app transactions received per second (from unique events created by self and others) */
	public static final String TRANSACTIONS_PER_SECOND = "trans/sec";
}
