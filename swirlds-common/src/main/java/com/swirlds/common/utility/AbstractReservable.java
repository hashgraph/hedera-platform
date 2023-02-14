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
package com.swirlds.common.utility;

import com.swirlds.common.Reservable;
import com.swirlds.common.exceptions.ReferenceCountException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

/** An implementation of {@link com.swirlds.common.Releasable Releasable}. */
public abstract class AbstractReservable implements Reservable {

    private final AtomicInteger reservationCount = new AtomicInteger(0);

    /**
     * This lambda method is used to increment the reservation count when {@link #reserve()} is
     * called.
     */
    private static final IntUnaryOperator RESERVE =
            current -> {
                if (current == DESTROYED_REFERENCE_COUNT) {
                    throw new ReferenceCountException(
                            "can not reserve node that has already been destroyed");
                } else {
                    return current + 1;
                }
            };

    /**
     * This lambda method is used to try to increment the reservation count when {@link
     * #tryReserve()} is called.
     */
    private static final IntUnaryOperator TRY_RESERVE =
            current -> {
                if (current == DESTROYED_REFERENCE_COUNT) {
                    return current;
                } else {
                    return current + 1;
                }
            };

    /**
     * This lambda method is used to decrement the reservation count when {@link #release()} is
     * called.
     */
    private static final IntUnaryOperator RELEASE =
            (final int current) -> {
                if (current == DESTROYED_REFERENCE_COUNT) {
                    throw new ReferenceCountException(
                            "can not release node that has already been destroyed");
                } else if (current == IMPLICIT_REFERENCE_COUNT || current == 1) {
                    return DESTROYED_REFERENCE_COUNT;
                } else {
                    return current - 1;
                }
            };

    /** {@inheritDoc} */
    @Override
    public void reserve() {
        reservationCount.updateAndGet(RESERVE);
    }

    /** {@inheritDoc} */
    @Override
    public boolean tryReserve() {
        return reservationCount.updateAndGet(TRY_RESERVE) != DESTROYED_REFERENCE_COUNT;
    }

    /** {@inheritDoc} */
    @Override
    public boolean release() {
        final int newReservationCount = reservationCount.updateAndGet(RELEASE);
        if (newReservationCount == DESTROYED_REFERENCE_COUNT) {
            onDestroy();
            return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDestroyed() {
        return reservationCount.get() == DESTROYED_REFERENCE_COUNT;
    }

    /** {@inheritDoc} */
    @Override
    public int getReservationCount() {
        return reservationCount.get();
    }

    /** This method is executed when this object becomes fully released. */
    protected void onDestroy() {
        // override this method if needed
    }
}
