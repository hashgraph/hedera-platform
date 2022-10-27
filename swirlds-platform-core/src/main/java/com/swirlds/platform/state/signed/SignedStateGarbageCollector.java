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

package com.swirlds.platform.state.signed;

import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;

import java.time.Duration;

/**
 * Deletes signed states on a background thread.
 */
public class SignedStateGarbageCollector {

	/**
	 * The number of states that are permitted to wait in the deletion queue.
	 */
	public static final int DELETION_QUEUE_CAPACITY = 25;

	/**
	 * <p>
	 * This queue thread is responsible for deleting/archiving signed states on a background thread.
	 * </p>
	 *
	 * <p>
	 * If, in the future, state deletion ever becomes a bottleneck, then it is safe to change this into a
	 * {@link com.swirlds.common.threading.framework.QueueThreadPool QueueThreadPool}.
	 * </p>
	 */
	private final QueueThread<Runnable> deletionQueue = new QueueThreadConfiguration<Runnable>()
			.setComponent("platform")
			.setThreadName("signed-state-deleter")
			.setMaxBufferSize(1)
			.setCapacity(DELETION_QUEUE_CAPACITY)
			.setHandler(Runnable::run)
			.build(true);

	private final SignedStateMetrics signedStateMetrics;

	/**
	 * Create a new garbage collector for signed states.
	 *
	 * @param signedStateMetrics
	 * 		metrics for signed states
	 */
	public SignedStateGarbageCollector(final SignedStateMetrics signedStateMetrics) {
		this.signedStateMetrics = signedStateMetrics;
	}

	/**
	 * Attempt to execute an operation on the garbage collection thread.
	 *
	 * @param operation
	 * 		the operation to be executed
	 * @return true if the operation will be eventually executed, false if the operation was rejected
	 * 		and will not be executed (due to a backlog of operations)
	 */
	public boolean executeOnGarbageCollectionThread(final Runnable operation) {
		if (signedStateMetrics != null) {
			signedStateMetrics.getStateDeletionQueueAvgMetric().update(deletionQueue.size());
		}
		return deletionQueue.offer(operation);
	}

	/**
	 * Report the time required to delete a state.
	 */
	public void reportDeleteTime(final Duration deletionTime) {
		if (signedStateMetrics != null) {
			signedStateMetrics.getStateDeletionTimeAvgMetric().update(deletionTime.toMillis());
		}
	}

	/**
	 * Report the time required to archive a state.
	 */
	public void reportArchiveTime(final Duration archiveTime) {
		if (signedStateMetrics != null) {
			signedStateMetrics.getStateArchivalTimeAvgMetric().update(archiveTime.toMillis());
		}
	}

	/**
	 * Stop the background thread.
	 */
	public void stop() {
		deletionQueue.stop();
	}

}
