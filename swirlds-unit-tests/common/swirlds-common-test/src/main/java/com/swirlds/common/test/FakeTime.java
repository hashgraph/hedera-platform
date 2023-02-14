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
package com.swirlds.common.test;

import static com.swirlds.common.utility.Units.NANOSECONDS_TO_MILLISECONDS;

import com.swirlds.common.time.Time;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

/** A fake clock that can be easily manipulated for testing purposes. */
public class FakeTime implements Time {

    private final Instant start;
    private final long autoIncrement;
    private final AtomicLong elapsedNanos = new AtomicLong(0);

    private static final Instant DEFAULT_START;

    static {
        final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(2018, Calendar.AUGUST, 25, 1, 24, 9);
        DEFAULT_START = calendar.toInstant().plusMillis(693);
    }

    /**
     * Create a fake clock, and start it at whatever time the wall clock says is "now". Does not
     * auto increment.
     */
    public FakeTime() {
        this(DEFAULT_START, Duration.ZERO);
    }

    /**
     * Create a fake clock starting now.
     *
     * @param autoIncrement the {@link Duration} the clock will auto-increment by each time it is
     *     observed.
     */
    public FakeTime(final Duration autoIncrement) {
        this(Instant.now(), autoIncrement);
    }

    /**
     * Create a fake clock that starts at a particular time.
     *
     * @param start the starting timestamp relative to the epoch
     * @param autoIncrement the {@link Duration} the clock will auto-increment by each time it is
     *     observed.
     */
    public FakeTime(final Instant start, final Duration autoIncrement) {
        this.start = start;
        this.autoIncrement = autoIncrement.toNanos();
    }

    /** {@inheritDoc} */
    @Override
    public long nanoTime() {
        return elapsedNanos.getAndAdd(autoIncrement);
    }

    /** {@inheritDoc} */
    @Override
    public long currentTimeMillis() {
        return (long) (start.toEpochMilli() + nanoTime() * NANOSECONDS_TO_MILLISECONDS);
    }

    /** {@inheritDoc} */
    @Override
    public Instant now() {
        return start.plusNanos(nanoTime());
    }

    /** Tick forward by a single nanosecond. */
    public void tick() {
        tick(1);
    }

    /** Move the clock forward a number of nanoseconds. */
    public void tick(final long nanos) {
        if (nanos < 0) {
            throw new IllegalStateException("clock can only move forward");
        }
        elapsedNanos.getAndAdd(nanos);
    }

    /** Move the clock forward for an amount of time. */
    public void tick(final Duration time) {
        tick(time.toNanos());
    }

    /**
     * Directly set the value of the chronometer relative to when the chronometer was started
     *
     * <p>WARNING: this method can cause the clock to go backwards in time. This is impossible for a
     * "real" implementation that will be used in production environments.
     *
     * @param elapsedSinceStart the time that has elapsed since the chronometer has started
     * @deprecated it's really easy to do things with this method that can never happen in reality,
     *     don't use this method for new tests
     */
    @Deprecated
    public void set(final Duration elapsedSinceStart) {
        elapsedNanos.set(elapsedSinceStart.toNanos());
    }

    /**
     * Reset this chronometer to the state it was in immediately after construction.
     *
     * <p>WARNING: this method can cause the clock to go backwards in time. This is impossible for a
     * "real" implementation that will be used in production environments.
     */
    public void reset() {
        elapsedNanos.set(0);
    }

    /** Get the fake duration that has elapsed since fake clock was started. */
    public Duration elapsed() {
        return Duration.between(start, now());
    }
}
