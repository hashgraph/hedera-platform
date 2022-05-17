/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.threading;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.swirlds.logging.LogMarker.ERROR;

/**
 * Implements a thread that continuously takes elements from a queue and handles them.
 *
 * @param <T>
 * 		the type of the item in the queue
 */
public class QueueThread<T> implements BlockingQueue<T>, StoppableThread {

	private static final Logger log = LogManager.getLogger();

	private static final int WAIT_FOR_WORK_DELAY_MS = 10;

	protected final BlockingQueue<T> queue;

	private final int bufferSize;
	
	private final List<T> buffer;

	private final List<QueueThreadThreshold> thresholds;

	private final QueueThreadHandler<T> handler;

	private final StoppableThread stoppableThread;

	private final InterruptableRunnable waitForItemRunnable;

	/**
	 * All instances of this class should be created via the appropriate configuration object.
	 * <p>
	 * Unlike previous iterations of this class, this constructor DOES NOT start the background handler thread.
	 * Call {@link #start()} to start the handler thread.
	 *
	 * @param configuration
	 * 		the configuration object
	 */
	protected QueueThread(final QueueThreadConfiguration<T> configuration) {
		final int capacity = configuration.getCapacity();
		if (configuration.getQueue() != null) {
			queue = configuration.getQueue();
		} else if (capacity > 0) {
			queue = new LinkedBlockingQueue<>(capacity);
		} else {
			queue = new LinkedBlockingQueue<>();
		}

		if (capacity > 0) {
			bufferSize = Math.min(capacity, configuration.getMaxBufferSize());
		} else {
			bufferSize = configuration.getMaxBufferSize();
		}

		thresholds = new LinkedList<>(configuration.getThresholds());

		buffer = new ArrayList<>(bufferSize);

		handler = configuration.getHandler();

		this.waitForItemRunnable = Objects.requireNonNullElseGet(configuration.getWaitForItemRunnable(),
				() -> this::waitForItem);

		stoppableThread = configuration.getStoppableThreadConfiguration()
				.setWork(this::doWork)
				.setInterruptable(configuration.isInterruptable())
				.setFinalCycleWork(this::doFinalCycleWork)
				.build();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void start() {
		stoppableThread.start();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void pause() throws InterruptedException {
		stoppableThread.pause();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void resume() {
		stoppableThread.resume();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void join() throws InterruptedException {
		stoppableThread.join();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void join(final long millis) throws InterruptedException {
		stoppableThread.join(millis);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void join(final long millis, final int nanos) throws InterruptedException {
		stoppableThread.join(millis, nanos);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void stop() {
		stoppableThread.stop();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void interrupt() {
		stoppableThread.interrupt();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isAlive() {
		return stoppableThread.isAlive();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isHanging() {
		return stoppableThread.isHanging();
	}

	/**
	 * Check thresholds, trigger callbacks if thresholds are exceeded.
	 */
	private void checkThresholds() {
		if (thresholds.size() > 0) {
			final int size = queue.size();
			final Instant now = Instant.now();
			for (final QueueThreadThreshold threshold : thresholds) {
				threshold.checkValue(now, size);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	private void doWork() throws InterruptedException {
		checkThresholds();

		queue.drainTo(buffer, bufferSize);
		if (buffer.size() == 0) {
			waitForItemRunnable.run();
			return;
		}

		for (final T item : buffer) {
			handler.handle(item);
		}
		buffer.clear();
	}

	/**
	 * Wait a while for the next item to become available and handle it. If no item becomes available before
	 * a timeout then return without doing any work.
	 *
	 * @throws InterruptedException
	 * 		if this method is interrupted during execution
	 */
	private void waitForItem() throws InterruptedException {
		final T item = queue.poll(WAIT_FOR_WORK_DELAY_MS, TimeUnit.MILLISECONDS);
		if (item != null) {
			handler.handle(item);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	private void doFinalCycleWork() throws InterruptedException {
		while (queue.size() > 0) {
			queue.drainTo(buffer, bufferSize);
			for (final T item : buffer) {
				handler.handle(item);
			}
			buffer.clear();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean add(final T t) {
		return queue.add(t);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean offer(final T t) {
		return queue.offer(t);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T remove() {
		return queue.remove();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T poll() {
		return queue.poll();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T element() {
		return queue.element();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T peek() {
		return queue.peek();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void put(final T t) throws InterruptedException {
		queue.put(t);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean offer(final T t, final long timeout, final TimeUnit unit) throws InterruptedException {
		return queue.offer(t, timeout, unit);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T take() throws InterruptedException {
		return queue.take();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T poll(final long timeout, final TimeUnit unit) throws InterruptedException {
		return queue.poll(timeout, unit);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int remainingCapacity() {
		return queue.remainingCapacity();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean remove(final Object o) {
		return queue.remove(o);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean containsAll(final Collection<?> c) {
		return queue.containsAll(c);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean addAll(final Collection<? extends T> c) {
		return queue.addAll(c);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean removeAll(final Collection<?> c) {
		return queue.removeAll(c);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean retainAll(final Collection<?> c) {
		return queue.retainAll(c);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		if (!isAlive()) {
			queue.clear();
			return;
		}

		try {
			pause();
		} catch (final InterruptedException e) {
			log.error(ERROR.getMarker(),
					"interrupted while attempting to clear queue thread {}", this.stoppableThread.getName());
			Thread.currentThread().interrupt();
			return;
		}
		queue.clear();
		resume();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int size() {
		return queue.size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEmpty() {
		return queue.isEmpty();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean contains(final Object o) {
		return queue.contains(o);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Iterator<T> iterator() {
		return queue.iterator();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object[] toArray() {
		return queue.toArray();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T1> T1[] toArray(final T1[] a) {
		return queue.toArray(a);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int drainTo(final Collection<? super T> c) {
		return queue.drainTo(c);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int drainTo(final Collection<? super T> c, final int maxElements) {
		return queue.drainTo(c, maxElements);
	}

	/**
	 * Get the name of this thread.
	 */
	public String getName() {
		return stoppableThread.getName();
	}

}
