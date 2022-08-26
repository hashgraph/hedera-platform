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

package com.swirlds.common.system;

import com.swirlds.common.system.events.ConsensusEvent;

import java.util.Iterator;

/**
 * A collection of unique events that reached consensus at the same time. The consensus data for every event in the round
 * will never change, and no more events will ever be added to the round. A round with a lower round number will always
 * reach consensus before a round with a higher round number.
 * <p>
 * IMPORTANT: Although this interface is not sealed, it should only be implemented by internal classes. This
 * interface may be changed at any time, in any way, without notice or prior deprecation. Third parties should NOT
 * implement this interface.
 */
public interface Round {

	/**
	 * An iterator for all consensus events in this round. Each invocation returns a new iterator over the same events.
	 * This method is thread safe.
	 *
	 * @return an iterator of consensus events
	 */
	Iterator<ConsensusEvent> eventIterator();

	/**
	 * Provides the unique round number for this round. Lower numbers reach consensus before higher numbers. This
	 * method is thread safe.
	 *
	 * @return the round number
	 */
	long getRoundNum();
}
