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
package com.swirlds.logging.test;

import static com.swirlds.logging.LogMarker.DEMO_INFO;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.PLATFORM_STATUS;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.logging.payloads.NodeStartPayload;
import com.swirlds.logging.payloads.PlatformStatusPayload;
import com.swirlds.logging.payloads.SoftwareVersionPayload;

/** Contains utility methods that add scripted events to a dummy log builder. */
public final class DummyLogScripts {

    private DummyLogScripts() {}

    /** Simulate a node first coming online, but not the node reaching an active state. */
    public static void simulatePlatformInitialization(DummyLogBuilder log) {
        log.debug(STARTUP.getMarker(), new NodeStartPayload().toString());
        log.debug(STARTUP.getMarker(), "Browser: About to run startPlatforms()");
        log.debug(STARTUP.getMarker(), "<main> Browser: About do crypto instantiation");
        log.debug(STARTUP.getMarker(), "<main> Browser: About start loading keys");
        log.debug(STARTUP.getMarker(), "<main> Browser: Done loading keys");
        log.debug(STARTUP.getMarker(), "<main> Browser: About to start creating Crypto objects");
        log.debug(STARTUP.getMarker(), "<main> Browser: Done creating Crypto objects");
        log.debug(STARTUP.getMarker(), "<main> Browser: Done with crypto instantiation");
        log.debug(
                STARTUP.getMarker(),
                "<main> Browser: Scanning the classpath for " + "RuntimeConstructable classes");
        log.debug(
                STARTUP.getMarker(),
                "<main> Browser: Done with registerConstructables, time taken 1250ms");
        log.info(STARTUP.getMarker(), "<main> Hashgraph: threadPollIntakeQueue is started");
        simulateInit(log, "GENESIS", null);
    }

    /** Simulate a node starting up and reaching an active state. */
    public static void simulatePlatformStart(DummyLogBuilder log) {
        simulatePlatformInitialization(log);

        log.info(
                DEMO_INFO.getMarker(),
                "<main> ExpectedFCMFamily: " + "Set ExpectedMap initial capacity to be: 3000");
        log.info(
                DEMO_INFO.getMarker(),
                "<main> ExpectedFCMFamily: " + "Set accountList initial capacity to be: 1000");
        log.info(
                DEMO_INFO.getMarker(),
                "<main> ExpectedFCMFamily: "
                        + "Set accountSelfEntitiesList initial capacity to be: 250");
        log.info(
                DEMO_INFO.getMarker(),
                "<main> ExpectedFCMFamily: " + "Set blobList initial capacity to be: 1000");
        log.info(
                DEMO_INFO.getMarker(),
                "<main> ExpectedFCMFamily: "
                        + "Set blobSelfEntitiesList initial capacity to be: 250");
        log.info(
                DEMO_INFO.getMarker(),
                "<main> ExpectedFCMFamily: " + "Set fcqList initial capacity to be: 1000");
        log.info(
                DEMO_INFO.getMarker(),
                "<main> ExpectedFCMFamily: "
                        + "Set fcqSelfEntitiesList initial capacity to be: 250");

        log.info(
                STARTUP.getMarker(),
                "<main> EventFlow: EventFlow.startAll(), forCons.size: 0, forCurr.size: 0,"
                        + " forNext.size: null, forSigs.size: 0, forWork.size: null");
        log.info(
                STARTUP.getMarker(),
                "<main> PlatformTestingToolMain: JVM arg: " + "-XX:+UnlockExperimentalVMOptions");
        log.info(STARTUP.getMarker(), "<main> PlatformTestingToolMain: JVM arg: -XX:+UseZGC");
        log.info(
                STARTUP.getMarker(),
                "<main> PlatformTestingToolMain: JVM arg: -XX:ConcGCThreads=14");
        log.info(
                STARTUP.getMarker(), "<main> PlatformTestingToolMain: JVM arg: -XX:+UseLargePages");
        log.info(STARTUP.getMarker(), "<main> PlatformTestingToolMain: JVM arg: -Xmx98g");
        log.info(STARTUP.getMarker(), "<main> PlatformTestingToolMain: JVM arg: -Xms10g");
        log.info(
                STARTUP.getMarker(),
                "<main> PlatformTestingToolMain: JVM arg: -XX:ZMarkStackSpaceLimit=16g");
        log.info(
                STARTUP.getMarker(),
                "<main> PlatformTestingToolMain: JVM arg: -XX:MaxDirectMemorySize=32g");
        log.info(
                STARTUP.getMarker(),
                "<main> PlatformTestingToolMain: JVM arg: -XX:MetaspaceSize=100M");
        log.info(STARTUP.getMarker(), "<main> PlatformTestingToolMain: JVM arg: -Xlog:gc*:gc.log");
        log.info(
                STARTUP.getMarker(),
                "<main> PlatformTestingToolMain: JVM arg: "
                        + "-Dlog4j.configurationFile=log4j2-regression.xml");
        log.info(
                STARTUP.getMarker(),
                "<main> PlatformTestingToolMain: Parsing "
                        + "JSON FCMSmallBlob-Recovery-10-5m.json");

        log.debug(
                STARTUP.getMarker(),
                "<main> TransactionPool: Public Key -> "
                    + "hex('0x80A1F27878B4A1989610EDC8ABC23668963F090241FAA930730659E5BD9CAFC7')");
        log.debug(STARTUP.getMarker(), "<main> Browser: main() finished");

        log.info(
                PLATFORM_STATUS.getMarker(),
                new PlatformStatusPayload("Platform status changed.", "", "STARTING_UP")
                        .toString());
        log.info(
                PLATFORM_STATUS.getMarker(),
                new PlatformStatusPayload("Platform status changed.", "STARTING_UP", "ACTIVE")
                        .toString());
    }

    /** Simulate an error. Useful for testing validators that should ignore general errors. */
    public static void simulateError(DummyLogBuilder log) {
        log.error(EXCEPTION.getMarker(), "there was an error", new RuntimeException("oh-no!"));
    }

    /**
     * Simulate calling init() on an application, logging the initTrigger and previous Software
     * versions.
     */
    public static void simulateInit(
            DummyLogBuilder log, String initTrigger, String previousSoftwareVersion) {
        log.info(
                STARTUP.getMarker(),
                new SoftwareVersionPayload(
                                "Application initializing", initTrigger, previousSoftwareVersion)
                        .toString());
    }
}
