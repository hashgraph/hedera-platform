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

package com.swirlds.platform.event;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.chatter.protocol.messages.ChatterEventDescriptor;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.time.Instant;

/**
 * A class used to hold information about an event transferred through gossip
 */
public class GossipEvent implements EventIntakeTask, BaseEvent, ChatterEvent {
	private static final long CLASS_ID = 0xfe16b46795bfb8dcL;
	private BaseEventHashedData hashedData;
	private BaseEventUnhashedData unhashedData;
	private ChatterEventDescriptor descriptor;
	private Instant timeReceived;

	@SuppressWarnings("unused") // needed for RuntimeConstructable
	public GossipEvent() {
	}

	/**
	 * @param hashedData
	 * 		the hashed data for the event
	 * @param unhashedData
	 * 		the unhashed data for the event
	 */
	public GossipEvent(final BaseEventHashedData hashedData, final BaseEventUnhashedData unhashedData) {
		this.hashedData = hashedData;
		this.unhashedData = unhashedData;
		this.timeReceived = Instant.now();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeSerializable(hashedData, false);
		out.writeSerializable(unhashedData, false);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		hashedData = in.readSerializable(false, BaseEventHashedData::new);
		unhashedData = in.readSerializable(false, BaseEventUnhashedData::new);
		timeReceived = Instant.now();
	}

	/**
	 * Get the hashed data for the event.
	 */
	@Override
	public BaseEventHashedData getHashedData() {
		return hashedData;
	}

	/**
	 * Get the unhashed data for the event.
	 */
	@Override
	public BaseEventUnhashedData getUnhashedData() {
		return unhashedData;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ChatterEventDescriptor getDescriptor() {
		return descriptor;
	}

	/**
	 * Build the descriptor of this event. This cannot be done when the event is first instantiated, it needs to be
	 * hashed before the descriptor can be built.
	 */
	public void buildDescriptor() {
		this.descriptor = new ChatterEventDescriptor(
				hashedData.getHash(),
				hashedData.getCreatorId(),
				hashedData.getGeneration()
		);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Instant getTimeReceived() {
		return timeReceived;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return EventStrings.toMediumString(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;

		if (o == null || getClass() != o.getClass()) return false;

		final GossipEvent that = (GossipEvent) o;

		return new EqualsBuilder()
				.append(hashedData, that.hashedData)
				.append(unhashedData, that.unhashedData)
				.append(descriptor, that.descriptor)
				.isEquals();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(hashedData).append(unhashedData).append(descriptor).toHashCode();
	}



	private static final class ClassVersion {
		public static final int ORIGINAL = 1;
	}
}
