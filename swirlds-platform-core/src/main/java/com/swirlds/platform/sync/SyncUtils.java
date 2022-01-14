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

import com.swirlds.platform.EventImpl;
import com.swirlds.platform.consensus.GraphGenerations;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Various static utility method used in syncing
 */
public final class SyncUtils {
	/**
	 * Private constructor to never instantiate this class
	 */
	private SyncUtils() {
	}

	/**
	 * Returns a predicate that determines if a {@link ShadowEvent}'s generation is non-ancient for the peer and greater
	 * than this node's minimum non-expired generation, and is not already known.
	 *
	 * @param knownShadows
	 * 		the {@link ShadowEvent}s that are already known and should therefore be rejected by the predicate
	 * @param myGenerations
	 * 		the generations of this node
	 * @param theirGenerations
	 * 		the generations of the peer node
	 * @return the predicate
	 */
	public static Predicate<ShadowEvent> unknownNonAncient(
			final Collection<ShadowEvent> knownShadows,
			final GraphGenerations myGenerations,
			final GraphGenerations theirGenerations) {
		long minSearchGen = Math.max(
				myGenerations.getMinRoundGeneration(),
				theirGenerations.getMinGenerationNonAncient()
		);
		return s -> s.getEvent().getGeneration() >= minSearchGen && !knownShadows.contains(s);
	}

	/**
	 * Computes the number of creators that have more than one tip. If a single creator has more than two tips, this
	 * method will only report once for each such creator. The execution time cost for this method is O(T + N) where
	 * T is the number of tips including all forks and N is the number of network nodes. There is some memory overhead,
	 * but it is fairly nominal in favor of the time complexity savings.
	 *
	 * @return the number of event creators that have more than one tip.
	 */
	public static int computeMultiTipCount(Iterable<ShadowEvent> tips) {
		// The number of tips per creator encountered when iterating over the sending tips
		final Map<Long, Integer> tipCountByCreator = new HashMap<>();

		// Make a single O(N) where N is the number of tips including all forks. Typically, N will be equal to the
		// number of network nodes.
		for (final ShadowEvent tip : tips) {
			tipCountByCreator.compute(tip.getEvent().getCreatorId(), (k, v) -> (v != null) ? (v + 1) : 1);
		}

		// Walk the entrySet() which is O(N) where N is the number network nodes. This is still more efficient than a
		// O(N^2) loop.
		int creatorsWithForks = 0;
		for (final Map.Entry<Long, Integer> entry : tipCountByCreator.entrySet()) {
			// If the number of tips for a given creator is greater than 1 then we have a fork.
			// This map is broken down by creator ID already as the key so this is guaranteed to be a single increment
			// for each creator with a fork. Therefore, this holds to the method contract.
			if (entry.getValue() > 1) {
				creatorsWithForks++;
			}
		}

		return creatorsWithForks; // total number of unique creators with more than one tip
	}

	/**
	 * @param sendList
	 * 		The list of events to sort.
	 */
	static void sort(final List<EventImpl> sendList) {
		sendList.sort((EventImpl e1, EventImpl e2) -> (int) (e1.getGeneration() - e2.getGeneration()));
	}
}
