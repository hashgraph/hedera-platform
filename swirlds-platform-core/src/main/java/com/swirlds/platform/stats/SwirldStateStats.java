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
	 *
	 * @param seconds
	 */
	void consensusTransHandleTime(double seconds);

	/**
	 * Records the amount of time between a transaction reaching consensus and being handled in {@link SwirldState}.
	 *
	 * @param seconds
	 */
	void consensusToHandleTime(double seconds);

	/**
	 * Records the fact that a consensus transaction was handled by {@link SwirldState}.
	 */
	void consensusTransHandled();

	/**
	 * Records the time it takes {@link SwirldState#copy()} to finish (in microseconds)
	 *
	 * @param micros
	 */
	void stateCopyMicros(final double micros);

	/**
	 * Records the time spent performing a shuffle in {@link com.swirlds.platform.state.SwirldStateManagerSingle}  (in
	 * microseconds).
	 *
	 * @param micros
	 */
	void shuffleMicros(final double micros);

	/**
	 * The average difference in creation time and consensus time for self events.
	 *
	 * @return
	 */
	double getAvgSelfCreatedTimestamp();

	/**
	 * The average difference in creation time and consensus time for other events.
	 *
	 * @return
	 */
	double getAvgOtherReceivedTimestamp();

	/**
	 * The start and end time of {@link SwirldState#noMoreTransactions()} execution
	 */
	void noMoreTransactionsMicros(final long start, final long end);

	void stateCopyAdmit(final long start, final long end);

	void preConsensusHandleTime(long start, final long end);
}
