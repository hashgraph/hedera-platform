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
package com.swirlds.platform.state.signed;

import com.swirlds.common.time.Time;
import com.swirlds.common.utility.StackTrace;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks the usage of a signed state over time, storing stack traces that can be used at a later
 * time for debugging.
 */
public class SignedStateHistory {

    /** An operation performed on a signed state. */
    public enum SignedStateAction {
        /** The signed state constructor is called. */
        CREATION,
        /** {@link SignedState#weakReserveState()} is called */
        WEAK_RESERVATION,
        /** {@link SignedState#weakReleaseState()} is called */
        WEAK_RELEASE,
        /** {@link SignedState#reserveState()} is called */
        STRONG_RESERVATION,
        /** {@link SignedState#weakReleaseState()} is called */
        STRONG_RELEASE,
        /** The inner state is archived. */
        ARCHIVE_STATE,
        /** The inner state is released. */
        RELEASE_STATE
    }

    /**
     * A record of an action taking with a signed state.
     *
     * @param action the action
     * @param stackTrace where the action was performed
     * @param timestamp the timestamp of the action
     * @param strongReservations the strong reservation count prior to the action
     * @param weakReservations the weak reservation count prior to the action
     */
    public record SignedStateActionReport(
            SignedStateAction action,
            StackTrace stackTrace,
            Instant timestamp,
            int strongReservations,
            int weakReservations) {

        /** {@inheritDoc} */
        @Override
        public String toString() {

            final StringBuilder sb = new StringBuilder();

            sb.append(action)
                    .append(" @ ")
                    .append(timestamp)
                    .append("\n")
                    .append("initial strong reservations = ")
                    .append(strongReservations)
                    .append(", initial weak reservations = ")
                    .append(weakReservations)
                    .append("\n")
                    .append(stackTrace);

            return sb.toString();
        }
    }

    private final Queue<SignedStateActionReport> actions = new ConcurrentLinkedQueue<>();
    private final Time time;
    private AtomicLong round = new AtomicLong(-1);

    /**
     * Create a new object to track the history of a signed state.
     *
     * @param time used to access wall clock time
     */
    public SignedStateHistory(final Time time) {
        this.time = time;
    }

    /** Set the round of the signed state. */
    public void setRound(final long round) {
        this.round.set(round);
    }

    /**
     * Record an action on a signed state.
     *
     * @param action the action
     * @param strongReservations the number of strong reservations before the action
     * @param weakReservations the number of weak reservations after the action
     */
    public void recordAction(
            final SignedStateAction action, int strongReservations, int weakReservations) {
        actions.add(
                new SignedStateActionReport(
                        action,
                        StackTrace.getStackTrace(),
                        time.now(),
                        strongReservations,
                        weakReservations));
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SignedState history for round ").append(round.get()).append("\n");
        actions.forEach(report -> sb.append(report).append("\n"));
        return sb.toString();
    }
}
