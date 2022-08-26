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

package com.swirlds.platform.state;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.SwirldTransaction;
import com.swirlds.platform.CryptoStatistics;
import com.swirlds.platform.components.SignatureExpander;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;
import java.util.List;

import static com.swirlds.common.utility.Units.NANOSECONDS_TO_MILLISECONDS;
import static com.swirlds.logging.LogMarker.EXCEPTION;

public class SignatureExpanderImpl implements SignatureExpander {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void expandSignatures(final ConsensusTransaction[] transactions, final State consensusState) {
		// Expand signatures for the given transactions
		// Additionally, we should enqueue any signatures for verification
		final long startTime = System.nanoTime();
		final List<TransactionSignature> signatures = new LinkedList<>();

		double expandTime = 0;

		for (final ConsensusTransaction t : transactions) {
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
