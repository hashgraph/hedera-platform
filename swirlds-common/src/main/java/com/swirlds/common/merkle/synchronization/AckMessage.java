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

package com.swirlds.common.merkle.synchronization;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;

/**
 * When the sender transmits a hash to the receiver, the receiver replies with an AckMessage.
 * The AckMessage informs the sender if the receiver's hash matches or not.
 */
public class AckMessage implements SelfSerializable {

	protected boolean affirmative;

	private final static int version = 1;

	private final static long classId = 0x7CBF61E166C6E5F7L;

	public AckMessage() {

	}

	public AckMessage(boolean affirmative) {
		this.affirmative = affirmative;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeBoolean(affirmative);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		affirmative = in.readBoolean();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return classId;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return version;
	}

	public boolean isAffirmative() {
		return affirmative;
	}
}
