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
package com.swirlds.common.merkle.route.internal;

import java.util.Iterator;
import java.util.NoSuchElementException;

/** Iterates over steps in a route encoded using {@link UncompressedMerkleRoute}. */
public class UncompressedMerkleRouteIterator implements Iterator<Integer> {

    private final int[] routeData;
    private int nextIndex;

    public UncompressedMerkleRouteIterator(final int[] routeData) {
        this.routeData = routeData;
        nextIndex = 0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasNext() {
        return nextIndex < routeData.length;
    }

    /** {@inheritDoc} */
    @Override
    public Integer next() {
        if (nextIndex > routeData.length) {
            throw new NoSuchElementException();
        }
        final int step = routeData[nextIndex];
        nextIndex++;
        return step;
    }
}
