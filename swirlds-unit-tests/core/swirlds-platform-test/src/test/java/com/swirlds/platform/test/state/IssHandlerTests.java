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
package com.swirlds.platform.test.state;

import static com.swirlds.common.test.RandomUtils.randomHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.triggers.control.HaltRequestedTrigger;
import com.swirlds.platform.dispatch.triggers.control.ShutdownRequestedTrigger;
import com.swirlds.platform.dispatch.triggers.control.StateDumpRequestedTrigger;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.state.iss.IssHandler;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IssHandler Tests")
class IssHandlerTests {

    @Test
    @DisplayName("Hash Disagreement From Self")
    void hashDisagreementFromSelf() {
        final DispatchBuilder dispatchBuilder = new DispatchBuilder();
        final StateSettings stateSettings = new StateSettings();
        stateSettings.haltOnAnyIss = true;
        stateSettings.dumpStateOnAnyISS = true;
        final long selfId = 0;

        final AtomicInteger dumpCount = new AtomicInteger();
        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        dispatchBuilder.registerObserver(
                StateDumpRequestedTrigger.class,
                (final String reason, final Boolean blocking) -> dumpCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                HaltRequestedTrigger.class, (final String reason) -> freezeCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                ShutdownRequestedTrigger.class,
                (final String reason, final Integer exitCode) -> shutdownCount.getAndIncrement());

        final IssHandler handler = new IssHandler(dispatchBuilder, stateSettings, selfId);

        dispatchBuilder.start();

        handler.stateHashValidityObserver(1234L, selfId, randomHash(), randomHash());

        assertEquals(0, dumpCount.get(), "unexpected dump count");
        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");
    }

    @Test
    @DisplayName("Hash Disagreement Always Freeze")
    void hashDisagreementAlwaysFreeze() {
        final DispatchBuilder dispatchBuilder = new DispatchBuilder();
        final StateSettings stateSettings = new StateSettings();
        stateSettings.haltOnAnyIss = true;
        stateSettings.dumpStateOnAnyISS = false;
        final long selfId = 0;

        final AtomicInteger dumpCount = new AtomicInteger();
        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        dispatchBuilder.registerObserver(
                StateDumpRequestedTrigger.class,
                (final String reason, final Boolean blocking) -> {
                    assertEquals(
                            "iss", reason, "state dump reason is important, effects file path");
                    assertFalse(blocking, "no need to block if we are going to freeze");
                    dumpCount.getAndIncrement();
                });

        dispatchBuilder.registerObserver(
                HaltRequestedTrigger.class, (final String reason) -> freezeCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                ShutdownRequestedTrigger.class,
                (final String reason, final Integer exitCode) -> shutdownCount.getAndIncrement());

        final IssHandler handler = new IssHandler(dispatchBuilder, stateSettings, selfId);

        dispatchBuilder.start();

        handler.stateHashValidityObserver(1234L, selfId + 1, randomHash(), randomHash());

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Once frozen, this should become a no-op
        handler.stateHashValidityObserver(1234L, selfId + 1, randomHash(), randomHash());

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");
    }

    @Test
    @DisplayName("Hash Disagreement Always Freeze")
    void hashDisagreementAlwaysDump() {
        final DispatchBuilder dispatchBuilder = new DispatchBuilder();
        final StateSettings stateSettings = new StateSettings();
        stateSettings.haltOnAnyIss = false;
        stateSettings.dumpStateOnAnyISS = true;
        final long selfId = 0;

        final AtomicInteger dumpCount = new AtomicInteger();
        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        dispatchBuilder.registerObserver(
                StateDumpRequestedTrigger.class,
                (final String reason, final Boolean blocking) -> {
                    assertEquals(
                            "iss", reason, "state dump reason is important, effects file path");
                    assertFalse(blocking, "no need to block");
                    dumpCount.getAndIncrement();
                });

        dispatchBuilder.registerObserver(
                HaltRequestedTrigger.class, (final String reason) -> freezeCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                ShutdownRequestedTrigger.class,
                (final String reason, final Integer exitCode) -> shutdownCount.getAndIncrement());

        final IssHandler handler = new IssHandler(dispatchBuilder, stateSettings, selfId);

        dispatchBuilder.start();

        handler.stateHashValidityObserver(1234L, selfId + 1, randomHash(), randomHash());

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Throttle should prevent double dumping
        handler.stateHashValidityObserver(1234L, selfId + 1, randomHash(), randomHash());

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");
    }

