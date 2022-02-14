/*
 * (c) 2016-2022 Swirlds, Inc.
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

package com.swirlds.platform.stats;

import com.swirlds.common.SwirldState;

/**
 * Interface for updating statistics relevant to {@code EventFlow}
 */
public interface EventFlowStats {

	/**
	 * Records the time it took to create a new SignedState in seconds.
	 *
	 * @param seconds
	 */
	void recordNewSignedStateTime(double seconds);

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
	 * Records the time spent processing event transactions by the {@code EventFlow#doCons()} method (in microseconds)
	 * including taking from the source queue and adding to the destination queue.
	 *
	 * @param micros
	 */
	void consShuffleMicros(final double micros);

	/**
	 * Records the time spent by the {@code EventFlow#doCons()} method hashing the event returned from {@code
	 * EventFlow.takeHandlePut()} (in microseconds)
	 *
	 * @param micros
	 */
	void consEventHashMicros(final double micros);

	/**
	 * Records the time it takes {@link SwirldState#copy()} to finish (in microseconds)
	 *
	 * @param micros
	 */
	void stateCopyMicros(final double micros);

	/**
	 * Records the time spent by the {@code EventFlow#doCons()} method waiting on the running hash future (in
	 * microseconds)
	 *
	 * @param micros
	 */
	void consRunningHashMicros(final double micros);

	/**
	 * Records the time spent by the {@code EventFlow#doCons()} method placing the {@code SignedState} into the signing
	 * queue (in microseconds)
	 *
	 * @param micros
	 */
	void consStateSignAdmitMicros(final double micros);

	/**
	 * Records the time spent in the {@code EventFlow#doCons()} method building the {@code SignedState} (in
	 * microseconds)
	 *
	 * @param micros
	 */
	void consBuildStateMicros(final double micros);

	/**
	 * Records the time spent by the {@code EventFlow#doCons()} method cleaning up the forSigs queue (in microseconds).
	 *
	 * @param micros
	 */
	void consForSigCleanMicros(final double micros);

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
}
