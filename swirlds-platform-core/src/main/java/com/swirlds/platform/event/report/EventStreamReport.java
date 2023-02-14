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

import com.swirlds.common.system.events.DetailedConsensusEvent;
import com.swirlds.common.utility.TextTable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Useful information about an event stream
 *
 * @param numberOfEvents the number of events in the stream
 * @param transactionCount the total number of transactions in the stream
 * @param firstEvent the first event in the stream
 * @param lastEvent the last event in the stream
 * @param runningHashes a list of round numbers and the running hash of the event stream at the end
 *     of that round
 */
public record EventStreamReport(
        long numberOfEvents,
        long transactionCount,
        DetailedConsensusEvent firstEvent,
        DetailedConsensusEvent lastEvent,
        List<RoundRunningHash> runningHashes) {

    private static final int HASH_STRING_LENGTH = 12;

    /**
     * @return the consensus time of the last event in the stream, or null if there are no events
     */
    public Instant lastEventTimestamp() {
        return lastEvent == null ? null : lastEvent.getConsensusData().getConsensusTimestamp();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("\n");

        final TextTable table =
                new TextTable("Event Stream Info", "", "first event", "last event", "total/delta");
        table.addRow(
                "round",
                commaSeparatedValue(firstEvent.getConsensusData().getRoundReceived()),
                commaSeparatedValue(lastEvent.getConsensusData().getRoundReceived()),
                commaSeparatedValue(
                        lastEvent.getConsensusData().getRoundReceived()
                                - firstEvent.getConsensusData().getRoundReceived()));
        table.addRow(
                "timestamp",
                firstEvent.getConsensusData().getConsensusTimestamp(),
                lastEvent.getConsensusData().getConsensusTimestamp(),
                commaSeparatedValue(
                                Duration.between(
                                                firstEvent
                                                        .getConsensusData()
                                                        .getConsensusTimestamp(),
                                                lastEvent
                                                        .getConsensusData()
                                                        .getConsensusTimestamp())
                                        .toSeconds())
                        + " s");
        table.addRow(
                "hash",
                firstEvent.getHash().toShortString(HASH_STRING_LENGTH),
                lastEvent.getHash().toShortString(HASH_STRING_LENGTH));
        table.addRow(
                "running hash",
                firstEvent.getRunningHash().getHash().toShortString(HASH_STRING_LENGTH),
                lastEvent.getRunningHash().getHash().toShortString(HASH_STRING_LENGTH));
        table.addRow(
                "consensus order",
                commaSeparatedValue(firstEvent.getConsensusData().getConsensusOrder()),
                commaSeparatedValue(lastEvent.getConsensusData().getConsensusOrder()),
                commaSeparatedValue(
                                lastEvent.getConsensusData().getConsensusOrder()
                                        - firstEvent.getConsensusData().getConsensusOrder())
                        + " *");
        table.addRow(
                "generation",
                commaSeparatedValue(firstEvent.getBaseEventHashedData().getGeneration()),
                commaSeparatedValue(lastEvent.getBaseEventHashedData().getGeneration()),
                commaSeparatedValue(
                        lastEvent.getBaseEventHashedData().getGeneration()
                                - firstEvent.getBaseEventHashedData().getGeneration()));
        table.addRow(
                "creator ID",
                firstEvent.getBaseEventHashedData().getCreatorId(),
                lastEvent.getBaseEventHashedData().getCreatorId());
        table.addRow(
                "transaction count",
                commaSeparatedValue(firstEvent.getBaseEventHashedData().getTransactions().length),
                commaSeparatedValue(lastEvent.getBaseEventHashedData().getTransactions().length),
                commaSeparatedValue(transactionCount));
        table.addRow(
                "last in round",
                firstEvent.getConsensusData().isLastInRoundReceived() ? "yes" : "no",
                lastEvent.getConsensusData().isLastInRoundReceived() ? "yes" : "no");

        table.addToStringBuilder(sb);

        sb.append("\n\n");
        sb.append("* The total number of events in the event stream.\n\n");
        sb.append("First event full hash: ").append(firstEvent.getHash()).append("\n");
        sb.append("First event full running hash: ")
                .append(firstEvent.getRunningHash().getHash())
                .append("\n");
        sb.append("Last event full hash: ").append(lastEvent.getHash()).append("\n");
        sb.append("First event full running hash: ")
                .append(lastEvent.getRunningHash().getHash())
                .append("\n");

        return sb.toString();
    }
}
