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

package com.swirlds.platform.event.creation;

import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.platform.EventImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.swirlds.logging.LogMarker.CREATE_EVENT;

/**
 * Event creation rules that are static and do not need to be instantiated
 */
public final class StaticCreationRules {
	private static final Logger LOG = LogManager.getLogger();

	private StaticCreationRules() {
	}

	/**
	 * A static implementation of {@link ParentBasedCreationRule} to disallow null other-parents
	 *
	 * @param selfParent
	 * 		a potential self-parent
	 * @param otherParent
	 * 		a potential other-parent
	 * @return DONT_CREATE if the otherParent is null, PASS otherwise
	 */
	@SuppressWarnings("unused") // selfParent is needed to conform to the ParentBasedCreationRule interface
	public static EventCreationRuleResponse nullOtherParent(final EventImpl selfParent, final EventImpl otherParent) {
		if (otherParent == null) {
			// we only have a null other-parent when creating a genesis event
			LOG.debug(CREATE_EVENT.getMarker(), "Not creating event because otherParent is null");
			return EventCreationRuleResponse.DONT_CREATE;
		}
		return EventCreationRuleResponse.PASS;
	}
}
