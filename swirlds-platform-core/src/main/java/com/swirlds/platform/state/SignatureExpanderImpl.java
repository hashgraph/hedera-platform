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

import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.platform.CryptoStatistics;
import com.swirlds.platform.components.SignatureExpander;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;
import java.util.List;

import static com.swirlds.common.Units.NANOSECONDS_TO_MILLISECONDS;
import static com.swirlds.logging.LogMarker.EXCEPTION;

public class SignatureExpanderImpl implements SignatureExpander {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void expandSignatures(final Transaction[] transactions, final State consensusState) {
		// Expand signatures for the given transactions
		// Additionally, we should enqueue any signatures for verification
		final long startTime = System.nanoTime();
		final List<TransactionSignature> signatures = new LinkedList<>();

		double expandTime = 0;

		for (final Transaction t : transactions) {
			if (t.isSystem()) {
				continue;
			}
			try {
				final SwirldTransaction swirldTransaction = (SwirldTransaction) t;
				final long expandStart = System.nanoTime();
				if (consensusState != null) {
					consensusState.getSwirldState().expandSignatures(swirldTransaction);
				}
				final long expandEnd = (System.nanoTime() - expandStart);
				expandTime += expandEnd * NANOSECONDS_TO_MILLISECONDS;

				// expand signatures for application transaction
				final List<TransactionSignature> sigs = swirldTransaction.getSignatures();

				if (sigs != null && !sigs.isEmpty()) {
					signatures.addAll(sigs);
				}
			} catch (final Exception ex) {
				LOG.error(EXCEPTION.getMarker(),
						"expandSignatures threw an unhandled exception", ex);
			}
		}

		CryptoFactory.getInstance().verifyAsync(signatures);

		final double elapsedTime = (System.nanoTime() - startTime) * NANOSECONDS_TO_MILLISECONDS;
		CryptoStatistics.getInstance().setPlatformSigIntakeValues(elapsedTime, expandTime);
	}
}
