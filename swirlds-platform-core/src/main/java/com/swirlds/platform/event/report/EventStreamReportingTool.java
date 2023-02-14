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
package com.swirlds.platform.event.report;

import static com.swirlds.common.utility.TextTable.commaSeparatedValue;

import com.swirlds.common.io.IOIterator;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import com.swirlds.platform.recovery.internal.EventStreamMultiFileIterator;
import com.swirlds.platform.recovery.internal.MultiFileRunningHashIterator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A tool that reads an event stream and generates a report about it */
public final class EventStreamReportingTool {

    private EventStreamReportingTool() {}

    private static final int PROGRESS_INTERVAL = 10_000;

    /**
     * @param eventStreamDirectory the path to the directory where the event stream is located
     * @param startingRound the round received after which we should start reading events
     * @return a report with useful information about the event stream
     */
    public static EventStreamReport createReport(
            final Path eventStreamDirectory,
            final long startingRound,
            final boolean enableProgressReport)
            throws IOException {

        try (final IOIterator<DetailedConsensusEvent> eventIt =
                new MultiFileRunningHashIterator(
                        new EventStreamMultiFileIterator(eventStreamDirectory, startingRound))) {
            long numberOfEvents = 0;
            long transactionCount = 0;
            final DetailedConsensusEvent firstEvent = eventIt.peek();
            DetailedConsensusEvent lastEvent = null;

            final List<RoundRunningHash> runningHashes = new ArrayList<>();
            while (eventIt.hasNext()) {
                lastEvent = eventIt.next();
                transactionCount += lastEvent.getBaseEventHashedData().getTransactions().length;
                // save the running hash at the end of each round
                if (lastEvent.getConsensusData().isLastInRoundReceived()) {
                    runningHashes.add(
                            new RoundRunningHash(
                                    lastEvent.getConsensusData().getRoundReceived(),
                                    lastEvent.getRunningHash().getHash()));
                }
                numberOfEvents++;
                if (enableProgressReport && numberOfEvents % PROGRESS_INTERVAL == 0) {
                    // This is intended to be used in a terminal with a human in the loop,
                    // intentionally not logged.
                    System.out.println(
                            commaSeparatedValue(numberOfEvents) + " events have been parsed");
                }
            }
            return new EventStreamReport(
                    numberOfEvents, transactionCount, firstEvent, lastEvent, runningHashes);
        }
    }
}
