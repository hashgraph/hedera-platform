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

import java.util.List;

import static com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse.PASS;

/**
 * This class is used for checking whether should initiate a sync and create an event for that sync or not.
 * It contains a list of {@link TransThrottleSyncAndCreateRule}s, which are checked one by one.
 * Once a rule has a firm answer such as THROTTLE or DONT_THROTTLE, the answer is returned;
 * else we continue checking the
 * next rule.
 */
public class TransThrottleSyncAndCreateRules {

	/**
	 * a list of rules based on which we check whether should initiate a sync and create an event for that sync or not
	 */
	private final List<TransThrottleSyncAndCreateRule> rules;

	public TransThrottleSyncAndCreateRules(List<TransThrottleSyncAndCreateRule> rules) {
		this.rules = rules;
	}

	/**
	 * check whether this node should initiate a sync and create an event for that sync
	 *
	 * @return whether this node should initiate a sync and create an event for that sync
	 */
	public TransThrottleSyncAndCreateRuleResponse shouldSyncAndCreate() {
		for (TransThrottleSyncAndCreateRule rule : rules) {
			TransThrottleSyncAndCreateRuleResponse response = rule.shouldSyncAndCreate();
			// if the response is THROTTLE or DONT_THROTTLE, we should return
			// else we continue checking subsequent rules
			if (response != PASS) {
				return response;
			}
		}
		return PASS;
	}
}
