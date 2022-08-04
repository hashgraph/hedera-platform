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

package com.swirlds.common.merkle.utility;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;

import java.io.IOException;
import java.util.Objects;

public class SerializableLong implements FastCopyable, SelfSerializable {

	private static final long CLASS_ID = 0x70deca6058a40bc6L;

	/**
	 * Both the copy and the current object are mutable after this method returns.
	 * {@inheritDoc}
	 */
	@Override
	public SerializableLong copy() {
		return new SerializableLong(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void release() {

	}

	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	private long value;

	public SerializableLong() {

	}

	public SerializableLong(final long value) {
		this.value = value;
	}

	public long getValue() {
		return this.value;
	}

	public void increment() {
		this.value++;
	}

	public void decrement() {
		this.value--;
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(this.value);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		this.value = in.readLong();
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof SerializableLong)) {
			return false;
		}

		final SerializableLong that = (SerializableLong) o;
		return value == that.value;
	}

	@Override
	public int hashCode() {
		return Objects.hash(value);
	}

}
