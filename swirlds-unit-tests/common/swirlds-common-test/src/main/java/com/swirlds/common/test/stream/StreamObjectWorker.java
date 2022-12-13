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
package com.swirlds.common.test.stream;

import static com.swirlds.common.test.stream.TestStreamType.TEST_STREAM;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.HashCalculatorForStream;
import com.swirlds.common.stream.QueueThreadObjectStream;
import com.swirlds.common.stream.QueueThreadObjectStreamConfiguration;
import com.swirlds.common.stream.RunningHashCalculatorForStream;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.stream.internal.TimestampStreamFileWriter;
import com.swirlds.common.system.NodeId;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * For testing object stream; takes objects from {@link ObjectForTestStreamGenerator}, sends objects
 * to LinkedObjectStream objects for calculating RunningHash and serializing to disk
 */
public class StreamObjectWorker {
    public static final NodeId NODE_ID = new NodeId(false, 0);
    /** receives objects from runningHashCalculator, then passes to writeConsumer */
    private final QueueThreadObjectStream<ObjectForTestStream> writeQueueThread;

    private Iterator<ObjectForTestStream> iterator;
    /** receives objects from a generator, then passes to hashCalculator */
    private QueueThreadObjectStream<ObjectForTestStream> hashQueueThread;
    /** number of objects needs to be processed */
    private int remainNum;
    /** objects that has been added, is used for unit test */
    private Deque<SelfSerializable> addedObjects;

    public StreamObjectWorker(
            int totalNum,
            int intervalMs,
            String dirPath,
            int logPeriodMs,
            Hash initialHash,
            boolean startWriteAtCompleteWindow,
            Instant firstTimestamp,
            Signer signer)
            throws NoSuchAlgorithmException {

        // writes objects to files
        TimestampStreamFileWriter<ObjectForTestStream> fileWriter =
                new TimestampStreamFileWriter<>(
                        dirPath, logPeriodMs, signer, startWriteAtCompleteWindow, TEST_STREAM);

        writeQueueThread =
                new QueueThreadObjectStreamConfiguration<ObjectForTestStream>()
                        .setForwardTo(fileWriter)
                        .build();
        writeQueueThread.start();

        initialize(totalNum, intervalMs, initialHash, firstTimestamp);
    }

    public StreamObjectWorker(
            int totalNum,
            int intervalMs,
            Hash initialHash,
            Instant firstTimestamp,
            SerializableDataOutputStream stream)
            throws IOException {
        // writes objects to a stream
        WriteToStreamConsumer streamWriter = new WriteToStreamConsumer(stream, initialHash);

        writeQueueThread =
                new QueueThreadObjectStreamConfiguration<ObjectForTestStream>()
                        .setForwardTo(streamWriter)
                        .build();
        writeQueueThread.start();

        initialize(totalNum, intervalMs, initialHash, firstTimestamp);
    }

    private void initialize(
            int totalNum, int intervalMs, Hash initialHash, Instant firstTimestamp) {
        this.remainNum = totalNum;
        this.iterator =
                new ObjectForTestStreamGenerator(totalNum, intervalMs, firstTimestamp)
                        .getIterator();
        Cryptography cryptography = CryptoFactory.getInstance();
        // receives objects from hashCalculator, calculates and set runningHash for this object
        RunningHashCalculatorForStream<ObjectForTestStream> runningHashCalculator =
                new RunningHashCalculatorForStream<>(writeQueueThread, cryptography);

        // receives objects from hashQueueThread, calculates it's Hash, then passes to
        // runningHashCalculator
        HashCalculatorForStream<ObjectForTestStream> hashCalculator =
                new HashCalculatorForStream<>(runningHashCalculator, cryptography);

        hashQueueThread =
                new QueueThreadObjectStreamConfiguration<ObjectForTestStream>()
                        .setForwardTo(hashCalculator)
                        .build();
        hashQueueThread.setRunningHash(initialHash);
        hashQueueThread.start();

        addedObjects = new LinkedList<>();
    }

    void work() {
        while (remainNum > 0 && iterator.hasNext()) {
            ObjectForTestStream object = iterator.next();

            // send this object to hashQueueThread
            hashQueueThread.getQueue().add(object);
            addedObjects.add(object);

            // if this is the last object,
            // should tell consumer to close current file after writing this object
            if (!iterator.hasNext()) {
                hashQueueThread.close();
            }

            remainNum--;
        }
    }

    Deque<SelfSerializable> getAddedObjects() {
        return addedObjects;
    }
}
