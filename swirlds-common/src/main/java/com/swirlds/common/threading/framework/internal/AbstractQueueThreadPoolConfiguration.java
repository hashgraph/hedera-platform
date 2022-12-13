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
package com.swirlds.common.threading.framework.internal;

import com.swirlds.common.threading.framework.QueueThreadPool;
import com.swirlds.common.threading.framework.config.QueueThreadPoolConfiguration;

/**
 * Boilerplate getters, setters, and configuration for queue thread pool configuration.
 *
 * @param <C> the type of the class extending this class
 * @param <T> the type of the objects in the queue
 */
public abstract class AbstractQueueThreadPoolConfiguration<
                C extends AbstractQueueThreadConfiguration<C, T>, T>
        extends AbstractQueueThreadConfiguration<QueueThreadPoolConfiguration<T>, T> {

    private static final int DEFAULT_THREAD_COUNT = Runtime.getRuntime().availableProcessors();

    private int threadCount = DEFAULT_THREAD_COUNT;

    protected AbstractQueueThreadPoolConfiguration() {}

    /**
     * Copy constructor.
     *
     * @param that the configuration to copy
     */
    protected AbstractQueueThreadPoolConfiguration(
            final AbstractQueueThreadPoolConfiguration<C, T> that) {
        super(that);

        this.threadCount = that.threadCount;
    }

    /** {@inheritDoc} */
    @Override
    public abstract AbstractQueueThreadPoolConfiguration<C, T> copy();

    /**
     * Build a new QueueThreadPool from this configuration.
     *
     * @param start if true then automatically start the threads in the pool
     * @return a QueueThreadPool
     */
    protected QueueThreadPool<T> buildQueueThreadPool(final boolean start) {
        final QueueThreadPool<T> pool = new QueueThreadPoolImpl<>(this);

        if (start) {
            pool.start();
        }

        return pool;
    }

    /**
     * Get the number of threads in the pool.
     *
     * @return the number of threads in the pool
     */
    public int getThreadCount() {
        return threadCount;
    }

    /**
     * Set the number of threads in the pool.
     *
     * @param threadCount the number of threads in the pool
     * @return this object
     */
    @SuppressWarnings("unchecked")
    public C setThreadCount(final int threadCount) {
        throwIfImmutable();
        this.threadCount = threadCount;
        return (C) this;
    }
}
