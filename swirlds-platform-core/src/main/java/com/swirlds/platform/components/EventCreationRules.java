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
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.event.creation.ParentBasedCreationRule;

import java.util.List;

import static com.swirlds.common.system.EventCreationRuleResponse.PASS;

/**
 * This class is used for checking whether should create an event or not.
 * It contains a list of {@link EventCreationRule}s, which are checked one by one.
 * Once a rule has a firm answer such as CREATE or DONT_CREATE, the answer is returned; else we continue checking the
 * next rule.
 */
public class EventCreationRules implements EventCreationRule, ParentBasedCreationRule {
	private final List<EventCreationRule> basicRules;
	private final List<ParentBasedCreationRule> parentRules;

	public EventCreationRules(final List<EventCreationRule> rules) {
		this(rules, null);
	}

	public EventCreationRules(
			final List<EventCreationRule> basicRules,
			final List<ParentBasedCreationRule> parentRules) {
		this.basicRules = basicRules;
		this.parentRules = parentRules;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EventCreationRuleResponse shouldCreateEvent() {
		for (final EventCreationRule rule : basicRules) {
			final EventCreationRuleResponse response = rule.shouldCreateEvent();
			// if the response is CREATE or DONT_CREATE, we should return
			// else we continue checking subsequent rules
			if (response != PASS) {
				return response;
			}
		}
		return PASS;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EventCreationRuleResponse shouldCreateEvent(final EventImpl selfParent, final EventImpl otherParent) {
		if (parentRules == null) {
			return PASS;
		}
		for (final ParentBasedCreationRule rule : parentRules) {
			final EventCreationRuleResponse response = rule.shouldCreateEvent(selfParent, otherParent);
			if (response != PASS) {
				return response;
			}
		}
		return PASS;
	}
}
