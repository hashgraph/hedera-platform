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

package com.swirlds.platform.eventhandling;

import com.swirlds.platform.EventImpl;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Keeps events that need to be stored in the signed state when it is created. It makes sure it has last {@code
 * staleRound} number of rounds created at all times. It also makes sure there is at least one event from each node. If
 * the rounds that are kept do not contain events from a particular node, it will hold on to older events.
 */
public class SignedStateEventStorage {
	/** number of members in the network */
	private final int numMembers;
	/** a queue of all the latest events that need to be saved in state */
	private final Deque<EventImpl> queue = new LinkedList<>();
	/**
	 * an array that keeps the last consensus event by each member
	 */
	private final EventImpl[] lastConsEventByMember;
	/**
	 * an array that keeps track of whose last event is in the queue
	 */
	private final boolean[] lastConsEventInQueue;

	/** the latest round received */
	private long latestRoundReceived;

	/** the minimum round created in the queue */
	private long minRoundCreatedInQueue;


	public SignedStateEventStorage(final int numMembers) {
		this.numMembers = numMembers;
		lastConsEventByMember = new EventImpl[numMembers];
		lastConsEventInQueue = new boolean[numMembers];

		clear();
	}

	public synchronized void add(final List<EventImpl> events) {
		for (final EventImpl event : events) {
			add(event);
		}
	}

	public synchronized void add(final EventImpl event) {
		queue.add(event);
		// keep track of the last event created by each member
		lastConsEventByMember[(int) event.getCreatorId()] = event;
		lastConsEventInQueue[(int) event.getCreatorId()] = true;

		latestRoundReceived = event.getRoundReceived();
		updateMinRoundCreated(event);
	}

	public synchronized EventImpl[] getEventsForLatestRound() {
		// the saved state should contain at least one event by each member. if the member doesnt have an
		// event in the queue, we need to add an older event in the signed state
		final List<EventImpl> ssEventList = new ArrayList<>(queue.size() + lastConsEventByMember.length);
		for (int i = 0; i < lastConsEventInQueue.length; i++) {
			if (!lastConsEventInQueue[i] && lastConsEventByMember[i] != null) {
				ssEventList.add(lastConsEventByMember[i]);
			}
		}

		ssEventList.addAll(queue);
		return ssEventList.toArray(new EventImpl[] { });
	}

	public synchronized void expireEvents(final long minGenerationNonAncient) {
		minRoundCreatedInQueue = Long.MAX_VALUE;
		// we traverse the queue from newest events to oldest
		final Iterator<EventImpl> i = queue.iterator();
		while (i.hasNext()) {
			final EventImpl event = i.next();
			// once we reach an event whose round is stale, we remove all events before it
			if (event.getGeneration() < minGenerationNonAncient) {
				// if we are deleting the last event created by this member, we must keep track of it so we add it to
				// the saved state
				if (event == lastConsEventByMember[(int) event.getCreatorId()]) {
					lastConsEventInQueue[(int) event.getCreatorId()] = false;
				}
				i.remove();
			} else {
				updateMinRoundCreated(event);
			}
		}
	}

	private void updateMinRoundCreated(final EventImpl event) {
		minRoundCreatedInQueue = Math.min(minRoundCreatedInQueue, event.getRoundCreated());
	}

	public synchronized void clear() {
		for (int i = 0; i < numMembers; i++) {
			lastConsEventByMember[i] = null;
			lastConsEventInQueue[i] = false;
		}
		latestRoundReceived = -1;
		minRoundCreatedInQueue = Long.MAX_VALUE;
	}

	public synchronized void loadDataFromSignedState(
			final EventImpl[] signedStateEvents,
			final long minGenerationNonAncient) {
		for (final EventImpl event : signedStateEvents) {
			add(event);
		}
		expireEvents(minGenerationNonAncient);
	}

	public synchronized int getQueueSize() {
		return queue.size();
	}

	public synchronized long getLatestRoundReceived() {
		return latestRoundReceived;
	}

	public synchronized long getMinRoundCreatedInQueue() {
		return minRoundCreatedInQueue;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final SignedStateEventStorage that = (SignedStateEventStorage) o;

		return new EqualsBuilder().append(numMembers, that.numMembers).append(
				latestRoundReceived, that.latestRoundReceived).append(queue, that.queue).append(lastConsEventByMember,
				that.lastConsEventByMember).append(lastConsEventInQueue, that.lastConsEventInQueue).isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37).append(numMembers).append(queue).append(lastConsEventByMember).append(
				lastConsEventInQueue).append(latestRoundReceived).toHashCode();
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
				.append("numMembers", numMembers)
				.append("queue", queue)
				.append("lastConsEventByMember", lastConsEventByMember)
				.append("lastConsEventInQueue", lastConsEventInQueue)
				.append("latestRoundReceived", latestRoundReceived)
				.toString();
	}
}
