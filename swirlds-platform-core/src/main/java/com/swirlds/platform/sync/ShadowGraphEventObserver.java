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

package com.swirlds.platform.sync;

import com.swirlds.platform.ConsensusRound;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import com.swirlds.platform.observers.EventAddedObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * Observes events and consensus in order to update the {@link ShadowGraph}
 */
public class ShadowGraphEventObserver implements EventAddedObserver, ConsensusRoundObserver {
	private static final Logger LOG = LogManager.getLogger();
	private final ShadowGraph shadowGraph;

	public ShadowGraphEventObserver(final ShadowGraph shadowGraph) {
		this.shadowGraph = shadowGraph;
	}

	/**
	 * Expire events in {@link ShadowGraph} based on the new minimum round generation
	 *
	 * @param consensusRound
	 * 		a new consensus round
	 */
	@Override
	public void consensusRound(final ConsensusRound consensusRound) {
		shadowGraph.expireBelow(consensusRound.getGenerations().getMinRoundGeneration());
	}

	/**
	 * Add an event to the {@link ShadowGraph}
	 *
	 * @param event
	 * 		the event to add
	 */
	@Override
	public void eventAdded(final EventImpl event) {
		try {
			shadowGraph.addEvent(event);
		} catch (final ShadowGraphInsertionException e) {
			LOG.error(EXCEPTION.getMarker(), "failed to add event {} to shadow graph",
					EventStrings.toMediumString(event), e);
		}
	}
}
