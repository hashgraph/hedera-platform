/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.platform.eventhandling;

import com.swirlds.common.NodeId;
import com.swirlds.common.notification.Notification;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteNotification;
import com.swirlds.common.threading.QueueThread;
import com.swirlds.common.threading.QueueThreadConfiguration;
import com.swirlds.common.threading.ThreadUtils;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.observers.PreConsensusEventObserver;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.stats.SwirldStateStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

/**
 * Created by a Platform to manage the flow of pre-consensus events to SwirldState (1 instance or 3 depending on the
 * SwirldState implemented). It contains a thread queue that contains a queue of pre-consensus events (q1) and a
 * SwirldStateManager which applies those events to the state
 */
public class PreConsensusEventHandler implements PreConsensusEventObserver, ReconnectCompleteListener {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();

	/** The initial size of the pre-consensus event queue. */
	private static final int INITIAL_PRE_CONS_EVENT_QUEUE_CAPACITY = 100;

	private final NodeId selfId;

	private QueueThread<EventImpl> queueThread;

	private final SwirldStateStats stats;

	/**
	 * The class responsible for all interactions with the swirld state
	 */
	private final SwirldStateManager swirldStateManager;

	/** The queue that the queueThread takes */
	private final BlockingQueue<EventImpl> queue;

	public PreConsensusEventHandler(final NodeId selfId,
			final SwirldStateManager swirldStateManager,
			final SwirldStateStats stats) {
		this.selfId = selfId;
		this.swirldStateManager = swirldStateManager;
		this.stats = stats;
		this.queue = new PriorityBlockingQueue<>(INITIAL_PRE_CONS_EVENT_QUEUE_CAPACITY,
				EventUtils::consensusPriorityComparator);
	}

	/**
	 * Creates and starts the queue thread.
	 */
	public void start() {
		queueThread = new QueueThreadConfiguration<EventImpl>()
				.setNodeId(selfId.getId())
				.setQueue(queue)
				.setComponent(PLATFORM_THREAD_POOL_NAME)
				.setThreadName("thread-curr")
				.setInterruptable(swirldStateManager.isInterruptable())
				// DO NOT turn the line below into a lambda reference because it will execute the getter, not the
				// runnable returned by the getter.
				.setWaitForItemRunnable(swirldStateManager.getPreConsensusWaitForWorkRunnable())
				.setHandler(swirldStateManager::handlePreConsensusEvent)
				.build();
		queueThread.start();
	}

	/**
	 * Stops and clears the queue thread in preparation reconnect.
	 *
	 * @throws InterruptedException
	 * 		if this thread is interrupted while stopping the queue thread
	 */
	public void prepareForReconnect() throws InterruptedException {
		LOG.info(RECONNECT.getMarker(), "pre-consensus handler: preparing for reconnect");
		// clear the queue before stopping the queue thread because we dont want to wait for existing items
		// to be handled.
		queue.clear();
		ThreadUtils.stopThreads(queueThread);
		LOG.info(RECONNECT.getMarker(), "pre-consensus handler: ready for reconnect");
	}

	/**
	 * Called for each {@link Notification} that this listener should handle.
	 *
	 * @param data
	 * 		the notification to be handled
	 */
	@Override
	public void notify(final ReconnectCompleteNotification data) {
		start();
		LOG.info(STARTUP.getMarker(), "PreConsensusEventHandler received ReconnectCompleteNotification, " +
						"queueThread.size: {}",
				queueThread == null ? null : queueThread.size());
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

//		try {
		// update the estimate now, so the queue can sort on it
		event.estimateTime(selfId, stats.getAvgSelfCreatedTimestamp(), stats.getAvgOtherReceivedTimestamp());
//			queueThread.put(event);
//		} catch (final InterruptedException e) {
//			LOG.error(EXCEPTION.getMarker(), "error:{} event:{}", e, event);
//			Thread.currentThread().interrupt();
//		}
	}

	public int getQueueSize() {
		return queueThread.size();
	}

}
