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
package com.swirlds.common.test.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import com.swirlds.common.crypto.internal.CryptographySettings;
import com.swirlds.common.threading.futures.FuturePool;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;

public class TransactionSignatureTests {

    private static final CryptographySettings engineSettings =
            CryptographySettings.getDefaultSettings();
    private static final int PARALLELISM = 16;
    private static ExecutorService executorService;
    private static SignaturePool signaturePool;
    private static Cryptography cryptoProvider;

    @BeforeAll
    public static void startup() throws NoSuchAlgorithmException, NoSuchProviderException {
        assertNotNull(engineSettings);
        assertTrue(engineSettings.computeCpuDigestThreadCount() >= 1);

        cryptoProvider = CryptoFactory.getInstance();
        executorService = Executors.newFixedThreadPool(PARALLELISM);
        signaturePool =
                new SignaturePool(
                        engineSettings.computeCpuVerifierThreadCount() * PARALLELISM, 4096, true);
    }

    @AfterAll
    public static void shutdown() throws InterruptedException {
        executorService.shutdown();
        executorService.awaitTermination(2, TimeUnit.SECONDS);
    }

    /** Checks correctness of DigitalSignature batch size of exactly one message */
    @Test
    public void signatureSizeOfOne()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {
        final TransactionSignature singleSignature = signaturePool.next();

        assertNotNull(singleSignature);
        assertEquals(VerificationStatus.UNKNOWN, singleSignature.getSignatureStatus());

        cryptoProvider.verifySync(singleSignature);

        Future<Void> future = singleSignature.waitForFuture();
        assertNotNull(future);
        future.get();

        assertEquals(VerificationStatus.VALID, singleSignature.getSignatureStatus());
    }

    /** Checks correctness of DigitalSignature batch sizes less than the thread count */
    @Test
    public void signatureSizeUnderThreads()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {
        final TransactionSignature[] signatures =
                new TransactionSignature[engineSettings.computeCpuVerifierThreadCount() - 1];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = signaturePool.next();
        }

        cryptoProvider.verifySync(Arrays.asList(signatures));

