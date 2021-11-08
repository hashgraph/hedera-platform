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

import com.swirlds.common.threading.CachedPoolParallelExecutor;
import com.swirlds.common.threading.ParallelExecutionException;
import com.swirlds.common.threading.ParallelExecutor;
import com.swirlds.platform.components.EventTaskCreator;
import com.swirlds.platform.stats.SyncStats;
import com.swirlds.platform.sync.ShadowGraphManager;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Temporary class until #3736 is done
 * ATM, NodeSynchronizerImpl needs a new instance for every sync, this is helper class for creating these instances.
 * Eventually, NodeSynchronizer will be constructed only at boot time and this class can be removed at that point
 */
public class NodeSynchronizerInstantiator {
	/** The shadow graph manager to use for this sync */
	private final ShadowGraphManager shadowGraphManager;
	/** Number of member nodes in the network for this sync */
	private final long numberOfNodes;
	/** All sync stats */
	private final SyncStats stats;
	/** sleep in ms after each sync */
	private final long callerDelayAfterSync;
	/** provides the current consensus instance */
	private final Supplier<Consensus> consensusSupplier;
	/** for event creation and validation */
	private final EventTaskCreator eventTaskCreator;
	/** manages sync related decisions */
	private final SyncManager syncManager;
	/** executes tasks in parallel */
	private final ParallelExecutor executor;

	public NodeSynchronizerInstantiator(
			final ShadowGraphManager shadowGraphManager,
			final long numberOfNodes,
			final SyncStats stats,
			final long callerDelayAfterSync,
			final Supplier<Consensus> consensusSupplier,
			final EventTaskCreator eventTaskCreator,
			final SyncManager syncManager) {
		this.shadowGraphManager = shadowGraphManager;
		this.numberOfNodes = numberOfNodes;
		this.stats = stats;
		this.callerDelayAfterSync = callerDelayAfterSync;
		this.consensusSupplier = consensusSupplier;
		this.eventTaskCreator = eventTaskCreator;
		this.syncManager = syncManager;
		this.executor = new CachedPoolParallelExecutor("node-sync");
	}

	/**
	 * @param conn
	 * 		the connection to sync through
	 * @param caller
	 * 		true if this computer is calling the other (initiating the sync). False if this computer
	 * 		is a listener.
	 * @param canAcceptSync
	 * 		If this function is executed from a {@link SyncListener} thread, this value is true iff a
	 * 		listener thread can accept a sync request from a caller.
	 *
	 * 		If executed from a {@link SyncCaller} thread, this value is ignored.
	 * @param reconnected
	 * 		true iff this call is the first call after a reconnect on this thread, used for logging
	 * @return true iff a sync was (a) accepted, and (b) completed, including exchange of event data
	 * @throws Exception
	 * 		if any error occurs
	 */
	public boolean synchronize(
			final SyncConnection conn,
			final boolean caller,
			final boolean canAcceptSync,
			final boolean reconnected) throws Exception {

		final NodeSynchronizerImpl nodeSync = new NodeSynchronizerImpl(
				conn,
				caller,
				shadowGraphManager,
				stats,
				eventTaskCreator,
				consensusSupplier,
				syncManager,
				executor,
				numberOfNodes,
				callerDelayAfterSync
		);

		try {
			return nodeSync.synchronize(canAcceptSync, reconnected);
		} catch (ParallelExecutionException e) {
			// since we handle IO exceptions differently, then we rethrow the cause if its IO
			// this can be removed once we change how we handle exceptions in the caller and listener
			// the IOException can be nested more than once, so we need to go through all the causes
			Throwable cause = e.getCause();
			while (cause != null) {
				if (cause instanceof IOException) {
					throw (IOException) cause;
				}
				cause = cause.getCause();
			}

			throw e;
		}
	}
}
