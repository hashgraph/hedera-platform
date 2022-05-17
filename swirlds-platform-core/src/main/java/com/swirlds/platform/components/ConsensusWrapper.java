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

package com.swirlds.platform.components;

import com.swirlds.common.AddressBook;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.ConsensusRound;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.sync.SyncGenerations;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * A wrapper for the consensus algorithm that returns {@link ConsensusRound} objects instead of {@link EventImpl}
 * objects. This class can be removed if/when the consensus interface is modified to return rounds.
 */
public class ConsensusWrapper {

	private final Supplier<Consensus> consensusSupplier;

	public ConsensusWrapper(final Supplier<Consensus> consensusSupplier) {
		this.consensusSupplier = consensusSupplier;
	}

	public List<ConsensusRound> addEvent(final EventImpl event, final AddressBook addressBook) {
		final List<EventImpl> consensusEvents = consensusSupplier.get().addEvent(event, addressBook);
		if (consensusEvents == null || consensusEvents.isEmpty()) {
			return null;
		}

		final SortedMap<Long, List<EventImpl>> roundEvents = new TreeMap<>();
		for (final EventImpl cEvent : consensusEvents) {
			final long round = cEvent.getRoundReceived();
			roundEvents.putIfAbsent(round, new LinkedList<>());
			roundEvents.get(round).add(cEvent);
		}

		final List<ConsensusRound> rounds = new LinkedList<>();
		for (final Map.Entry<Long, List<EventImpl>> entry : roundEvents.entrySet()) {
			rounds.add(new ConsensusRound(entry.getValue(), new SyncGenerations(consensusSupplier.get())));
		}

		return rounds;
	}
}
