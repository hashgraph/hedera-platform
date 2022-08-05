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

package com.swirlds.common.merkle.synchronization.internal;

import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.swirlds.logging.LogMarker.RECONNECT;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * This class encapsulates all logic for the teacher's receiving thread.
 *
 * @param <T>
 * 		the type of data used by the view to represent a node
 */
public class TeacherReceivingThread<T> {

	private static final Logger LOG = LogManager.getLogger(TeacherReceivingThread.class);

	private static final String NAME = "receiver";

	private final StandardWorkGroup workGroup;
	private final AsyncInputStream<QueryResponse> in;
	private final TeacherTreeView<T> view;
	private final AtomicBoolean senderIsFinished;

	/**
	 * Create a thread for receiving responses to queries from the learner.
	 *
	 * @param workGroup
	 * 		the work group that will manage this thread
	 * @param in
	 * 		the input stream, this object is responsible for closing this when finished
	 * @param view
	 * 		the view to be used when touching the merkle tree
	 * @param senderIsFinished
	 * 		becomes true once the sending thread has finished
	 */
	public TeacherReceivingThread(
			final StandardWorkGroup workGroup,
			final AsyncInputStream<QueryResponse> in,
			final TeacherTreeView<T> view,
			final AtomicBoolean senderIsFinished) {
		this.workGroup = workGroup;
		this.in = in;
		this.view = view;
		this.senderIsFinished = senderIsFinished;
	}

	public void start() {
		workGroup.execute(NAME, this::run);
	}

	private void run() {
		try (in) {
			boolean finished = senderIsFinished.get();
			boolean responseExpected = view.isResponseExpected();

			while (!finished || responseExpected) {
				if (responseExpected) {
					final QueryResponse response = in.readAnticipatedMessage();
					final T node = view.getNodeForNextResponse();
					view.registerResponseForNode(node, response.doesLearnerHaveTheNode());
				} else {
					MILLISECONDS.sleep(1);
				}

				finished = senderIsFinished.get();
				responseExpected = view.isResponseExpected();
			}
		} catch (final InterruptedException ex) {
			LOG.warn(RECONNECT.getMarker(), "teacher's receiving thread interrupted");
			Thread.currentThread().interrupt();
		} catch (final Exception ex) {
			throw new MerkleSynchronizationException("exception in the teacher's receiving thread", ex);
		}
	}

}
