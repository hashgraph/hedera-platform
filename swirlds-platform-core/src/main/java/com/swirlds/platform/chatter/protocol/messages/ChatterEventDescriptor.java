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

package com.swirlds.platform.chatter.protocol.messages;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.utility.CommonUtils;

import java.io.IOException;
import java.util.Objects;

/**
 * A stripped down description of an event.
 */
public class ChatterEventDescriptor implements SelfSerializable {

	public static final long CLASS_ID = 0x825e17f25c6e2566L;

	private static final class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	private Hash hash;
	private long creator;
	private long generation;

	private int hashCode;

	public ChatterEventDescriptor() {

	}

	/**
	 * Create a new gossip event descriptor.
	 *
	 * @param hash
	 * 		the hash of the event
	 * @param creator
	 * 		the creator of the event
	 * @param generation
	 * 		the age of an event, smaller is older
	 */
	public ChatterEventDescriptor(final Hash hash, final long creator, final long generation) {
		this.hash = Objects.requireNonNull(hash);
		this.creator = creator;
		this.generation = generation;

		hashCode = Objects.hash(hash, creator, generation);
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
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeSerializable(hash, false);
		out.writeLong(creator);
		out.writeLong(generation);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		hash = in.readSerializable(false, Hash::new);
		creator = in.readLong();
		generation = in.readLong();

		hashCode = Objects.hash(hash, creator, generation);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}

	/**
	 * Get the hash of the event.
	 *
	 * @return the event's hash
	 */
	public Hash getHash() {
		return hash;
	}

	/**
	 * Get the node ID of the event's creator.
	 *
	 * @return a node ID
	 */
	public long getCreator() {
		return creator;
	}

	/**
	 * Get the generation of the event described
	 *
	 * @return the generation of the event described
	 */
	public long getGeneration() {
		return generation;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final ChatterEventDescriptor that = (ChatterEventDescriptor) o;

		if (this.hashCode != that.hashCode) {
			return false;
		}

		return creator == that.creator && generation == that.generation && hash.equals(that.hash);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public String toString() {
		return String.format("GossipEventDescriptor(%d,%d,%s)",
				creator, generation, CommonUtils.hex(hash.getValue()));
	}
}
