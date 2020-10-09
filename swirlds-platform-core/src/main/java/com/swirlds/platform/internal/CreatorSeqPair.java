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

package com.swirlds.platform.internal;

/**
 * immutable pair of (creator Id, sequence number) to use as a key in eventsByCreatorSeq *
 */
public class CreatorSeqPair implements Comparable<CreatorSeqPair> {
	private final long creatorId;
	private final long seq;
	private final int hash;
	// for hash: 16-bit primes (and so coprime to each other and the implied modulus of 2^32)
	private final int mult1 = 43261;
	private final int mult2 = 53927;

	/** instantiate an immutable pair, usable as a key for HashMap */
	public CreatorSeqPair(Long creatorId, Long seq) {
		this.creatorId = creatorId;
		this.seq = seq;
		hash = (int) (creatorId * mult1 + seq * mult2);
	}

	@Override
	public boolean equals(Object other) { // overrides Object.equals for use in HashMap
		if (!(other instanceof CreatorSeqPair)) {
			return false;
		}
		CreatorSeqPair x = (CreatorSeqPair) other;
		return creatorId == x.creatorId && seq == x.seq;
	}

	@Override
	public int hashCode() { // overrides Object.hashCode for use in HashMap
		return hash;
	}

	@Override
	public int compareTo(CreatorSeqPair o) {
		int ids = Long.compare(this.creatorId, o.creatorId);
		if (ids == 0) {
			return Long.compare(this.seq, o.seq);
		} else {
			return ids;
		}
	}

	@Override
	public String toString() {
		return "(" + creatorId + "," + seq + ")";
	}

	public long getCreatorId() {
		return creatorId;
	}

	public long getSeq() {
		return seq;
	}
}
