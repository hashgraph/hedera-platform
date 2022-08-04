/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
