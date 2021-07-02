/*
 * (c) 2016-2021 Swirlds, Inc.
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

import com.swirlds.platform.Consensus;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A type which defines whether either node has fallen-behind the other. This condition is determined solely
 * by the gossip session for which this type is used.
 */
public class SyncFallenBehind {

	/**
	 * defined by this type
	 */
	private final AtomicBoolean otherHasFallenBehind = new AtomicBoolean(false);

	/**
	 * defined by this type
	 */
	private final AtomicBoolean selfHasFallenBehind = new AtomicBoolean(false);

	/**
	 * For this node, this is the minimum famous witness generation number from the maximum round which
	 * for which the fame of all witnesses has been decided. Cached when these values are initialized.
	 */
	private volatile long selfMaxRoundGeneration;

	/**
	 * For this node, this is the minimum famous witness generation number from the minimum
	 * (oldest) non-expired round. Cached when these values are initialized.
	 */
	private volatile long selfMinRoundGeneration;

	/**
	 * For the remote node, this is the minimum famous witness generation number from the maximum round which
	 * for which the fame of all witnesses has been decided. Cached when these values are initialized.
	 */
	private volatile long otherMaxRoundGeneration;

	/**
	 * For the remote node, this is the minimum famous witness generation number from the minimum
	 * (oldest) non-expired round. Cached when these values are initialized.
	 */
	private volatile long otherMinRoundGeneration;

	/**
	 * Default ctor does nothing
	 */
	public SyncFallenBehind() {
		// This ctor does nothing.
	}


	/**
	 * Get the minimum generation number of the witnesses in the minimum non-expired round, for this node.
	 *
	 * @return the generation number
	 */
	public long getSelfMinRoundGeneration() {
		return selfMinRoundGeneration;
	}

	/**
	 * Get the minimum generation number of the witnesses in the most recent to have reached consensus, for this node.
	 *
	 * @return the generation number
	 */
	public long getSelfMaxRoundGeneration() {
		return selfMaxRoundGeneration;
	}

	/**
	 * Get the minimum generation number of the witnesses in the minimum non-expired round, for the remote node.
	 *
	 * @return the generation number
	 */
	public long getOtherMinRoundGeneration() {
		return otherMinRoundGeneration;
	}

	/**
	 * Get the minimum generation number of the witnesses in the most recent to have reached consensus, for the remote
	 * node.
	 *
	 * @return the generation number
	 */
	public long getOtherMaxRoundGeneration() {
		return otherMaxRoundGeneration;
	}

	/**
	 * Set the min and max generation numbers for this node.
	 *
	 * @param consensus
	 * 		the {@link Consensus} implementor to use
	 */
	public void setSelfGenerations(final Consensus consensus) {
		setSelfGenerations(consensus.getMinRoundGeneration(), consensus.getMaxRoundGeneration());
	}

	/**
	 * Set the min and max generation numbers for this node.
	 *
	 * @param max
	 * 		min round generation
	 * @param min
	 * 		max round generation
	 */
	public void setSelfGenerations(final long min, final long max) {
		selfMaxRoundGeneration = max;
		selfMinRoundGeneration = min;
	}

	/**
	 * Set the min and max generation numbers for the other-node.
	 *
	 * @param max
	 * 		min round generation
	 * @param min
	 * 		max round generation
	 */
	public void setOtherGenerations(final long min, final long max) {
		otherMaxRoundGeneration = max;
		otherMinRoundGeneration = min;
	}

	/**
	 * Detect whether this node or the remote node has fallen behind.
	 */
	public void detect() {
		detectSelfHasFallenBehind();
		detectOtherHasFallenBehind();
	}

	/**
	 * Detect whether this node has fallen behind its remote peer.
	 */
	private void detectSelfHasFallenBehind() {
		if (selfMaxRoundGeneration >= 0 && otherMaxRoundGeneration >= 0) {
			selfHasFallenBehind.set(selfMaxRoundGeneration < otherMinRoundGeneration);
		} else {
			// We do not detect fallen-behind prior to either node's first fame-decided round.
			selfHasFallenBehind.set(false);
		}
	}

	/**
	 * Detect whether the remote node has fallen behind this node.
	 */
	private void detectOtherHasFallenBehind() {
		if (otherMaxRoundGeneration >= 0 && selfMaxRoundGeneration >= 0) {
			otherHasFallenBehind.set(otherMaxRoundGeneration < selfMinRoundGeneration);
		} else {
			// We do not detect fallen-behind prior to either node's first dame-decided round.
			otherHasFallenBehind.set(false);
		}
	}

	/**
	 * Has {@code this} node fallen behind the peer in this session?
	 *
	 * @return true iff {@code this} node has fallen behind its peer in this gossip session
	 */
	public boolean selfFallenBehind() {
		return selfHasFallenBehind.get();
	}

	/**
	 * Has the peer fallen behind this node?
	 *
	 * @return true iff the remote peer has fallen behind {@code this} node in this gossip session
	 */
	public boolean otherFallenBehind() {
		return otherHasFallenBehind.get();
	}

	/**
	 * Has either: this node fallen behind the remote node, or the remote node fallen behind this node?
	 *
	 * @return true iff either node has fallen behind
	 */
	public boolean detected() {
		return selfHasFallenBehind.get() || otherHasFallenBehind.get();
	}

}
