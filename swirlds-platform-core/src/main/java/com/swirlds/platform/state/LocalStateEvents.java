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

package com.swirlds.platform.state;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.platform.EventImpl;

import java.io.IOException;

public class LocalStateEvents implements SelfSerializable {
	private static final long CLASS_ID = 0xadbb701819073c20L;
	private static final int VERSION_ORIGINAL = 1;
	private static final int CLASS_VERSION = VERSION_ORIGINAL;

	private EventImpl[] events;

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInt(events.length);
		for (EventImpl event : events) {
			out.writeSerializable(event.getBaseEventHashedData(), true);
			out.writeSerializable(event.getBaseEventHashedData().getHash(), true);
			out.writeSerializable(event.getBaseEventUnhashedData(), true);
		}
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		int eventNum = in.readInt();
		events = new EventImpl[eventNum];
		for (int i = 0; i < eventNum; i++) {
			BaseEventHashedData hashedData = in.readSerializable();
			Hash hash = in.readSerializable();
			BaseEventUnhashedData unhashedData = in.readSerializable();

			hashedData.setHash(hash);

			events[i] = new EventImpl(hashedData, unhashedData);
		}
	}

	public EventImpl[] getEvents() {
		return events;
	}

	public void setEvents(EventImpl[] events) {
		this.events = events;
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return CLASS_VERSION;
	}
}