    @Test
    @DisplayName("Hash Disagreement No Action")
    void hashDisagreementNoAction() {
        final DispatchBuilder dispatchBuilder = new DispatchBuilder();
        final StateSettings stateSettings = new StateSettings();
        stateSettings.haltOnAnyIss = false;
        stateSettings.dumpStateOnAnyISS = false;
        final long selfId = 0;

        final AtomicInteger dumpCount = new AtomicInteger();
        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        dispatchBuilder.registerObserver(
                StateDumpRequestedTrigger.class,
                (final String reason, final Boolean blocking) -> dumpCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                HaltRequestedTrigger.class, (final String reason) -> freezeCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                ShutdownRequestedTrigger.class,
                (final String reason, final Integer exitCode) -> shutdownCount.getAndIncrement());

        final IssHandler handler = new IssHandler(dispatchBuilder, stateSettings, selfId);

        dispatchBuilder.start();

        handler.stateHashValidityObserver(1234L, selfId + 1, randomHash(), randomHash());

        assertEquals(0, dumpCount.get(), "unexpected dump count");
        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");
    }

    @Test
    @DisplayName("Hash Disagreement Always Freeze")
    void hashDisagreementFreezeAndDump() {
        final DispatchBuilder dispatchBuilder = new DispatchBuilder();
        final StateSettings stateSettings = new StateSettings();
        stateSettings.haltOnAnyIss = true;
        stateSettings.dumpStateOnAnyISS = true;
        final long selfId = 0;

        final AtomicInteger dumpCount = new AtomicInteger();
        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        dispatchBuilder.registerObserver(
                StateDumpRequestedTrigger.class,
                (final String reason, final Boolean blocking) -> {
                    assertEquals(
                            "iss", reason, "state dump reason is important, effects file path");
                    assertFalse(blocking, "no need to block if we are going to freeze");
                    dumpCount.getAndIncrement();
                });

        dispatchBuilder.registerObserver(
                HaltRequestedTrigger.class, (final String reason) -> freezeCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                ShutdownRequestedTrigger.class,
                (final String reason, final Integer exitCode) -> shutdownCount.getAndIncrement());

        final IssHandler handler = new IssHandler(dispatchBuilder, stateSettings, selfId);

        dispatchBuilder.start();

        handler.stateHashValidityObserver(1234L, selfId + 1, randomHash(), randomHash());

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Once frozen, this should become a no-op
        handler.stateHashValidityObserver(1234L, selfId + 1, randomHash(), randomHash());

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");
    }

    @Test
    @DisplayName("Self ISS Automated Recovery")
    void selfIssAutomatedRecovery() {
        final DispatchBuilder dispatchBuilder = new DispatchBuilder();
        final StateSettings stateSettings = new StateSettings();
        stateSettings.haltOnAnyIss = false;
        stateSettings.dumpStateOnAnyISS = false;
        stateSettings.automatedSelfIssRecovery = true;
        final long selfId = 0;

        final AtomicInteger dumpCount = new AtomicInteger();
        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        dispatchBuilder.registerObserver(
                StateDumpRequestedTrigger.class,
                (final String reason, final Boolean blocking) -> {
                    assertEquals(
                            "iss", reason, "state dump reason is important, effects file path");
                    assertTrue(blocking, "should block before shutdown");
                    dumpCount.getAndIncrement();
                });

        dispatchBuilder.registerObserver(
                HaltRequestedTrigger.class, (final String reason) -> freezeCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                ShutdownRequestedTrigger.class,
                (final String reason, final Integer exitCode) -> shutdownCount.getAndIncrement());

        final IssHandler handler = new IssHandler(dispatchBuilder, stateSettings, selfId);

        dispatchBuilder.start();

        handler.selfIssObserver(1234L, randomHash(), randomHash());

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(1, shutdownCount.get(), "unexpected shutdown count");
    }

