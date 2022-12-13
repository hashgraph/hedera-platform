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
package com.swirlds.platform.test.recovery;

import static com.swirlds.common.stream.EventStreamType.EVENT_EXTENSION;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getTimeStampFromFileName;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.readStartRunningHashFromStreamFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.stream.EventStreamType;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.recovery.EventStreamParser;
import com.swirlds.platform.test.SimpleEventGenerator;
import com.swirlds.test.framework.TestQualifierTags;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventStreamParserTests {

    private static final long MILLIS_BETWEEN_EVENTS = 10;
    private static final String NODE_NAME = "test-node";
    private static final NodeId selfId = new NodeId(false, 0);
    private static final Signer mockSigner = mock(Signer.class);
    @TempDir Path eventStreamDir;
    private EventStreamManager<EventImpl> eventStreamManager;

    @BeforeAll
    static void staticSetup() throws ConstructableRegistryException {
        ConstructableRegistry.registerConstructables("com.swirlds");
        SettingsCommon.maxTransactionCountPerEvent = 1024;
        SettingsCommon.transactionMaxBytes = 6144;
        SettingsCommon.maxTransactionBytesPerEvent = 4096;
    }

    private static Instant timestamp(final String path) {
        return getTimeStampFromFileName(path);
    }

    private static Hash freshHash() {
        return new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);
    }

    @BeforeEach
    void setup() {
        final Signature mockSig = mock(Signature.class);
        when(mockSig.getSignatureBytes()).thenReturn(new byte[8]);
        when(mockSigner.sign(any(byte[].class))).thenReturn(mockSig);
    }

    @AfterEach
    void cleanup() throws IOException {
        eventStreamManager.stop();
        try (final Stream<Path> walk = Files.walk(eventStreamDir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::deleteOnExit);
        }
    }

    /**
     * For a given set of event files, choose different time intervals to parse. Verify the correct
     * events are parsed and that the initial hash is correct.
     */
    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void testTimeInterval() throws NoSuchAlgorithmException, IOException {
        final Instant endTime = Instant.now();
        final Instant startTime = endTime.minus(15, ChronoUnit.SECONDS);
        final List<EventFile> eventFiles = new LinkedList<>(writeEventFiles(startTime, endTime));

        eventFiles.sort(Comparator.comparing(EventFile::getTimestamp));

        // all files should be parsed
        parseAndVerifyEvents(
                new TimeRange(eventFiles.get(0).getTimestamp(), endTime), eventFiles.get(0));

        // Adjust the start time such that the start is in each file
        for (int i = 0; i < eventFiles.size() - 1; i++) {
            final Instant parserStartTime = timeBetweenFiles(eventFiles, i, i + 1);
            parseAndVerifyEvents(new TimeRange(parserStartTime, endTime), eventFiles.get(i));
        }

        // Adjust the end time such that the end is in each file (except the last)
        for (int i = eventFiles.size() - 2; i > 0; i--) {
            final Instant parserEndTime = timeBetweenFiles(eventFiles, i, i + 1);
            parseAndVerifyEvents(new TimeRange(startTime, parserEndTime), eventFiles.get(0));
        }
    }

    private Instant timeBetweenFiles(
            final List<EventFile> eventFiles, final int index1, final int index2) {
        final Duration fileTimeSpan =
                Duration.between(
                        eventFiles.get(index1).getTimestamp(),
                        eventFiles.get(index2).getTimestamp());
        return eventFiles.get(index1).getTimestamp().plusMillis(fileTimeSpan.toMillis() / 2);
    }

    private List<EventImpl> parseEvents(final EventStreamParser parser) {
        parser.start();
        final List<EventImpl> parsedEvents = new ArrayList<>();
        while (parser.hasMoreEvents()) {
            final EventImpl parsedEvent = parser.getNextEvent(10, TimeUnit.MILLISECONDS);
            if (parsedEvent != null) {
                parsedEvents.add(parsedEvent);
            }
        }
        return parsedEvents;
    }

    /**
     * Parse the event stream and verify that events from the correct files are in the correct time
     * range.
     *
     * @param parserTimeRange the time range to provide the parser
     * @param expectedFirstParsedFile the first file that should get parsed
     */
    private void parseAndVerifyEvents(
            final TimeRange parserTimeRange, final EventFile expectedFirstParsedFile) {

        final AtomicReference<Hash> hashConsumer = new AtomicReference<>();

        final EventStreamParser parser =
                new EventStreamParser(
                        eventStreamDir,
                        parserTimeRange.start,
                        parserTimeRange.stop,
                        hashConsumer::set,
                        true);

        final List<EventImpl> parsedEvents = parseEvents(parser);

        assertNotNull(hashConsumer.get(), "Initial hash never set.");

        Instant prevConsTime = null;

        for (final EventImpl parsedEvent : parsedEvents) {

            // Verify that events are in consensus order
            final Instant consTime = parsedEvent.getConsensusTimestamp();
            if (prevConsTime != null) {
                assertTrue(prevConsTime.isBefore(consTime), "Event parsed out of order.");
            }
            prevConsTime = consTime;

            // Verify that events were not parsed from before the
            // first file (based on the start time) or after the stop time
            final boolean afterStart =
                    expectedFirstParsedFile.getTimestamp().compareTo(consTime) <= 0;
            final boolean beforeEnd = parserTimeRange.stop.compareTo(consTime) >= 0;

            assertTrue(
                    afterStart,
                    String.format(
                            "Parsed event consensus time is before first file timestamp. "
                                    + "\nConsTime: %s"
                                    + "\nStartTime: %s"
                                    + "\nParser start: %s"
                                    + "\nParser stop: %s",
                            consTime,
                            expectedFirstParsedFile.getTimestamp(),
                            parserTimeRange.start,
                            parserTimeRange.stop));
            assertTrue(
                    beforeEnd,
                    String.format(
                            "Parsed event has consensus time after last file timestamp. ConsTime:"
                                    + " %s, EndTime: %s",
                            consTime, parserTimeRange.stop));
        }

        assertEquals(
                expectedFirstParsedFile.getStartingHash(),
                hashConsumer.get(),
                "Incorrect initial hash sent");
    }

    /**
     * Generates events with consensus times between {@code startTime} and {@code endTime} and sends
     * them to the event stream.
     *
     * @param startTime the consensus time of the first generated event
     * @param endTime the consensus time of the last generated event
     * @return a list of data objects corresponding to each event file written to disk
     */
    private List<EventFile> writeEventFiles(final Instant startTime, final Instant endTime)
            throws NoSuchAlgorithmException, IOException {
        eventStreamManager =
                new EventStreamManager<>(
                        selfId,
                        mockSigner,
                        NODE_NAME,
                        true,
                        eventStreamDir.toString(),
                        5L,
                        0,
                        (e) -> false);

        eventStreamManager.setInitialHash(freshHash());

        final Random random = RandomUtils.getRandomPrintSeed();

        final SimpleEventGenerator generator = new SimpleEventGenerator(4, random);

        int consensusOrder = 0;
        Instant consensusTime = startTime;

        while (consensusTime.isBefore(endTime)) {
            final EventImpl e = generator.nextEvent(false);
            e.setConsensus(true);
            e.setConsensusOrder(consensusOrder++);
            e.setConsensusTimestamp(consensusTime);

            eventStreamManager.addEvent(e);

            consensusTime = consensusTime.plusMillis(MILLIS_BETWEEN_EVENTS);
        }

        try (final Stream<Path> stream = Files.list(eventStreamDir())) {
            return stream.filter(f -> !Files.isDirectory(f))
                    .filter(f -> f.getFileName().toString().endsWith(EVENT_EXTENSION))
                    .map(EventFile::new)
                    .toList();
        }
    }

    private Path eventStreamDir() {
        return eventStreamDir.resolve("events_" + NODE_NAME);
    }

    private record TimeRange(Instant start, Instant stop) {}

    private record EventFile(Path path) {
        public Instant getTimestamp() {
            return timestamp(path.getFileName().toString());
        }

        public Hash getStartingHash() {
            return readStartRunningHashFromStreamFile(path.toFile(), EventStreamType.getInstance());
        }
    }
}
