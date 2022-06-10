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

import com.swirlds.platform.event.GossipEvent;

import java.util.List;

/**
 * A {@link GossipEventValidator} which combines multiple validators to provide a single output
 */
public class GossipEventValidators implements GossipEventValidator {
	private final List<GossipEventValidator> validators;

	public GossipEventValidators(final List<GossipEventValidator> validators) {
		this.validators = validators;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEventValid(final GossipEvent event) {
		for (final GossipEventValidator validator : validators) {
			if (!validator.isEventValid(event)) {
				// if a single validation fails, the event is invalid
				return false;
			}
		}
		// if all checks pass, the event is valid
		return true;
	}
}
