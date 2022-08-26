/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.threading.framework.internal;

import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.Stoppable;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Boilerplate getters, setters, and configuration for queue thread configuration.
 *
 * @param <C>
 * 		the type of the class extending this class
 * @param <T>
 * 		the type of the objects in the queue
 */
public abstract class AbstractQueueThreadConfiguration<C extends AbstractQueueThreadConfiguration<C, T>, T>
		extends AbstractStoppableThreadConfiguration<C, InterruptableRunnable> {

	public static final int DEFAULT_CAPACITY = 100;
	public static final int DEFAULT_MAX_BUFFER_SIZE = 10_000;
	public static final int UNLIMITED_CAPACITY = -1;

	/**
	 * The maximum capacity of the queue. If -1 then there is no maximum capacity.
	 */
	private int capacity = DEFAULT_CAPACITY;

	/**
	 * The maximum size of the buffer used to drain the queue.
	 */
	private int maxBufferSize = DEFAULT_MAX_BUFFER_SIZE;

	/**
	 * The method used to handle items from the queue.
	 */
	private InterruptableConsumer<T> handler;

	/** A runnable to execute when waiting for an item to become available in the queue. */
	private InterruptableRunnable waitForItemRunnable;

	/** An initialized queue to use. */
	private BlockingQueue<T> queue;

	protected AbstractQueueThreadConfiguration() {
		super();

		// Queue threads are not interruptable by default
		setStopBehavior(Stoppable.StopBehavior.BLOCKING);
	}

	/**
	 * Copy constructor.
	 *
	 * @param that
	 * 		the configuration to copy
	 */
	protected AbstractQueueThreadConfiguration(final AbstractQueueThreadConfiguration<C, T> that) {
		super(that);

		this.capacity = that.capacity;
		this.maxBufferSize = that.maxBufferSize;
		this.handler = that.handler;
		this.waitForItemRunnable = that.waitForItemRunnable;
		this.queue = that.queue;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract AbstractQueueThreadConfiguration<C, T> copy();

	protected QueueThread<T> buildQueueThread(final boolean start) {
		final QueueThread<T> thread = new QueueThreadImpl<>(this);

		if (start) {
			thread.start();
		}

		return thread;
	}

	/**
	 * Get the capacity for created threads. If -1 then the queue has no maximum capacity.
	 */
	public int getCapacity() {
		return capacity;
	}

	/**
	 * Set the capacity for created threads.
	 *
	 * @return this object
	 */
	@SuppressWarnings("unchecked")
	public C setCapacity(final int capacity) {
		throwIfImmutable();
		this.capacity = capacity;
		return (C) this;
	}

	/**
	 * Set the capacity to unlimited.
	 *
	 * @return this object
	 */
	@SuppressWarnings("unchecked")
	public C setUnlimitedCapacity() {
		throwIfImmutable();
		this.capacity = UNLIMITED_CAPACITY;
		return (C) this;
	}

	/**
	 * Get the maximum buffer size for created threads. Buffer size is not the same as queue capacity, it has to do
	 * with the buffer that is used when draining the queue.
	 */
	public int getMaxBufferSize() {
		return maxBufferSize;
	}

	/**
	 * Set the maximum buffer size for created threads. Buffer size is not the same as queue capacity, it has to do
	 * with the buffer that is used when draining the queue.
	 *
	 * @return this object
	 */
	@SuppressWarnings("unchecked")
	public C setMaxBufferSize(final int maxBufferSize) {
		throwIfImmutable();
		this.maxBufferSize = maxBufferSize;
		return (C) this;
	}

	/**
	 * Get the handler method that will be called against every item in the queue.
	 */
	protected InterruptableConsumer<T> getHandler() {
		return handler;
	}

	/**
	 * Set the handler method that will be called against every item in the queue.
	 *
	 * @return this object
	 */
	@SuppressWarnings("unchecked")
	protected C setHandler(final InterruptableConsumer<T> handler) {
		throwIfImmutable();
		this.handler = handler;
		return (C) this;
	}

	/**
	 * Get the runnable to execute when waiting for an item to become available in the queue.
	 */
	public InterruptableRunnable getWaitForItemRunnable() {
		return waitForItemRunnable;
	}

	/**
	 * Set the runnable to execute when there is nothing in the queue to handle. The default is to poll the queue,
	 * waiting a certain amount of time for it to return an item.
	 *
	 * @param waitForItemRunnable
	 * 		the runnable to execute
	 * @return this object
	 */
	@SuppressWarnings("unchecked")
	public C setWaitForItemRunnable(final InterruptableRunnable waitForItemRunnable) {
		throwIfImmutable();
		this.waitForItemRunnable = waitForItemRunnable;
		return (C) this;
	}

	/**
	 * Build a queue. Should only be called if a queue has not been provided.
	 *
	 * @return a newly initialized queue
	 */
	private BlockingQueue<T> buildDefaultQueue() {
		if (capacity > 0) {
			return new LinkedBlockingQueue<>(capacity);
		} else {
			return new LinkedBlockingQueue<>();
		}
	}

	/**
	 * Get the queue. If it doesn't exist then initialize with a default queue, and return that new queue.
	 *
	 * @return the queue that should be used
	 */
	protected BlockingQueue<T> getOrInitializeQueue() {
		if (getQueue() == null) {
			setQueue(buildDefaultQueue());
		}
		return getQueue();
	}

	/**
	 * Get the queue specified by the user, or null if none has been specified.
	 */
	public BlockingQueue<T> getQueue() {
		return queue;
	}

	/**
	 * Sets the initialized queue. The default is a {@link LinkedBlockingQueue}.
	 *
	 * @param queue
	 * 		the initialized queue
	 * @return this object
	 */
	@SuppressWarnings("unchecked")
	public C setQueue(final BlockingQueue<T> queue) {
		throwIfImmutable();
		this.queue = queue;
		return (C) this;
	}
}
