/*
 * (c) 2016-2020 Swirlds, Inc.
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

package com.swirlds.common.merkle.utility;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;
import java.util.Objects;

public class SerializableLong implements FastCopyable<SerializableLong>, SelfSerializable {

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
