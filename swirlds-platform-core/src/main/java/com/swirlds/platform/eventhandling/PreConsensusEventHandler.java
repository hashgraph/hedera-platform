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

package com.swirlds.platform.eventhandling;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.observers.PreConsensusEventObserver;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.stats.SwirldStateStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

/**
 * Created by a Platform to manage the flow of pre-consensus events to SwirldState (1 instance or 3 depending on the
 * SwirldState implemented). It contains a thread queue that contains a queue of pre-consensus events (q1) and a
 * SwirldStateManager which applies those events to the state
 */
public class PreConsensusEventHandler implements PreConsensusEventObserver, Clearable {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();

	/** The initial size of the pre-consensus event queue. */
	private static final int INITIAL_PRE_CONS_EVENT_QUEUE_CAPACITY = 100;

	private final NodeId selfId;

	private final QueueThread<EventImpl> queueThread;

	private final SwirldStateStats stats;

	/**
	 * The class responsible for all interactions with the swirld state
	 */
	private final SwirldStateManager swirldStateManager;

	public PreConsensusEventHandler(final NodeId selfId,
			final SwirldStateManager swirldStateManager,
			final SwirldStateStats stats) {
		this.selfId = selfId;
		this.swirldStateManager = swirldStateManager;
		this.stats = stats;
		final BlockingQueue<EventImpl> queue = new PriorityBlockingQueue<>(INITIAL_PRE_CONS_EVENT_QUEUE_CAPACITY,
				EventUtils::consensusPriorityComparator);
		queueThread = new QueueThreadConfiguration<EventImpl>()
				.setNodeId(selfId.getId())
				.setQueue(queue)
				.setComponent(PLATFORM_THREAD_POOL_NAME)
				.setThreadName("thread-curr")
				.setStopBehavior(swirldStateManager.getStopBehavior())
				// DO NOT turn the line below into a lambda reference because it will execute the getter, not the
				// runnable returned by the getter.
				.setWaitForItemRunnable(swirldStateManager.getPreConsensusWaitForWorkRunnable())
				.setHandler(swirldStateManager::handlePreConsensusEvent)
				.build();
	}

	/**
	 * Starts the queue thread.
	 */
	public void start() {
		queueThread.start();
	}

	@Override
	public void clear() {
		LOG.info(RECONNECT.getMarker(), "pre-consensus handler: preparing for reconnect");
		queueThread.clear();
		LOG.info(RECONNECT.getMarker(), "pre-consensus handler: ready for reconnect");
	}

	/**
	 * Expand signatures on the pre-consensus event and add it to the queue (q1) for handling. Events that are null,
	 * empty, or should be discarded according to {@link SwirldStateManager#discardPreConsensusEvent(EventImpl)} are not
	 * added to the queue.
	 */
	@Override
	public void preConsensusEvent(final EventImpl event) {
		// we don't need empty pre-consensus events
		if (event == null || event.isEmpty()) {
			return;
		}

		// we don't need events that should be discarded
		if (swirldStateManager.discardPreConsensusEvent(event)) {
			return;
		}

		// Expand signatures for events received from sync operations
		// Additionally, we should enqueue any signatures for verification
		// Signature expansion should be the last thing that is done. It can be fairly time consuming, so we don't
		// want to delay the verification of an event for it because other events depend on this one being valid.
		// Furthermore, we don't want to validate signatures contained in an event that is invalid.
		swirldStateManager.expandSignatures(event);

		// Temporarily disabled. This will be re-enabled in release 27.
		try {
			// update the estimate now, so the queue can sort on it
			event.estimateTime(selfId, stats.getAvgSelfCreatedTimestamp(), stats.getAvgOtherReceivedTimestamp());
			queueThread.put(event);
		} catch (final InterruptedException e) {
			LOG.error(EXCEPTION.getMarker(), "error:{} event:{}", e, event);
			Thread.currentThread().interrupt();
		}
	}

	public int getQueueSize() {
		return queueThread.size();
	}

}
