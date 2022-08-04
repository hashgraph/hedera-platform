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

package com.swirlds.platform.stats;

import com.swirlds.platform.observers.StaleEventObserver;


public interface EventIntakeStats extends DefaultStats, StaleEventObserver {
	/**
	 * Update a statistics accumulator whenever this node creates an event with
	 * an other-parent that has no children. (The OP is "rescued".)
	 */
	void rescuedEvent();

	/**
	 * Update a statistics accumulator when a duplicate event has been detected.
	 */
	void duplicateEvent();

	/**
	 * Update a statistics accumulator when a non-duplicate event has been detected
	 */
	void nonDuplicateEvent();

	/**
	 * Update event task statistics
	 * @param startTime a start time, in nanoseconds
	 */
	void processedEventTask(long startTime);

	/**
	 * Notifies the stats that the event creation phase has entered
	 *
	 * @param shouldCreateEvent
	 * 		did the sync manager tell us to create an event?
	 */
	void eventCreation(boolean shouldCreateEvent);
}
