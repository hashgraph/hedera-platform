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

package com.swirlds.platform.components;

import com.swirlds.common.EventCreationRule;
import com.swirlds.common.EventCreationRuleResponse;

import java.util.List;

import static com.swirlds.common.EventCreationRuleResponse.PASS;

/**
 * This class is used for checking whether should create an event or not.
 * It contains a list of {@link EventCreationRule}s, which are checked one by one.
 * Once a rule has a firm answer such as CREATE or DONT_CREATE, the answer is returned; else we continue checking the
 * next rule.
 */
public class EventCreationRules {

	/**
	 * a list of rules based on which we check whether should create an event or not
	 */
	private final List<EventCreationRule> rules;

	public EventCreationRules(List<EventCreationRule> rules) {
		this.rules = rules;
	}

	/**
	 * check whether should create an event based on the rules
	 *
	 * @return whether should create an event based on the rules
	 */
	public EventCreationRuleResponse shouldCreateEvent() {
		for (EventCreationRule rule : rules) {
			EventCreationRuleResponse response = rule.shouldCreateEvent();
			// if the response is CREATE or DONT_CREATE, we should return
			// else we continue checking subsequent rules
			if (response != PASS) {
				return response;
			}
		}
		return PASS;
	}
}
