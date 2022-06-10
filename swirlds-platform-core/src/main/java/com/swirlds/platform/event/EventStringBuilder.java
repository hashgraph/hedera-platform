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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.EventImpl;

/**
 * A class used to convert an event into a string
 */
public final class EventStringBuilder {
	/** number of bytes of a hash to write */
	private static final int NUM_BYTES_HASH = 4;

	/** used for building the event string */
	private final StringBuilder sb = new StringBuilder();
	/** hashed data of an event */
	private final BaseEventHashedData hashedData;
	/** unhashed data of an event */
	private final BaseEventUnhashedData unhashedData;


	private EventStringBuilder(
			final BaseEventHashedData hashedData,
			final BaseEventUnhashedData unhashedData) {
		this.hashedData = hashedData;
		this.unhashedData = unhashedData;
	}

	private EventStringBuilder(final String errorString) {
		this(null, null);
		sb.append(errorString);
	}

	public static EventStringBuilder builder(final EventImpl event) {
		if (event == null) {
			return new EventStringBuilder("(EventImpl=null)");
		}
		return builder(event.getBaseEvent());
	}

	public static EventStringBuilder builder(final BaseEvent event) {
		if (event == null) {
			return new EventStringBuilder("(BaseEvent=null)");
		}
		return builder(event.getHashedData(), event.getUnhashedData());
	}

	public static EventStringBuilder builder(final BaseEventHashedData hashedData,
			final BaseEventUnhashedData unhashedData) {
		if (hashedData == null) {
			return new EventStringBuilder("(HashedData=null)");
		}
		if (unhashedData == null) {
			return new EventStringBuilder("(UnhashedData=null)");
		}

		return new EventStringBuilder(hashedData, unhashedData);
	}

	private boolean isNull() {
		return hashedData == null || unhashedData == null;
	}

	public EventStringBuilder appendEvent() {
		if (isNull()) {
			return this;
		}
		appendShortEvent(
				hashedData.getCreatorId(),
				hashedData.getGeneration(),
				hashedData.getHash());
		return this;
	}

	public EventStringBuilder appendSelfParent() {
		if (isNull()) {
			return this;
		}
		sb.append(" sp");
		appendShortEvent(
				hashedData.getCreatorId(),
				hashedData.getSelfParentGen(),
				hashedData.getSelfParentHash());
		return this;
	}

	public EventStringBuilder appendOtherParent() {
		if (isNull()) {
			return this;
		}
		sb.append(" op");
		appendShortEvent(
				unhashedData.getOtherId(),
				hashedData.getOtherParentGen(),
				hashedData.getOtherParentHash());
		return this;
	}

	/**
	 * Append a short string representation of an event with the supplied information
	 *
	 * @param creatorId
	 * 		creator ID of the event
	 * @param generation
	 * 		generation of the event
	 * @param hash
	 * 		the hash of the event
	 */
	private void appendShortEvent(
			final long creatorId,
			final long generation,
			final Hash hash) {
		sb.append('(');
		if (creatorId == EventConstants.CREATOR_ID_UNDEFINED || generation == EventConstants.GENERATION_UNDEFINED) {
			sb.append("none)");
			return;
		}
		sb.append(creatorId)
				.append(',')
				.append(generation)
				.append(',');
		appendHash(hash);
		sb.append(')');
	}

	/**
	 * Append the shortened hash value to the StringBuilder
	 *
	 * @param hash
	 * 		the hash to append
	 */
	private void appendHash(final Hash hash) {
		if (hash == null) {
			sb.append("null");
		} else {
			sb.append(CommonUtils.hex(hash.getValue(), NUM_BYTES_HASH));
		}
	}

	public String build() {
		return sb.toString();
	}
}
