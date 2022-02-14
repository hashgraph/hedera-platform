/*
 * (c) 2016-2022 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
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
public class QueueThread<T> implements BlockingQueue<T> {

	private static final Logger log = LogManager.getLogger();

	private static final int WAIT_FOR_WORK_DELAY_MS = 10;

	private final BlockingQueue<T> queue;

	private final int bufferSize;
	private final List<T> buffer;

	private final List<QueueThreadThreshold> thresholds;

	private final QueueThreadHandler<T> handler;

	private final StoppableThread stoppableThread;

	/**
	 * This constructor is intentionally package private. All instances of this class should be created via
	 * the appropriate configuration object.
	 * <p>
	 * Unlike previous iterations of this class, this constructor DOES NOT start the background handler thread.
	 * Call {@link #start()} to start the handler thread.
	 *
	 * @param configuration
	 * 		the configuration object
	 */
	QueueThread(final QueueThreadConfiguration<T> configuration) {
		final int capacity = configuration.getCapacity();
		if (capacity >= 0) {
			queue = new LinkedBlockingQueue<>(capacity);
			bufferSize = Math.min(capacity, configuration.getMaxBufferSize());
		} else {
			queue = new LinkedBlockingQueue<>();
			bufferSize = configuration.getMaxBufferSize();
		}

		thresholds = new LinkedList<>(configuration.getThresholds());

		buffer = new ArrayList<>(bufferSize);

		handler = configuration.getHandler();

		stoppableThread = configuration.getStoppableThreadConfiguration()
				.setWork(this::doWork)
				.setFinalCycleWork(this::doFinalCycleWork)
				.build();
	}

	/**
	 * Start the thread.
	 */
	public void start() {
		stoppableThread.start();
	}

	/**
	 * Causes the thread to finish the current call to {@link #doWork()} and to then block. Thread remains blocked
	 * until {@link #resume()} is called.
	 */
	public void pause() throws InterruptedException {
		stoppableThread.pause();
	}

	/**
	 * This method can be called to resume work on the thread after a {@link #pause()} call.
	 */
	public void resume() {
		stoppableThread.resume();
	}

	/**
	 * Join the thread. Equivalent to {@link Thread#join()}.
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	public void join() throws InterruptedException {
		stoppableThread.join();
	}

	/**
	 * Join the thread. Equivalent to {@link Thread#join(long)}.
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	public void join(long millis) throws InterruptedException {
		stoppableThread.join(millis);
	}

	/**
	 * Join the thread. Equivalent to {@link Thread#join(long, int)}.
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	public void join(long millis, int nanos) throws InterruptedException {
		stoppableThread.join(millis, nanos);
	}

	/**
	 * <p>
	 * Attempt to gracefully stop the thread.
	 * </p>
	 *
	 * <p>
	 * Do not call this method inside the internal thread (i.e. the one calling the handler over and over). This
	 * method joins on that thread, and will deadlock if it attempts to join on itself.
	 * </p>
	 */
	public void stop() {
		stoppableThread.stop();
	}

	/**
	 * Interrupt this thread.
	 */
	public void interrupt() {
		stoppableThread.interrupt();
	}

	/**
	 * Check if this thread is currently alive.
	 */
	public boolean isAlive() {
		return stoppableThread.isAlive();
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
			waitForWork();
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
	private void waitForWork() throws InterruptedException {
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
		try {
			pause();
		} catch (InterruptedException e) {
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
