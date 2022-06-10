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

package com.swirlds.platform;

import com.swirlds.common.system.PlatformStatus;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.platform.stats.TransactionStatistics;

import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.swirlds.common.utility.Units.NANOSECONDS_TO_MICROSECONDS;

/**
 * Submits valid transactions received from the application to a consumer. Invalid transactions are rejected.
 */
public class TransactionSubmitter {

	private final Supplier<PlatformStatus> platformStatusSupplier;
	private final boolean isZeroStakeNode;
	private final SettingsProvider settings;
	private final Predicate<Transaction> transactionConsumer;
	private final TransactionStatistics stats;

	/**
	 * Creates a new instance.
	 *
	 * @param platformStatusSupplier
	 * 		supplier of the current status of the platform
	 * @param settings
	 * 		provider of static settings
	 * @param isZeroStakeNode
	 * 		true is this node is a zero-stake node
	 * @param transactionConsumer
	 * 		a consumer of valid transactions
	 * @param stats
	 * 		stats relevant to transactions
	 */
	public TransactionSubmitter(
			final Supplier<PlatformStatus> platformStatusSupplier,
			final SettingsProvider settings,
			final boolean isZeroStakeNode,
			final Predicate<Transaction> transactionConsumer,
			final TransactionStatistics stats) {

		this.platformStatusSupplier = platformStatusSupplier;
		this.settings = settings;
		this.isZeroStakeNode = isZeroStakeNode;
		this.transactionConsumer = transactionConsumer;
		this.stats = stats;
	}

	/**
	 * Submits a transaction to the consumer if it passes validity checks.
	 *
	 * @param trans
	 * 		the transaction to submit
	 * @return true if the transaction passed all validity checks and was accepted by the consumer
	 */
	public boolean submitTransaction(final Transaction trans) {
		// no new transaction allowed during recover mode
		if (settings.isEnableStateRecovery()) {
			return false;
		}

		// if the platform is not active, it is better to reject transactions submitted by the app
		if (platformStatusSupplier.get() != PlatformStatus.ACTIVE) {
			return false;
		}

		// create a transaction to be added to the next Event when it is created.
		// The "system" boolean is set to false, because this is an app-generated transaction.
		// Refuse to create any type of transaction if the beta mirror is enabled and this node has zero stake
		if (settings.isEnableBetaMirror() && isZeroStakeNode) {
			return false;
		}

		if (trans == null) {
			return false;
		}

		// check if system transaction serialized size is above the required threshold
		if (trans.getSize() > settings.getTransactionMaxBytes()) {
			return false;
		}

		long start = System.nanoTime();
		boolean success = transactionConsumer.test(trans);
		stats.updateTransSubmitMicros((long) ((System.nanoTime() - start) * NANOSECONDS_TO_MICROSECONDS));

		return success;
	}

}
