/*
 * (c) 2016-2022 Swirlds, Inc.
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

package com.swirlds.platform.sync;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.platform.consensus.GraphGenerations;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.IOException;

public class SyncGenerations implements GraphGenerations, SelfSerializable {
	private static final long CLASS_ID = 0x2d745f265302ccfbL;

	private static final class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	/** The minimum famous witness generation number from the minimum (oldest) non-expired round. */
	private long minRoundGeneration;
	/** the minimum generation of all the judges that are not ancient */
	private long minGenNonAncient;
	/**
	 * The minimum famous witness generation number from the maximum round which for which the fame of all witnesses has
	 * been decided.
	 */
	private long maxRoundGeneration;

	/**
	 * No-args constructor for RuntimeConstructable
	 */
	public SyncGenerations() {
	}

	public SyncGenerations(final long minRoundGeneration, final long minGenNonAncient, final long maxRoundGeneration) {
		this.minRoundGeneration = minRoundGeneration;
		this.minGenNonAncient = minGenNonAncient;
		this.maxRoundGeneration = maxRoundGeneration;
		checkGenerations();
	}

	@Override
	public long getMinRoundGeneration() {
		return minRoundGeneration;
	}

	@Override
	public long getMinGenerationNonAncient() {
		return minGenNonAncient;
	}

	@Override
	public long getMaxRoundGeneration() {
		return maxRoundGeneration;
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeLong(minRoundGeneration);
		out.writeLong(minGenNonAncient);
		out.writeLong(maxRoundGeneration);
	}

	@Override
	public void deserialize(final SerializableDataInputStream in, int version) throws IOException {
		minRoundGeneration = in.readLong();
		minGenNonAncient = in.readLong();
		maxRoundGeneration = in.readLong();
		checkGenerations();
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
	public String toString() {
		return new ToStringBuilder(this)
				.append("minRoundGeneration", minRoundGeneration)
				.append("minGenNonAncient", minGenNonAncient)
				.append("maxRoundGeneration", maxRoundGeneration)
				.toString();
	}

	/**
	 * Check if the generation numbers conform to constraints
	 */
	private void checkGenerations() {
		if (minRoundGeneration < GraphGenerations.FIRST_GENERATION) {
			throw new IllegalArgumentException(
					"minRoundGeneration cannot be smaller than " + GraphGenerations.FIRST_GENERATION + "! " + this);
		}
		if (minGenNonAncient < minRoundGeneration) {
			throw new IllegalArgumentException("minGenNonAncient cannot be smaller than minRoundGeneration! " + this);
		}

		if (maxRoundGeneration < minGenNonAncient) {
			throw new IllegalArgumentException("maxRoundGeneration cannot be smaller than minGenNonAncient! " + this);
		}
	}
}
