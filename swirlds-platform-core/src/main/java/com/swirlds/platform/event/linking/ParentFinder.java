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

package com.swirlds.platform.event.linking;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.event.GossipEvent;

import java.util.function.Function;

/**
 * Looks up the parents of an event if they are not ancient
 */
public class ParentFinder {
	/** Look up an event by its hash */
	private final Function<Hash, EventImpl> eventByHash;

	public ParentFinder(final Function<Hash, EventImpl> eventByHash) {
		this.eventByHash = eventByHash;
	}

	/**
	 * An event's parent is required iff (a) the event does have that parent event, and (b) that
	 * parent event is non-ancient.
	 *
	 * @return true iff the event's parent is required
	 */
	private static boolean requiredParent(
			final GossipEvent event,
			final boolean selfParent,
			final long minGenerationNonAncient) {
		final long parentGeneration = selfParent
				? event.getHashedData().getSelfParentGen()
				: event.getHashedData().getOtherParentGen();
		// if an event does not have a parent, its generation will be EventConstants.GENERATION_UNDEFINED,
		// which is always smaller than minGenerationNonAncient
		return parentGeneration >= minGenerationNonAncient;
	}

	private EventImpl getParent(final GossipEvent event, final boolean selfParent) {
		final Hash parentHash = selfParent
				? event.getHashedData().getSelfParentHash()
				: event.getHashedData().getOtherParentHash();
		return eventByHash.apply(parentHash);
	}

	/**
	 * Looks for the events parents if they are not ancient
	 *
	 * @param event
	 * 		the event whose parents are looked for
	 * @param minGenerationNonAncient
	 * 		the generation below which all events are ancient
	 * @return a {@link ChildEvent} which may be an orphan
	 */
	public ChildEvent findParents(final GossipEvent event, final long minGenerationNonAncient) {
		final EventImpl selfParent;
		final EventImpl otherParent;
		final boolean missingSP;
		final boolean missingOP;
		if (requiredParent(event, true, minGenerationNonAncient)) {
			selfParent = getParent(event, true);
			missingSP = selfParent == null;
		} else {
			selfParent = null;
			missingSP = false;
		}
		if (requiredParent(event, false, minGenerationNonAncient)) {
			otherParent = getParent(event, false);
			missingOP = otherParent == null;
		} else {
			otherParent = null;
			missingOP = false;
		}
		return new ChildEvent(event, missingSP, missingOP, selfParent, otherParent);
	}

}
