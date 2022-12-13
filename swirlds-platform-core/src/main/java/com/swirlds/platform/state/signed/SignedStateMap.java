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

import static com.swirlds.common.utility.CommonUtils.throwArgNull;
import static com.swirlds.platform.state.signed.SignedStateUtilities.newSignedStateWrapper;

import com.swirlds.common.utility.AutoCloseableWrapper;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A thread safe map-like object for storing a number of states. This object automatically manages
 * reservations.
 */
public class SignedStateMap {

    private final Map<Long, SignedState> map = new HashMap<>();

    private final boolean strongReservations;

    /**
     * Create a new map for signed states.
     *
     * @param strongReservations if true then this map will hold strong reservations on states
     *     contained. If false then only weak reservations will be held.
     */
    public SignedStateMap(final boolean strongReservations) {
        this.strongReservations = strongReservations;
    }

    /**
     * Get a signed state. A strong reservation is taken on the state before this method returns.
     * This method may only be called if this map is configured to hold strong reservations.
     *
     * @param round the round to get
     * @return an auto-closable object that wraps a signed state. May point to a null state if there
     *     is no state for the given round. Will automatically release the state when closed.
     */
    public synchronized AutoCloseableWrapper<SignedState> get(final long round) {
        return get(round, true);
    }

    /**
     * Get a signed state. A reservation is taken on the state before this method returns.
     *
     * @param round the round to get
     * @param strong if true then take a strong reservation, will fail if this map is configured to
     *     only hold weak reservations
     * @return an auto-closable object that wraps a signed state. May point to a null state if there
     *     is no state for the given round. Will automatically release the state when closed.
     */
    @SuppressWarnings("DuplicatedCode")
    public synchronized AutoCloseableWrapper<SignedState> get(
            final long round, final boolean strong) {

        if (strong && !strongReservations) {
            throw new IllegalArgumentException(
                    "This map is configured to hold only weak reservations, so strong reservations"
                            + " can not be taken");
        }

        final SignedState signedState = map.get(round);

        return newSignedStateWrapper(signedState, strong);
    }

    /** Take a strong/weak reservation. */
    private void reserve(final SignedState signedState) {
        if (strongReservations) {
            signedState.reserveState();
        } else {
            signedState.weakReserveState();
        }
    }

    /** Release a strong/weak reservation. */
    private void release(final SignedState signedState) {
        if (strongReservations) {
            signedState.releaseState();
        } else {
            signedState.weakReleaseState();
        }
    }

    /**
     * Add a signed state to the map.
     *
     * @param signedState the signed state to add
     */
    public synchronized void put(final SignedState signedState) {
        throwArgNull(signedState, "signedState");

        reserve(signedState);

        final SignedState previousState = map.put(signedState.getRound(), signedState);

        if (previousState != null) {
            release(previousState);
        }
    }

    /**
     * Remove a signed state from the map if it is present.
     *
     * @param round the round to remove
     */
    public synchronized void remove(final long round) {
        final SignedState signedState = map.remove(round);
        if (signedState != null) {
            release(signedState);
        }
    }

    /** Remove all signed states from this collection. */
    public synchronized void clear() {
        for (final SignedState signedState : map.values()) {
            release(signedState);
        }
        map.clear();
    }

    /**
     * Finds the first signed state matching the supplied {@code predicate} and returns it, if
     * found, with a strong or weak reservation.
     *
     * @param predicate the search criteria
     * @param strong if true, take a strong reservation on the returned signed state, otherwise take
     *     a weak reservation
     * @return an {@link AutoCloseableWrapper} with the first matching signed state with the
     *     specified reservation take out on it, or an {@link AutoCloseableWrapper} with null if
     *     none was found
     */
    public synchronized AutoCloseableWrapper<SignedState> find(
            final Predicate<SignedState> predicate, final boolean strong) {
        for (final SignedState signedState : map.values()) {
            if (predicate.test(signedState)) {
                return newSignedStateWrapper(signedState, strong);
            }
        }
        return newSignedStateWrapper(null, strong);
    }

    /**
     * While holding a lock, execute a function that operates on an iterator of states in this map.
     * The iterator is permitted to remove elements from the map.
     *
     * <p>Using the iterator after this method returns is strictly prohibited.
     *
     * @param operation an operation that will use an iterator
     */
    public synchronized void atomicIteration(final Consumer<Iterator<SignedState>> operation) {

        final Iterator<SignedState> baseIterator = map.values().iterator();

        final Iterator<SignedState> iterator =
                new Iterator<>() {
                    private SignedState previous;

                    @Override
                    public boolean hasNext() {
                        return baseIterator.hasNext();
                    }

                    @Override
                    public SignedState next() {
                        previous = baseIterator.next();
                        return previous;
                    }

                    @Override
                    public void remove() {
                        baseIterator.remove();
                        if (previous != null) {
                            release(previous);
                        }
                    }
                };

        operation.accept(iterator);
    }

    /** Get the number of states in this map. */
    public synchronized int getSize() {
        return map.size();
    }
}
