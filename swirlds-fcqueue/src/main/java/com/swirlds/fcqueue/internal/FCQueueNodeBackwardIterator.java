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
package com.swirlds.fcqueue.internal;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.fcqueue.FCQueue;

/**
 * An iterator for FCQueue, starts at the tail of the given queue, ends at the head of the given
 * queue
 *
 * @param <E> the type of elements in the FCQueue
 */
public final class FCQueueNodeBackwardIterator<E extends FastCopyable & SerializableHashable>
        extends FCQueueNodeIterator<E> {

    /**
     * start this iterator at the tail of the given queue
     *
     * @param queue the queue to iterate over
     * @param head the head of the queue
     * @param tail the tail of the queue
     */
    public FCQueueNodeBackwardIterator(
            final FCQueue<E> queue, final FCQueueNode<E> head, final FCQueueNode<E> tail) {
        super(queue, tail, head);
    }

    @Override
    FCQueueNode<E> nextNode() {
        return current.getTowardHead();
    }
}
