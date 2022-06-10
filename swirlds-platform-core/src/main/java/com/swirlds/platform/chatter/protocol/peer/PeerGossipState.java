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
