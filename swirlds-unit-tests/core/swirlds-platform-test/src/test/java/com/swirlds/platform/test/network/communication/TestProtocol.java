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
package com.swirlds.platform.test.network.communication;

import com.swirlds.platform.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.network.protocol.ProtocolRunnable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;

public class TestProtocol implements Protocol {
    private final AtomicInteger timesRan;
    private final AtomicInteger initiateTrue;
    private final AtomicInteger acceptTrue;
    private final AtomicInteger failedCalled;
    private ProtocolRunnable runProtocol = (c) -> {};
    private boolean shouldInitiate;
    private boolean shouldAccept;
    private boolean acceptOnSimultaneousInitiate;

    public TestProtocol() {
        this(false, false, false);
    }

    public TestProtocol(
            final boolean shouldInitiate,
            final boolean shouldAccept,
            final boolean acceptOnSimultaneousInitiate) {
        this.shouldInitiate = shouldInitiate;
        this.shouldAccept = shouldAccept;
        this.acceptOnSimultaneousInitiate = acceptOnSimultaneousInitiate;
        this.timesRan = new AtomicInteger(0);
        this.initiateTrue = new AtomicInteger(0);
        this.acceptTrue = new AtomicInteger(0);
        this.failedCalled = new AtomicInteger(0);
    }

    public TestProtocol copy() {
        return new TestProtocol(
                this.shouldInitiate(), this.shouldAccept(), this.acceptOnSimultaneousInitiate());
    }

    public void reset() {
        timesRan.set(0);
        initiateTrue.set(0);
        acceptTrue.set(0);
        failedCalled.set(0);
        shouldInitiate = false;
        shouldAccept = false;
        acceptOnSimultaneousInitiate = false;
    }

    public TestProtocol setShouldInitiate(final boolean shouldInitiate) {
        this.shouldInitiate = shouldInitiate;
        return this;
    }

    public TestProtocol setShouldAccept(final boolean shouldAccept) {
        this.shouldAccept = shouldAccept;
        return this;
    }

    public TestProtocol setAcceptOnSimultaneousInitiate(
            final boolean acceptOnSimultaneousInitiate) {
        this.acceptOnSimultaneousInitiate = acceptOnSimultaneousInitiate;
        return this;
    }

    public TestProtocol setRunProtocol(final ProtocolRunnable runProtocol) {
        this.runProtocol = runProtocol;
        return this;
    }

    public int getTimesRan() {
        return timesRan.get();
    }

    @Override
    public boolean shouldInitiate() {
        if (shouldInitiate) {
            initiateTrue.incrementAndGet();
        }
        return shouldInitiate;
    }

    @Override
    public void initiateFailed() {
        failedCalled.incrementAndGet();
    }

    @Override
    public boolean shouldAccept() {
        if (shouldAccept) {
            acceptTrue.incrementAndGet();
        }
        return shouldAccept;
    }

    @Override
    public boolean acceptOnSimultaneousInitiate() {
        return acceptOnSimultaneousInitiate;
    }

    @Override
    public void runProtocol(final Connection connection)
            throws NetworkProtocolException, IOException, InterruptedException {
        timesRan.incrementAndGet();
        runProtocol.runProtocol(connection);
    }

    /**
     * asserts that the method contracts specified in {@link #shouldInitiate()} and {@link
     * #shouldAccept()} are upheld
     */
    public void assertInitiateContract() {
        Assertions.assertEquals(
                initiateTrue.get() + acceptTrue.get(),
                failedCalled.get() + timesRan.get(),
                "for each call to shouldInitiate() or shouldAccept() that returns true,"
                        + " initiateFailed() or runProtocol() should be called exactly once");
    }
}