    @Test
    @DisplayName("Self ISS No Action")
    void selfIssNoAction() {
        final DispatchBuilder dispatchBuilder = new DispatchBuilder();
        final StateSettings stateSettings = new StateSettings();
        stateSettings.haltOnAnyIss = false;
        stateSettings.dumpStateOnAnyISS = false;
        stateSettings.automatedSelfIssRecovery = false;
        final long selfId = 0;

        final AtomicInteger dumpCount = new AtomicInteger();
        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        dispatchBuilder.registerObserver(
                StateDumpRequestedTrigger.class,
                (final String reason, final Boolean blocking) -> dumpCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                HaltRequestedTrigger.class, (final String reason) -> freezeCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                ShutdownRequestedTrigger.class,
                (final String reason, final Integer exitCode) -> shutdownCount.getAndIncrement());

        final IssHandler handler = new IssHandler(dispatchBuilder, stateSettings, selfId);

        dispatchBuilder.start();

        handler.selfIssObserver(1234L, randomHash(), randomHash());

        assertEquals(0, dumpCount.get(), "unexpected dump count");
        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");
    }

    @Test
    @DisplayName("Self ISS Always Freeze")
    void selfIssAlwaysFreeze() {
        final DispatchBuilder dispatchBuilder = new DispatchBuilder();
        final StateSettings stateSettings = new StateSettings();
        stateSettings.haltOnAnyIss = true;
        stateSettings.dumpStateOnAnyISS = false;
        stateSettings.automatedSelfIssRecovery = false;
        final long selfId = 0;

        final AtomicInteger dumpCount = new AtomicInteger();
        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        dispatchBuilder.registerObserver(
                StateDumpRequestedTrigger.class,
                (final String reason, final Boolean blocking) -> {
                    assertEquals(
                            "iss", reason, "state dump reason is important, effects file path");
                    assertFalse(blocking, "no need to block if we are going to freeze");
                    dumpCount.getAndIncrement();
                });

        dispatchBuilder.registerObserver(
                HaltRequestedTrigger.class, (final String reason) -> freezeCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                ShutdownRequestedTrigger.class,
                (final String reason, final Integer exitCode) -> shutdownCount.getAndIncrement());

        final IssHandler handler = new IssHandler(dispatchBuilder, stateSettings, selfId);

        dispatchBuilder.start();

        handler.selfIssObserver(1234L, randomHash(), randomHash());

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Once frozen, this should become a no-op
        handler.selfIssObserver(1234L, randomHash(), randomHash());

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");
    }

    @Test
    @DisplayName("Self ISS Always Dump")
    void selfIssAlwaysDump() {
        final DispatchBuilder dispatchBuilder = new DispatchBuilder();
        final StateSettings stateSettings = new StateSettings();
        stateSettings.haltOnAnyIss = false;
        stateSettings.dumpStateOnAnyISS = true;
        stateSettings.automatedSelfIssRecovery = false;
        final long selfId = 0;

        final AtomicInteger dumpCount = new AtomicInteger();
        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        dispatchBuilder.registerObserver(
                StateDumpRequestedTrigger.class,
                (final String reason, final Boolean blocking) -> {
                    assertEquals(
                            "iss", reason, "state dump reason is important, effects file path");
                    assertFalse(blocking, "no need to block");
                    dumpCount.getAndIncrement();
                });

        dispatchBuilder.registerObserver(
                HaltRequestedTrigger.class, (final String reason) -> freezeCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                ShutdownRequestedTrigger.class,
                (final String reason, final Integer exitCode) -> shutdownCount.getAndIncrement());

        final IssHandler handler = new IssHandler(dispatchBuilder, stateSettings, selfId);

        dispatchBuilder.start();

        handler.selfIssObserver(1234L, randomHash(), randomHash());

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Rate limiter should prevent double dump
        handler.selfIssObserver(1234L, randomHash(), randomHash());

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");
    }

    @Test
    @DisplayName("Catastrophic ISS No Action")
    void catastrophicIssNoAction() {
        final DispatchBuilder dispatchBuilder = new DispatchBuilder();
        final StateSettings stateSettings = new StateSettings();
        stateSettings.haltOnAnyIss = false;
        stateSettings.dumpStateOnAnyISS = false;
        stateSettings.haltOnCatastrophicIss = false;
        final long selfId = 0;

        final AtomicInteger dumpCount = new AtomicInteger();
        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        dispatchBuilder.registerObserver(
                StateDumpRequestedTrigger.class,
                (final String reason, final Boolean blocking) -> dumpCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                HaltRequestedTrigger.class, (final String reason) -> freezeCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                ShutdownRequestedTrigger.class,
                (final String reason, final Integer exitCode) -> shutdownCount.getAndIncrement());

        final IssHandler handler = new IssHandler(dispatchBuilder, stateSettings, selfId);

        dispatchBuilder.start();

        handler.catastrophicIssObserver(1234L, null);

        assertEquals(0, dumpCount.get(), "unexpected dump count");
        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");
    }

