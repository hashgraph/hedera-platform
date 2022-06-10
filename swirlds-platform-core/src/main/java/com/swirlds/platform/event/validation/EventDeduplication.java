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

package com.swirlds.platform.event.validation;

import com.swirlds.platform.chatter.protocol.messages.ChatterEventDescriptor;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.stats.EventIntakeStats;

import java.util.List;
import java.util.function.Predicate;

/**
 * A {@link GossipEventValidator} that checks a list of predicates to see if this event is a duplicate
 */
public class EventDeduplication implements GossipEventValidator {
	private final Predicate<ChatterEventDescriptor> isDuplicate;
	private final EventIntakeStats stats;

	public EventDeduplication(final Predicate<ChatterEventDescriptor> isDuplicateCheck, final EventIntakeStats stats) {
		this(List.of(isDuplicateCheck), stats);
	}

	public EventDeduplication(
			final List<Predicate<ChatterEventDescriptor>> isDuplicateChecks,
			final EventIntakeStats stats) {
		Predicate<ChatterEventDescriptor> chain = null;
		for (final Predicate<ChatterEventDescriptor> check : isDuplicateChecks) {
			if (chain == null) {
				chain = check;
			} else {
				chain = chain.or(check);
			}
		}
		this.isDuplicate = chain;
		this.stats = stats;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEventValid(final GossipEvent event) {
		final boolean duplicate = isDuplicate.test(event.getDescriptor());
		if (duplicate) {
			stats.duplicateEvent();
		} else {
			stats.nonDuplicateEvent();
		}
		return !duplicate;
	}
}
