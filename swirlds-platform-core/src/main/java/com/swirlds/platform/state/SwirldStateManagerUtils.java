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

package com.swirlds.platform.state;

import com.swirlds.platform.components.TransThrottleSyncAndCreateRule;
import com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse;
import com.swirlds.platform.stats.SwirldStateStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.swirlds.common.utility.Units.NANOSECONDS_TO_MICROSECONDS;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse.PASS;
import static com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse.SYNC_AND_CREATE;

/**
 * A utility class with useful methods for implementations of {@link SwirldStateManager}.
 */
public final class SwirldStateManagerUtils {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();

	// prevent instantiation of a static utility class
	private SwirldStateManagerUtils() {

	}

	/**
	 * Gets an instance of the state to be used for signing.
	 *
	 * @param stateInfo
	 * 		the state info wrapper object to use for the fast copy
	 * @param stats
	 * 		object to record stats in
	 * @return the state to sign
	 */
	public static State getStateForSigning(final StateInfo stateInfo, final SwirldStateStats stats) {
		final State prevState = fastCopy(stateInfo, stats);

		final long startNoMoreTransactions = System.nanoTime();
		// this state will be saved, so there will be no more transactions sent to it
		try {
			prevState.getSwirldState().noMoreTransactions();
		} catch (final Exception e) {
			// defensive: catch exceptions from a bad app
			LOG.error(EXCEPTION.getMarker(), "exception in app during noMoreTransactions:", e);
		}
		stats.noMoreTransactionsMicros(startNoMoreTransactions, System.nanoTime());

		return prevState;
	}

	/**
	 * Performs a fast copy and returns the previous state. This method is invoked by the consensus handler thread,
	 * which is also the only thread that modifies the state.
	 */
	private static State fastCopy(final StateInfo stateInfo, final SwirldStateStats stats) {
		final long admitStart = System.nanoTime();
		final State prevState = stateInfo.getState();
		final long admitEnd = System.nanoTime();

		final long copyStart = System.nanoTime();

		// Create a fast copy
		final State copy = prevState.copy();

		// Increment the reference count because this reference becomes the new value
		copy.incrementReferenceCount();

		final long copyEnd = System.nanoTime();

		stateInfo.setState(copy);

		stats.stateCopyAdmit(admitStart, admitEnd);
		stats.stateCopyMicros((copyEnd - copyStart) * NANOSECONDS_TO_MICROSECONDS);

		return prevState;
	}

	/**
	 * @see TransThrottleSyncAndCreateRule#shouldSyncAndCreate
	 */
	public static TransThrottleSyncAndCreateRuleResponse shouldSyncAndCreate(final State consensusState) {
		// if current time is 1 minute before or during the freeze period, initiate a sync
		if (isInFreezePeriod(Instant.now().plus(1, ChronoUnit.MINUTES), consensusState)
				|| isInFreezePeriod(Instant.now(), consensusState)) {
			return SYNC_AND_CREATE;
		} else {
			return PASS;
		}
	}

	/**
	 * Determines if a {@code timestamp} is in a freeze period according to the provided state.
	 *
	 * @param timestamp
	 * 		the timestamp to check
	 * @param consensusState
	 * 		the state that contains the freeze periods
	 * @return true is the {@code timestamp} is in a freeze period
	 */
	public static boolean isInFreezePeriod(final Instant timestamp, final State consensusState) {
		final PlatformDualState dualState = consensusState.getPlatformDualState();
		return dualState != null && dualState.isInFreezePeriod(timestamp);
	}
}
