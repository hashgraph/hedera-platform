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
package com.swirlds.platform.test.event.generator;

import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.test.event.IndexedEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * A base graph generator class that provides most functionality of a graph generator except for
 * determining how to generate the next event.
 */
public abstract class AbstractGraphGenerator<T extends AbstractGraphGenerator<T>>
        implements GraphGenerator<T> {

    /** The total number of events that have been emitted by this generator. */
    private long numEventsGenerated;

    /** The initial seed of this generator. */
    private final long initialSeed;

    /** The source of all randomness for this class. */
    private Random random;

    /** A map that holds the maximum event generation for each creator */
    private final Map<Long, Long> maxGenerationPerCreator;

    protected AbstractGraphGenerator(final long initialSeed) {
        this.initialSeed = initialSeed;
        random = new Random(initialSeed);
        maxGenerationPerCreator = new HashMap<>();
    }

    /** Child classes should reset internal metadata in this method. */
    protected abstract void resetInternalData();

    /**
     * {@inheritDoc}
     *
     * <p>Child classes must call super.reset() if they override this method.
     */
    @Override
    public final void reset() {
        numEventsGenerated = 0;
        random = new Random(initialSeed);
        maxGenerationPerCreator.clear();
        resetInternalData();
    }

    /**
     * Build the event that will be returned by getNextEvent.
     *
     * @param eventIndex the index of the event to build
     */
    protected abstract IndexedEvent buildNextEvent(long eventIndex);

    /** {@inheritDoc} */
    public final IndexedEvent generateEvent() {
        final IndexedEvent next = buildNextEvent(numEventsGenerated);
        numEventsGenerated++;
        updateMaxGeneration(next);
        return next;
    }

    /** {@inheritDoc} */
    @Override
    public final long getNumEventsGenerated() {
        return numEventsGenerated;
    }

    /** Get the Random object to be used by this class. */
    protected final Random getRandom() {
        return random;
    }

    /** The seed used at the start of this generator. */
    public final long getInitialSeed() {
        return initialSeed;
    }

    /** Updates the max generation based on the latest event */
    private void updateMaxGeneration(final IndexedEvent event) {
        maxGenerationPerCreator.merge(event.getCreatorId(), event.getGeneration(), Math::max);
    }

    /** {@inheritDoc} */
    @Override
    public long getMaxGeneration(final long creatorId) {
        return maxGenerationPerCreator.getOrDefault(creatorId, EventConstants.GENERATION_UNDEFINED);
    }

    /** {@inheritDoc} */
    @Override
    public long getMaxGeneration() {
        return maxGenerationPerCreator.values().stream()
                .max(Long::compareTo)
                .orElse(GraphGenerations.FIRST_GENERATION);
    }
}