    @Test
    @DisplayName("Catastrophic ISS Always Freeze")
    void catastrophicIssAlwaysFreeze() {
        final DispatchBuilder dispatchBuilder = new DispatchBuilder();
        final StateSettings stateSettings = new StateSettings();
        stateSettings.haltOnAnyIss = true;
        stateSettings.dumpStateOnAnyISS = false;
        stateSettings.haltOnCatastrophicIss = false;
        final long selfId = 0;

        final AtomicInteger dumpCount = new AtomicInteger();
        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        dispatchBuilder.registerObserver(
                StateDumpRequestedTrigger.class,
                (final String reason, final Boolean blocking) -> {
                    assertEquals(
                            "iss", reason, "state dump reason is important, effects file path");
                    assertFalse(blocking, "no need to block if we are going to freeze");
                    dumpCount.getAndIncrement();
                });

        dispatchBuilder.registerObserver(
                HaltRequestedTrigger.class, (final String reason) -> freezeCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                ShutdownRequestedTrigger.class,
                (final String reason, final Integer exitCode) -> shutdownCount.getAndIncrement());

        final IssHandler handler = new IssHandler(dispatchBuilder, stateSettings, selfId);

        dispatchBuilder.start();

        handler.catastrophicIssObserver(1234L, null);

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Once frozen, this should become a no-op
        handler.catastrophicIssObserver(1234L, null);

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");
    }

    @Test
    @DisplayName("Catastrophic ISS Freeze On Catastrophic")
    void catastrophicIssFreezeOnCatastrophic() {
        final DispatchBuilder dispatchBuilder = new DispatchBuilder();
        final StateSettings stateSettings = new StateSettings();
        stateSettings.haltOnAnyIss = false;
        stateSettings.dumpStateOnAnyISS = false;
        stateSettings.haltOnCatastrophicIss = true;
        final long selfId = 0;

        final AtomicInteger dumpCount = new AtomicInteger();
        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        dispatchBuilder.registerObserver(
                StateDumpRequestedTrigger.class,
                (final String reason, final Boolean blocking) -> {
                    assertEquals(
                            "iss", reason, "state dump reason is important, effects file path");
                    assertFalse(blocking, "no need to block if we are going to freeze");
                    dumpCount.getAndIncrement();
                });

        dispatchBuilder.registerObserver(
                HaltRequestedTrigger.class, (final String reason) -> freezeCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                ShutdownRequestedTrigger.class,
                (final String reason, final Integer exitCode) -> shutdownCount.getAndIncrement());

        final IssHandler handler = new IssHandler(dispatchBuilder, stateSettings, selfId);

        dispatchBuilder.start();

        handler.catastrophicIssObserver(1234L, null);

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Once frozen, this should become a no-op
        handler.catastrophicIssObserver(1234L, null);

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");
    }

    @Test
    @DisplayName("Catastrophic ISS Always Dump")
    void catastrophicIssAlwaysDump() {
        final DispatchBuilder dispatchBuilder = new DispatchBuilder();
        final StateSettings stateSettings = new StateSettings();
        stateSettings.haltOnAnyIss = false;
        stateSettings.dumpStateOnAnyISS = true;
        stateSettings.haltOnCatastrophicIss = false;
        final long selfId = 0;

        final AtomicInteger dumpCount = new AtomicInteger();
        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        dispatchBuilder.registerObserver(
                StateDumpRequestedTrigger.class,
                (final String reason, final Boolean blocking) -> {
                    assertEquals(
                            "iss", reason, "state dump reason is important, effects file path");
                    assertFalse(blocking, "no need to block");
                    dumpCount.getAndIncrement();
                });

        dispatchBuilder.registerObserver(
                HaltRequestedTrigger.class, (final String reason) -> freezeCount.getAndIncrement());

        dispatchBuilder.registerObserver(
                ShutdownRequestedTrigger.class,
                (final String reason, final Integer exitCode) -> shutdownCount.getAndIncrement());

        final IssHandler handler = new IssHandler(dispatchBuilder, stateSettings, selfId);

        dispatchBuilder.start();

        handler.catastrophicIssObserver(1234L, null);

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Throttle should prevent double dump
        handler.catastrophicIssObserver(1234L, null);

        assertEquals(1, dumpCount.get(), "unexpected dump count");
        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");
    }
}
