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

package com.swirlds.platform.components;

import com.swirlds.common.system.EventCreationRule;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.EventAddedObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static com.swirlds.common.system.EventCreationRuleResponse.DONT_CREATE;
import static com.swirlds.common.system.EventCreationRuleResponse.PASS;
import static com.swirlds.logging.LogMarker.STARTUP;


/**
 * Allows nodes to wait for each other when starting up
 */
public class StartupThrottle implements EventAddedObserver, EventCreationRule, TransThrottleSyncAndCreateRule {
	private static final Logger log = LogManager.getLogger();

	/** number of members not started yet */
	private final AtomicLong numNotStarted;
	/** used to keep track of which members have started and decrease the numNotStarted value */
	private final AtomicReferenceArray<Boolean> nodesStarted;
	/** to notify is when there is a change */
	private final PlatformStatusManager statusManager;
	/** The ID of this node */
	private final NodeId selfId;

	/**
	 * Construct a {@code StartupThrottle} instance
	 *
	 * @param addressBook
	 * 		the {@code AddressBook} to use
	 * @param selfId
	 * 		the ID this node
	 * @param statusManager
	 * 		a {@code PlatformStatusManager} implementor
	 * @param enableBetaMirror
	 * 		true iff mirror nodes are enabled. See {@code Settings.enableBetaMirror}.
	 */
	public StartupThrottle(
			AddressBook addressBook,
			NodeId selfId,
			PlatformStatusManager statusManager,
			boolean enableBetaMirror) {
		this.statusManager = statusManager;
		this.selfId = selfId;

		int n = addressBook.getSize();
		if (enableBetaMirror) {
			// only count nodes with non-zero stake, excluding self from the count if the local node has stake
			int numStakedNodes = addressBook.getNumberWithStake();

			if (!addressBook.getAddress(selfId.getId()).isZeroStake()) {
				numStakedNodes--;
			}

			numNotStarted = new AtomicLong(numStakedNodes);
		} else {
			// lastSeq starts with n - 1 elements, so n haven't started
			numNotStarted = new AtomicLong(n);
		}

		nodesStarted = new AtomicReferenceArray<>(n);
	}

	/**
	 * Used by the noop class
	 */
	private StartupThrottle() {
		this.statusManager = null;
		this.selfId = null;
		this.nodesStarted = null;
		this.numNotStarted = null;
	}

	/**
	 * @return an instance of this class that does not throttle
	 */
	public static StartupThrottle getNoOpInstance() {
		return new DisabledStartupThrottle();
	}

	@Override
	public void eventAdded(EventImpl event) {
		nodeStarted(event.getCreatorId());
	}

	@Override
	public EventCreationRuleResponse shouldCreateEvent() {
		// If Settings.waitAtStartup is active, then there are some cases when it
		// shouldn't create the event. This member should only create a single event
		// prior to receiving at least one event from every member. And that event
		// should only be created after receiving an event from member 0 (except that
		// member 0 can create the event before receiving from self).

		// when there are some nodes haven't started yet,
		// if this node has created an event, but someone else hasn't, this node should not create anymore events
		// else if this node should wait for node 0 to create an event first, this node should not create any events
		if (numNotStarted.get() > 0 && (hasNodeStarted(selfId.getIdAsInt()) || shouldWaitForNode0())) {
			return DONT_CREATE;
		}
		return PASS;
	}

	/**
	 * If the node is not node0, it should wait for node0 to create an event first
	 *
	 * @return whether this node should wait for node0
	 */
	public boolean shouldWaitForNode0() {
		return selfId.getId() != 0 && !hasNodeStarted(0);
	}

	/**
	 * Update the values of parameters that keep track of which node has been started
	 *
	 * @param nodeId
	 * 		the id of the node that started
	 */
	public void nodeStarted(long nodeId) {
		// if all nodes have started, we do not need to do anything
		if (numNotStarted.get() > 0) {
			Boolean wasStarted = nodesStarted.getAndSet((int) nodeId, true);
			if (wasStarted == null) {
				// if this is the first event received for that member in all of history, or since the restart
				// then that member just stopped being "not started", so decrement the count
				numNotStarted.decrementAndGet();
			}
			log.info(STARTUP.getMarker(), "Node {} started with wasStarted {} and numNotStarted {}", nodeId,
					wasStarted, numNotStarted);
			statusManager.checkPlatformStatus();
		}
	}

	/**
	 * @return true if all nodes have started, false otherwise
	 */
	public boolean allNodesStarted() {
		return numNotStarted.get() == 0;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TransThrottleSyncAndCreateRuleResponse shouldSyncAndCreate() {
		if (!allNodesStarted()) {
			return TransThrottleSyncAndCreateRuleResponse.SYNC_AND_CREATE;
		} else {
			return TransThrottleSyncAndCreateRuleResponse.PASS;
		}
	}

	/**
	 * Returns whether a node has been started or not, by looking at whether we have received any events
	 * from this node
	 *
	 * @param nodeId
	 * 		the ID of the node
	 * @return true if is has started, false otherwise
	 */
	private boolean hasNodeStarted(int nodeId) {
		Boolean started = nodesStarted.get(nodeId);
		return started != null && started;
	}

	/**
	 * An implementation that does not throttle. This is a temporary implementation, at some point we
	 * should not depend on this class directly so we can just not instantiate it if its not needed
	 */
	private static class DisabledStartupThrottle extends StartupThrottle {
		@Override
		public void eventAdded(EventImpl event) {
			// does not track events, considers all nodes started
		}

		@Override
		public EventCreationRuleResponse shouldCreateEvent() {
			// Always pass this check
			return PASS;
		}

		@Override
		public void nodeStarted(long nodeId) {
			// does not track events, considers all nodes started
		}

		@Override
		public boolean allNodesStarted() {
			// Assume all nodes have started creating events
			return true;
		}

		@Override
		public boolean shouldWaitForNode0() {
			return false;
		}
	}
}
