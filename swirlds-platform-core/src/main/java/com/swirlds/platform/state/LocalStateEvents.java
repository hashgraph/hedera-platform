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
