/*
 * (c) 2016-2020 Swirlds, Inc.
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

package com.swirlds.common.crypto.engine;

import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;

import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * A signature verification capable {@link AsyncOperationHandler} implementation.
 *
 * Provides a generic way to process cryptographic transformations for a given {@link List} of work items in a
 * asynchronous manner on a background thread. This object also serves as the {@link java.util.concurrent.Future}
 * implementation assigned to each item contained in the {@link List}.
 */
public class AsyncVerificationHandler extends AsyncOperationHandler<TransactionSignature, VerificationProvider> {

	/**
	 * Constructs an {@link AsyncOperationHandler} which will operate on the provided {@link List} of items using the
	 * specified algorithm provider. This method does not make a copy of the list provided and expects exclusive access
	 * to the list.
	 *
	 * @param workItems
	 * 		the list of items to be asynchronously processed by the algorithm provider
	 * @param provider
	 * 		the algorithm provider used to perform cryptographic transformations on each item
	 */
	public AsyncVerificationHandler(final List<TransactionSignature> workItems, final VerificationProvider provider) {
		super(workItems, provider);
	}

	/**
	 * Constructs an {@link AsyncOperationHandler} which will operate on the provided {@link List} of items using the
	 * specified algorithm provider.
	 *
	 * @param workItems
	 * 		the list of items to be asynchronously processed by the algorithm provider
	 * @param shouldCopy
	 * 		if true, then a shallow copy of the provided list will be made; otherwise the original list will be used
	 * @param provider
	 * 		the algorithm provider used to perform cryptographic transformations on each item
	 */
	public AsyncVerificationHandler(final List<TransactionSignature> workItems, final boolean shouldCopy,
			final VerificationProvider provider) {
		super(workItems, shouldCopy, provider);
	}

	/**
	 * Called by the {@link #run()} method to process the cryptographic transformation for a single item on the
	 * background thread.
	 *
	 * @param provider
	 * 		the algorithm provider to use
	 * @param item
	 * 		the input to be transformed
	 * @throws NoSuchAlgorithmException
	 * 		if an implementation of the required algorithm cannot be located or loaded
	 */
	@Override
	protected void handleWorkItem(final VerificationProvider provider,
			final TransactionSignature item) throws NoSuchAlgorithmException {
		item.setFuture(this);
		final boolean isValid = provider.compute(item, item.getSignatureType());

		item.setSignatureStatus((isValid) ? VerificationStatus.VALID : VerificationStatus.INVALID);
	}
}
