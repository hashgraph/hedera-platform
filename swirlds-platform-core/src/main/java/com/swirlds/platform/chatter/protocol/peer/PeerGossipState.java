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

package com.swirlds.platform.chatter.protocol.peer;

import com.swirlds.platform.chatter.protocol.Purgable;
import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.chatter.protocol.messages.ChatterEventDescriptor;
import com.swirlds.platform.chatter.protocol.purgable.twomaps.PurgableDoubleMap;
import com.swirlds.platform.consensus.GraphGenerations;
import org.apache.commons.lang3.ObjectUtils;

/**
 * Keeps track of events we are sure the peer knows
 */
public class PeerGossipState implements Purgable {
	private final PurgableDoubleMap<ChatterEventDescriptor, ObjectUtils.Null> events =
			new PurgableDoubleMap<>(ChatterEventDescriptor::getGeneration);
	private long maxReceivedDescriptorGeneration = GraphGenerations.FIRST_GENERATION;

	/**
	 * Mark an event represented by this descriptor as known by the peer
	 *
	 * @param event
	 * 		the descriptor of the event the peer knows
	 */
	public synchronized void setPeerKnows(final ChatterEventDescriptor event) {
		events.put(event, ObjectUtils.NULL);
	}

	/**
	 * Query the state about the knowledge of this event
	 *
	 * @param event
	 * 		the descriptor of the event being queried
	 * @return true if the peer knows this event, false otherwise
	 */
	public synchronized boolean getPeerKnows(final ChatterEventDescriptor event) {
		return events.get(event) != null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void purge(final long olderThan) {
		events.purge(olderThan);
	}

	/**
	 * Handle the descriptor received by this peer
	 *
	 * @param descriptor
	 * 		the descriptor received
	 */
	public synchronized void handleDescriptor(final ChatterEventDescriptor descriptor) {
		maxReceivedDescriptorGeneration = Math.max(maxReceivedDescriptorGeneration, descriptor.getGeneration());
		setPeerKnows(descriptor);
	}

	/**
	 * Handle the event received by this peer
	 *
	 * @param event
	 * 		the event received
	 */
	public synchronized void handleEvent(final ChatterEvent event) {
		setPeerKnows(event.getDescriptor());
	}

	/**
	 * @return the maximum generation of all descriptors received from this peer
	 */
	public long getMaxReceivedDescriptorGeneration() {
		return maxReceivedDescriptorGeneration;
	}
}
