/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.common.crypto.engine;

import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * A signature verification capable {@link AsyncOperationHandler} implementation.
 *
 * <p>Provides a generic way to process cryptographic transformations for a given {@link List} of
 * work items in a asynchronous manner on a background thread. This object also serves as the {@link
 * java.util.concurrent.Future} implementation assigned to each item contained in the {@link List}.
 */
public class AsyncVerificationHandler
        extends AsyncOperationHandler<
                TransactionSignature,
                OperationProvider<TransactionSignature, Void, Boolean, ?, SignatureType>> {
    /**
     * Constructs an {@link AsyncOperationHandler} which will operate on the provided {@link List}
     * of items using the specified algorithm provider. This method does not make a copy of the list
     * provided and expects exclusive access to the list.
     *
     * @param workItems the list of items to be asynchronously processed by the algorithm provider
     * @param provider the algorithm provider used to perform cryptographic transformations on each
     *     item
     */
    public AsyncVerificationHandler(
            final List<TransactionSignature> workItems,
            final OperationProvider<TransactionSignature, Void, Boolean, ?, SignatureType>
                    provider) {
        super(workItems, provider);
    }

    /**
     * Constructs an {@link AsyncOperationHandler} which will operate on the provided {@link List}
     * of items using the specified algorithm provider.
     *
     * @param workItems the list of items to be asynchronously processed by the algorithm provider
     * @param shouldCopy if true, then a shallow copy of the provided list will be made; otherwise
     *     the original list will be used
     * @param provider the algorithm provider used to perform cryptographic transformations on each
     *     item
     */
    public AsyncVerificationHandler(
            final List<TransactionSignature> workItems,
            final boolean shouldCopy,
            final OperationProvider<TransactionSignature, Void, Boolean, ?, SignatureType>
                    provider) {
        super(workItems, shouldCopy, provider);
    }

    /**
     * Called by the {@link #run()} method to process the cryptographic transformation for a single
     * item on the background thread.
     *
     * @param provider the algorithm provider to use
     * @param item the input to be transformed
     * @throws NoSuchAlgorithmException if an implementation of the required algorithm cannot be
     *     located or loaded
     */
    @Override
    protected void handleWorkItem(
            final OperationProvider<TransactionSignature, Void, Boolean, ?, SignatureType> provider,
            final TransactionSignature item)
            throws NoSuchAlgorithmException {
        item.setFuture(this);
        final boolean isValid = provider.compute(item, item.getSignatureType());
        item.setSignatureStatus(isValid ? VerificationStatus.VALID : VerificationStatus.INVALID);
    }
}
