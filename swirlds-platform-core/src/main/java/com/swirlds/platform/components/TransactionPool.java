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

import com.swirlds.common.system.EventCreationRule;
import com.swirlds.common.system.EventCreationRuleResponse;

public interface TransactionPool extends EventCreationRule {
	/**
	 * @return the number of user transactions in the pool
	 */
	int numUserTransForEvent();

	/**
	 * @return the number of freeze transactions in the pool
	 */
	int numFreezeTransEvent();

	/**
	 * {@inheritDoc}
	 */
	default EventCreationRuleResponse shouldCreateEvent() {
		if (numFreezeTransEvent() > 0) {
			return EventCreationRuleResponse.CREATE;
		} else {
			return EventCreationRuleResponse.PASS;
		}
	}
}
