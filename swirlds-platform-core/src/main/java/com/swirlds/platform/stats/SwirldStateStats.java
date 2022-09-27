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

import com.swirlds.common.system.SwirldState;

public interface SwirldStateStats {

	/**
	 * Records the amount of time to handle a consensus transaction in {@link SwirldState}.
	 */
	void consensusTransHandleTime(double seconds);

	/**
	 * Records the amount of time between a transaction reaching consensus and being handled in {@link SwirldState}.
	 */
	void consensusToHandleTime(double seconds);

	/**
	 * Records the fact that consensus transactions were handled by {@link SwirldState}.
	 */
	void consensusTransHandled(final int numTrans);

	/**
	 * Records the time it takes {@link SwirldState#copy()} to finish (in microseconds)
	 */
	void stateCopyMicros(final double micros);

	/**
	 * Records the time spent performing a shuffle in {@link com.swirlds.platform.state.SwirldStateManagerSingle}  (in
	 * microseconds).
	 */
	void shuffleMicros(final double micros);

	/**
	 * Returns the average difference in creation time and consensus time for self events.
	 */
	double getAvgSelfCreatedTimestamp();

	/**
	 * Returns the average difference in creation time and consensus time for other events.
	 */
	double getAvgOtherReceivedTimestamp();

	/**
	 * The amount of time it takes to handle a single event from the pre-consensus event queue.
	 */
	void preConsensusHandleTime(long start, final long end);

	/**
	 * The amount of time it takes to apply an event or a transaction, depending on which {@link SwirldState} the
	 * application implements.
	 */
	void preHandleTime(long start, long end);
}