        checkSignatures(signatures);
    }

    /** Checks correctness of DigitalSignature batch sizes more than the thread count */
    @Test
    public void signatureSizeOverThreads()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {
        final TransactionSignature[] signatures =
                new TransactionSignature[(engineSettings.computeCpuVerifierThreadCount() * 2) - 1];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = signaturePool.next();
        }

        cryptoProvider.verifySync(Arrays.asList(signatures));

        checkSignatures(signatures);
    }

    /** */
    @Test
    public void signatureHalfQueueSize()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {
        final TransactionSignature[] signatures =
                new TransactionSignature[engineSettings.getCpuVerifierQueueSize() / 2];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = signaturePool.next();
        }

        cryptoProvider.verifySync(Arrays.asList(signatures));

        checkSignatures(signatures);
    }

    /** */
    @Test
    public void signatureExactQueueSize()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {

        final TransactionSignature[] signatures =
                new TransactionSignature[engineSettings.getCpuVerifierQueueSize()];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = signaturePool.next();
        }

        cryptoProvider.verifySync(Arrays.asList(signatures));

        checkSignatures(signatures);
    }

    /** */
    @Test
    public void signatureDoubleQueueSize()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {
        final TransactionSignature[] signatures =
                new TransactionSignature[(engineSettings.getCpuVerifierQueueSize() * 2)];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = signaturePool.next();
        }

        cryptoProvider.verifySync(Arrays.asList(signatures));

        checkSignatures(signatures);
    }

    /** */
    @Test
    public void signatureThreadedHalfQueueSize() throws ExecutionException, InterruptedException {
        final int totalSignatures = engineSettings.getCpuVerifierQueueSize() / 2;
        final TransactionSignature[] signatures = new TransactionSignature[totalSignatures];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = signaturePool.next();
        }

        verifyParallel(signatures, PARALLELISM);
        checkSignatures(signatures);
    }

    /** */
    @Test
    public void signatureThreadedFourTimesQueueSize()
            throws ExecutionException, InterruptedException {
        final int totalSignatures = engineSettings.getCpuVerifierQueueSize() * 4;
        final TransactionSignature[] signatures = new TransactionSignature[totalSignatures];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = signaturePool.next();
        }

        verifyParallel(signatures, PARALLELISM);
        checkSignatures(signatures);
    }

    /** */
    @Test
    @ValueSource(ints = {0, 1})
    public void signatureThreadedEightTimesQueueSize()
            throws ExecutionException, InterruptedException {
        final int totalSignatures = engineSettings.getCpuVerifierQueueSize() * 8;
        final TransactionSignature[] signatures = new TransactionSignature[totalSignatures];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = signaturePool.next();
        }

        verifyParallel(signatures, PARALLELISM);
        checkSignatures(signatures);
    }

    /** Checks correctness of DigitalSignature batch sizes less than the thread count */
    @Test
    public void asyncSignatureSizeUnderThreads()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {
        final TransactionSignature[] signatures =
                new TransactionSignature[engineSettings.computeCpuVerifierThreadCount() - 1];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = signaturePool.next();
        }

        cryptoProvider.verifyAsync(Arrays.asList(signatures));
        // Thread.sleep(250);

        checkSignatures(signatures);
    }

    /** Checks correctness of DigitalSignature batch sizes more than the thread count */
    @Test
    public void asyncSignatureSizeOverThreads()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {

        final TransactionSignature[] signatures =
                new TransactionSignature[(engineSettings.computeCpuVerifierThreadCount() * 2) - 1];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = signaturePool.next();
        }

        cryptoProvider.verifyAsync(Arrays.asList(signatures));
        // Thread.sleep(250);

        checkSignatures(signatures);
    }

    /** */
    @Test
    public void asyncSignatureHalfQueueSize()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {
        final TransactionSignature[] signatures =
                new TransactionSignature[engineSettings.getCpuVerifierQueueSize() / 2];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = signaturePool.next();
        }

        cryptoProvider.verifyAsync(Arrays.asList(signatures));
        // Thread.sleep(250);

        checkSignatures(signatures);
    }

    /** */
    @Test
    public void asyncSignatureExactQueueSize()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {
        final TransactionSignature[] signatures =
                new TransactionSignature[engineSettings.getCpuVerifierQueueSize()];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = signaturePool.next();
        }

        cryptoProvider.verifyAsync(Arrays.asList(signatures));
        // Thread.sleep(250);

        checkSignatures(signatures);
    }

    /** */
    @Test
    public void asyncSignatureDoubleQueueSize()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {

        final TransactionSignature[] signatures =
                new TransactionSignature[(engineSettings.getCpuVerifierQueueSize() * 2)];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = signaturePool.next();
        }

        cryptoProvider.verifyAsync(Arrays.asList(signatures));
        // Thread.sleep(250);

        checkSignatures(signatures);
    }

    private void checkSignatures(TransactionSignature... signatures)
            throws ExecutionException, InterruptedException {
        int numInvalid = 0;

        for (final TransactionSignature sig : signatures) {
            Future<Void> future = sig.waitForFuture();
            assertNotNull(future);
            future.get();

            if (!VerificationStatus.VALID.equals(sig.getSignatureStatus())) {
                numInvalid++;
            }
        }

        assertEquals(0, numInvalid);
    }

    private void verifyParallel(final TransactionSignature[] signatures, final int parallelism) {
        final FuturePool<Void> futures =
                parallelExecute(
                        signatures,
                        parallelism,
                        (sItems, offset, count) -> {
                            for (int i = offset; i < count; i++) {
                                final TransactionSignature signature = sItems[i];

                                final boolean isValid = cryptoProvider.verifySync(signature);

                                signature.setSignatureStatus(
                                        ((isValid)
                                                ? VerificationStatus.VALID
                                                : VerificationStatus.INVALID));
                            }
                        });

        futures.waitForCompletion();
    }

    private FuturePool<Void> parallelExecute(
            final TransactionSignature[] signatures,
            final int parallelism,
            final SliceConsumer<TransactionSignature, Integer, Integer> executor) {
        final FuturePool<Void> futures = new FuturePool<>();

        if (signatures == null || signatures.length == 0) {
            return futures;
        }

        final int sliceSize = (int) Math.ceil((double) signatures.length / (double) parallelism);

        int offset = 0;
        int sliceLength = sliceSize;

        while ((offset + sliceLength) <= signatures.length) {
            //			futures.add(executor, signatures, offset, offset + sliceLength);
            final int fOffset = offset;
            final int fSliceLength = sliceLength;

            Future<Void> future =
                    executorService.submit(
                            new Callable<Void>() {
                                @Override
                                public Void call() throws Exception {
                                    executor.accept(signatures, fOffset, fOffset + fSliceLength);
                                    return null;
                                }
                            });

            futures.add(future);

            for (int i = offset; i < (fOffset + fSliceLength); i++) {
                signatures[i].setFuture(future);
            }

            offset += sliceLength;

            if (offset >= signatures.length) {
                break;
            } else if (signatures.length < offset + sliceLength) {
                sliceLength = signatures.length - offset;
            }
        }

        return futures;
    }
}
