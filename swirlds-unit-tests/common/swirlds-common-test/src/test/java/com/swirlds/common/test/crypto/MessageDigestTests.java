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
import com.swirlds.common.crypto.Message;
import com.swirlds.common.crypto.internal.CryptographySettings;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MessageDigestTests {

    private static final CryptographySettings engineSettings =
            CryptographySettings.getDefaultSettings();
    private static MessageDigestPool digestPool;
    private static Cryptography cryptoProvider;

    @BeforeAll
    public static void startup() throws NoSuchAlgorithmException {
        assertNotNull(engineSettings);
        assertTrue(engineSettings.computeCpuDigestThreadCount() >= 1);

        cryptoProvider = CryptoFactory.getInstance();
        digestPool = new MessageDigestPool(engineSettings.computeCpuDigestThreadCount() * 16, 100);
    }

    @AfterAll
    public static void shutdown() {}

    /** Checks correctness of MessageDigest batch size of exactly one message */
    @Test
    public void digestSizeOfOne() throws ExecutionException, InterruptedException {
        final Message singleMessage = digestPool.next();

        assertNotNull(singleMessage);
        assertTrue(
                singleMessage.getHash() == null || singleMessage.getHash().getValue().length == 0);

        cryptoProvider.digestSync(singleMessage);

        assertNotNull(singleMessage.getFuture());
        singleMessage.getFuture().get();

        assertTrue(digestPool.isValid(singleMessage));
    }

    /** Checks correctness of MessageDigest batch sizes less than the thread count */
    @Test
    public void digestSizeUnderThreads() throws ExecutionException, InterruptedException {
        final Message[] messages = new Message[engineSettings.computeCpuDigestThreadCount() - 1];

        for (int i = 0; i < messages.length; i++) {
            messages[i] = digestPool.next();
        }

        cryptoProvider.digestSync(Arrays.asList(messages));

        checkMessages(messages);
    }

    /** Checks correctness of MessageDigest batch sizes more than the thread count */
    @Test
    public void digestSizeOverThreads()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {
        final Message[] messages =
                new Message[(engineSettings.computeCpuDigestThreadCount() * 2) - 1];

        for (int i = 0; i < messages.length; i++) {
            messages[i] = digestPool.next();
        }

        cryptoProvider.digestSync(Arrays.asList(messages));

        checkMessages(messages);
    }

    /** */
    @Test
    public void digestHalfQueueSize()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {
        final Message[] messages = new Message[engineSettings.getCpuDigestQueueSize() / 2];

        for (int i = 0; i < messages.length; i++) {
            messages[i] = digestPool.next();
        }

        cryptoProvider.digestSync(Arrays.asList(messages));

        checkMessages(messages);
    }

    /** */
    @Test
    public void digestExactQueueSize()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {
        final Message[] messages = new Message[engineSettings.getCpuDigestQueueSize()];

        for (int i = 0; i < messages.length; i++) {
            messages[i] = digestPool.next();
        }

        cryptoProvider.digestSync(Arrays.asList(messages));

        checkMessages(messages);
    }

    /** */
    @Test
    public void digestDoubleQueueSize()
            throws ExecutionException, InterruptedException, NoSuchProviderException,
                    NoSuchAlgorithmException {
        final Message[] messages = new Message[engineSettings.getCpuDigestQueueSize() * 2];

        for (int i = 0; i < messages.length; i++) {
            messages[i] = digestPool.next();
        }

        cryptoProvider.digestSync(Arrays.asList(messages));

        checkMessages(messages);
    }

    private void checkMessages(Message... messages)
            throws ExecutionException, InterruptedException {
        int numInvalid = 0;

        for (final Message m : messages) {
            assertNotNull(m.getFuture());
            m.getFuture().get();

            if (!digestPool.isValid(m)) {
                numInvalid++;
            }
        }

        assertEquals(0, numInvalid);
    }
}
