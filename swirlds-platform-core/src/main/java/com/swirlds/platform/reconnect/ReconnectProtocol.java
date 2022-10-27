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

package com.swirlds.platform.reconnect;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.state.signed.SignedState;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Implements the reconnect protocol over a bidirectional network
 */
public class ReconnectProtocol implements Protocol {
	private final NodeId peerId;
	private final ReconnectThrottle teacherThrottle;
	private final Supplier<SignedState> lastCompleteSignedState;
	private final int reconnectSocketTimeout;
	private final ReconnectMetrics statistics;
	private final ReconnectController reconnectController;


	private InitiatedBy initiatedBy = InitiatedBy.NO_ONE;

	/**
	 * @param peerId
	 * 		the ID of the peer we are communicating with
	 * @param teacherThrottle
	 * 		restricts reconnects as a teacher
	 * @param lastCompleteSignedState
	 * 		provides the latest completely signed state
	 * @param reconnectSocketTimeout
	 * 		the socket timeout to use when executing a reconnect
	 * @param statistics
	 * 		tracks reconnect statistics
	 * @param reconnectController
	 * 		controls reconnecting as a learner
	 */
	public ReconnectProtocol(
			final NodeId peerId,
			final ReconnectThrottle teacherThrottle,
			final Supplier<SignedState> lastCompleteSignedState,
			final int reconnectSocketTimeout,
			final ReconnectMetrics statistics,
			final ReconnectController reconnectController) {
		this.peerId = peerId;
		this.teacherThrottle = teacherThrottle;
		this.lastCompleteSignedState = lastCompleteSignedState;
		this.reconnectSocketTimeout = reconnectSocketTimeout;
		this.statistics = statistics;
		this.reconnectController = reconnectController;
	}

	@Override
	public boolean shouldInitiate() {
		final boolean shouldInitiate = reconnectController.acquireLearnerPermit();
		if (shouldInitiate) {
			initiatedBy = InitiatedBy.SELF;
		}
		return shouldInitiate;
	}

	@Override
	public void initiateFailed() {
		reconnectController.cancelLearnerPermit();
		initiatedBy = InitiatedBy.NO_ONE;
	}

	@Override
	public boolean shouldAccept() {
		final boolean shouldAccept = teacherThrottle.initiateReconnect(peerId.getId());
		if (shouldAccept) {
			initiatedBy = InitiatedBy.PEER;
		}
		return shouldAccept;
	}

	@Override
	public boolean acceptOnSimultaneousInitiate() {
		// if both nodes fall behind, it makes no sense to reconnect with each other
		// also, it would not be clear who the teacher and who the learner is
		return false;
	}

	@Override
	public void runProtocol(final Connection connection)
			throws NetworkProtocolException, IOException, InterruptedException {
		try {
			switch (initiatedBy) {
				case PEER -> teacher(connection);
				case SELF -> reconnectController.provideLearnerConnection(connection);
				default -> throw new NetworkProtocolException(
						"runProtocol() called but it is unclear who the teacher and who the learner is");
			}
		} finally {
			initiatedBy = InitiatedBy.NO_ONE;
		}
	}

	private void teacher(final Connection connection) {
		try {
			// this method reserves the signed state which is later manually released by the ReconnectTeacher
			final SignedState state = lastCompleteSignedState.get();
			new ReconnectTeacher(
					connection,
					state,
					reconnectSocketTimeout,
					connection.getSelfId().getId(),
					connection.getOtherId().getId(),
					state.getRound(),
					statistics).execute();
		} finally {
			teacherThrottle.markReconnectFinished();
		}
	}

	private enum InitiatedBy {
		NO_ONE,
		SELF,
		PEER
	}
}
