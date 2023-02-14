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
package com.swirlds.common.stream;

import static com.swirlds.logging.LogMarker.OBJECT_STREAM;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.stream.internal.AbstractLinkedObjectStream;
import com.swirlds.common.stream.internal.LinkedObjectStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Accepts a SerializableRunningHashable object each time, calculates and sets its runningHash when
 * nextStream is not null, pass this object to the next stream
 *
 * @param <T> type of the objects
 */
public class RunningHashCalculatorForStream<T extends RunningHashable & SerializableHashable>
        extends AbstractLinkedObjectStream<T> {
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger LOGGER = LogManager.getLogger(RunningHashCalculatorForStream.class);
    /** Used for hashing */
    private final Cryptography cryptography;
    /** current running Hash */
    private Hash runningHash;

    public RunningHashCalculatorForStream() {
        this.cryptography = CryptographyHolder.get();
    }

    public RunningHashCalculatorForStream(LinkedObjectStream<T> nextStream) {
        super(nextStream);
        this.cryptography = CryptographyHolder.get();
    }

    public RunningHashCalculatorForStream(Cryptography cryptography) {
        this.cryptography = cryptography;
    }

    public RunningHashCalculatorForStream(
            LinkedObjectStream<T> nextStream, Cryptography cryptography) {
        super(nextStream);
        this.cryptography = cryptography;
    }

    /** {@inheritDoc} */
    @Override
    public void addObject(T t) {
        // if Hash of this object is not set yet, calculates and sets its Hash
        if (t.getHash() == null) {
            cryptography.digestSync(t);
        }

        final Hash newHashToAdd = t.getHash();
        // calculates and updates runningHash
        runningHash = cryptography.calcRunningHash(runningHash, newHashToAdd, DigestType.SHA_384);
        t.getRunningHash().setHash(runningHash);
        super.addObject(t);
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        super.close();
        LOGGER.info(OBJECT_STREAM.getMarker(), "RunningHashCalculatorForStream is closed");
    }

    public Hash getRunningHash() {
        return runningHash;
    }

    /** {@inheritDoc} */
    @Override
    public void setRunningHash(final Hash hash) {
        this.runningHash = hash;
        super.setRunningHash(hash);
        LOGGER.info(
                OBJECT_STREAM.getMarker(),
                "RunningHashCalculatorForStream :: setRunningHash: {}",
                hash);
    }
}
