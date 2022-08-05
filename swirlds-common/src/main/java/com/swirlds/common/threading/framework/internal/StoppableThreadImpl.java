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

import com.swirlds.common.threading.framework.ThreadSeed;
import com.swirlds.common.threading.framework.TypedStoppableThread;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.swirlds.common.threading.interrupt.Uninterruptable.retryIfInterrupted;
import static com.swirlds.common.utility.CompareTo.isGreaterThan;
import static com.swirlds.common.utility.StackTrace.getStackTrace;
import static com.swirlds.common.utility.Units.NANOSECONDS_TO_MILLISECONDS;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.THREADS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Implements the concept of a thread that can be gracefully stopped. Once stopped this instance can no longer be used
 * and must be recreated.
 *
 * @param <T>
 * 		the type of instance that will do work
 */
public class StoppableThreadImpl<T extends InterruptableRunnable> implements TypedStoppableThread<T> {

	private static final Logger LOG = LogManager.getLogger();

	/**
	 * The current status of this thread.
	 */
	private final AtomicReference<Status> status = new AtomicReference<>(Status.NOT_STARTED);

	/**
	 * If true then interrupt the thread when it is closed.
	 */
	private final boolean interruptable;

	/**
	 * The amount of time in milliseconds to wait after setting the thread status to {@link Status#DYING}
	 * before interrupting the thread if {@link #interruptable} is true.
	 */
	private final int joinWaitMs;

	/**
	 * Used to wait until the work thread has started its pause.
	 */
	private final AtomicReference<CountDownLatch> pauseStartedLatch = new AtomicReference<>(new CountDownLatch(1));

	/**
	 * Used by the work thread to wait until a pause has completed.
	 */
	private final AtomicReference<CountDownLatch> pauseCompletedLatch = new AtomicReference<>(new CountDownLatch(1));

	/**
	 * The minimum amount of time that a cycle is permitted to take. If a cycle completes in less time,
	 * then sleep until the minimum period has been met.
	 */
	private final Duration minimumPeriod;

	/**
	 * Used to enforce the minimum period.
	 */
	private Instant previousCycleStart;

	/**
	 * The work to perform on the thread. Called over and over.
	 */
	private final T work;

	/**
	 * The work that is done after the thread is closed. Ignored if this thread is interruptable.
	 */
	private final InterruptableRunnable finalCycleWork;

	/**
	 * The thread on which to do work.
	 */
	private final AtomicReference<Thread> thread = new AtomicReference<>();

	/**
	 * True if this thread was injected.
	 */
	private volatile boolean injected;

	/**
	 * Used by join, necessary in case join is called before the thread is started.
	 */
	private final CountDownLatch started = new CountDownLatch(1);

	/**
	 * This latch is when joining a thread that was injected as a seed. When a seed is injected,
	 * the thread may live after the stoppable thread finishes, and so join shouldn't wait for
	 * the thread to actually die. Ignored if this thread was not injected.
	 */
	private final CountDownLatch finished = new CountDownLatch(1);

	/**
	 * If a thread is requested to stop but does not it is considered to be hanging after this
	 * period. If 0 then thread is never considered to be hanging.
	 */
	private final Duration hangingThreadDuration;

	/**
	 * True if the thread is in a hanging state.
	 */
	private volatile boolean hanging;

	/**
	 * The configuration used to create this stoppable thread.
	 */
	private final AbstractStoppableThreadConfiguration<?, T> configuration;

	/**
	 * Create a new stoppable thread.
	 */
	public StoppableThreadImpl(final AbstractStoppableThreadConfiguration<?, T> configuration) {
		this.configuration = configuration;

		interruptable = configuration.isInterruptable();
		joinWaitMs = configuration.getJoinWaitMs();
		hangingThreadDuration = configuration.getHangingThreadPeriod();

		work = configuration.getWork();
		finalCycleWork = configuration.getFinalCycleWork();

		minimumPeriod = configuration.getMinimumPeriod();

		configuration.setRunnable(this::run);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized ThreadSeed buildSeed() {
		if (injected) {
			throw new IllegalStateException("this StoppableThread has already built a seed");
		}
		if (thread.get() != null) {
			throw new IllegalStateException("can not build seed after thread is started");
		}

		injected = true;

		return configuration.buildStoppableThreadSeed(this);
	}

	/**
	 * Mark this stoppable thread as injected.
	 */
	protected void setInjected() {
		this.injected = true;
	}

	/**
	 * Check if this stoppable thread has been started or injected.
	 *
	 * @return true if started or injected
	 */
	protected boolean hasBeenStartedOrInjected() {
		return injected || (thread.get() != null);
	}

	/**
	 * The method to execute on the {@link #thread} object.
	 */
	private void run() {
		try {
			for (Status currentStatus = status.get();
				 currentStatus != Status.DYING;
				 currentStatus = status.get()) {

				if (currentStatus == Status.PAUSED) {
					waitUntilUnpaused();
				} else {
					enforceMinimumPeriod();
					doWork();
				}
			}
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			status.set(Status.DEAD);
			finished.countDown();
		}
	}

	/**
	 * If a minimum period is configured then enforce it.
	 */
	private void enforceMinimumPeriod() throws InterruptedException {
		if (minimumPeriod == null) {
			return;
		}

		final Instant now = Instant.now();

		if (previousCycleStart == null) {
			previousCycleStart = now;
			return;
		}

		final Duration previousDuration = Duration.between(previousCycleStart, now);
		final Duration remainingCycleTime = minimumPeriod.minus(previousDuration);

		if (isGreaterThan(remainingCycleTime, Duration.ZERO)) {
			NANOSECONDS.sleep(remainingCycleTime.toNanos());
			previousCycleStart = now.plus(remainingCycleTime);
		} else {
			previousCycleStart = now;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean start() {
		if (injected) {
			throw new IllegalStateException("Thread can not be started if it has built a seed");
		}

		final Status originalStatus = status.get();
		if (originalStatus != Status.NOT_STARTED) {
			LOG.error(EXCEPTION.getMarker(),
					"can not start thread {} when it is in the state {}", this::getName, originalStatus::name);
			return false;
		}

		final Thread t = configuration.buildThread(false);
		markAsStarted(t);
		t.start();

		return true;
	}

	/**
	 * Get the current thread. Blocks until the thread has been started.
	 * This method is not interruptable, so calling this method before
	 * the thread is started is a big commitment.
	 *
	 * @return the thread
	 */
	private Thread uninterruptableGetThread() {
		Thread t = thread.get();
		while (t == null) {
			retryIfInterrupted(() -> MILLISECONDS.sleep(1));
			t = thread.get();
		}

		return t;
	}

	/**
	 * Get the current thread. Blocks until the thread has been started.
	 * This method is not interruptable, so calling this method before
	 * the thread is started is a big commitment.
	 *
	 * @return the thread
	 */
	private Thread getThread() throws InterruptedException {
		Thread t = thread.get();
		while (t == null) {
			MILLISECONDS.sleep(1);
			t = thread.get();
		}

		return t;
	}

	/**
	 * Called when a pause has been requested. Waits until the pause has been lifted.
	 */
	private void waitUntilUnpaused() throws InterruptedException {
		final CountDownLatch pauseCompleted = pauseCompletedLatch.get();

		// Alert pausing thread that the pause has started.
		pauseStartedLatch.get().countDown();

		// Wait until the pause has completed.
		pauseCompleted.await();
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("StatementWithEmptyBody")
	@Override
	public synchronized boolean pause() {

		final Status originalStatus = status.get();
		if (originalStatus != Status.ALIVE) {
			LOG.error(EXCEPTION.getMarker(),
					"can not pause thread {} when it is in the state {}", this::getName, originalStatus::name);
			return false;
		}

		final Thread t = thread.get();

		status.set(Status.PAUSED);

		// Wait until the target thread is paused or interrupted.
		retryIfInterrupted(() -> {
			while (!t.isInterrupted() && !pauseStartedLatch.get().await(1, TimeUnit.MILLISECONDS)) {
				// Spin until loop conditions allow exit. Conditions will block, preventing pure busy wait.
			}
		});

		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean resume() {

		final Status originalStatus = status.get();
		if (originalStatus != Status.PAUSED) {
			LOG.error(EXCEPTION.getMarker(),
					"can not resume thread {} when it is in the state {}", this::getName, originalStatus::name);
			return false;
		}

		status.set(Status.ALIVE);
		unblockPausedThread();

		return true;
	}

	/**
	 * Calling this method unblocks the thread if it is in a paused state.
	 */
	private void unblockPausedThread() {
		pauseCompletedLatch.get().countDown();
		pauseStartedLatch.set(new CountDownLatch(1));
		pauseCompletedLatch.set(new CountDownLatch(1));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void join() throws InterruptedException {
		while (status.get() == Status.NOT_STARTED) {
			MILLISECONDS.sleep(1);
		}

		if (injected) {
			// This stoppable thread was injected into a pre-existing thread. Wait for the stoppable thread to finish,
			// but don't worry about the underlying thread being stopped.
			finished.await();
		} else {
			// This stoppable thread was run on top of its own thread. Wait for that thread to be closed.
			thread.get().join();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("ResultOfMethodCallIgnored")
	@Override
	public void join(final long millis) throws InterruptedException {
		while (status.get() == Status.NOT_STARTED) {
			MILLISECONDS.sleep(1);
		}

		if (injected) {
			// This stoppable thread was injected into a pre-existing thread. Wait for the stoppable thread to finish,
			// but don't worry about the underlying thread being stopped.
			finished.await(millis, TimeUnit.MILLISECONDS);
		} else {
			// This stoppable thread was run on top of its own thread. Wait for that thread to be closed.
			getThread().join(millis);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("ResultOfMethodCallIgnored")
	@Override
	public void join(final long millis, final int nanos) throws InterruptedException {
		while (status.get() == Status.NOT_STARTED) {
			MILLISECONDS.sleep(1);
		}

		if (injected) {
			// This stoppable thread was injected into a pre-existing thread. Wait for the stoppable thread to finish,
			// but don't worry about the underlying thread being stopped.
			finished.await((long) (millis + nanos * NANOSECONDS_TO_MILLISECONDS), TimeUnit.MILLISECONDS);
		} else {
			// This stoppable thread was run on top of its own thread. Wait for that thread to be closed.
			getThread().join(millis, nanos);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean stop() {
		final Status originalStatus = status.get();

		if (originalStatus != Status.ALIVE && originalStatus != Status.PAUSED) {
			final Thread t = thread.get();
			final String name = t == null ? "null" : t.getName();
			final String message = "can not stop thread {} when it is in the state {}";

			if (originalStatus == Status.DEAD) {
				// Closing a thread after it dies is probably not the root cause of any errors (if there is an error)
				LOG.warn(THREADS.getMarker(), message, () -> name, originalStatus::name);
			} else {
				// Closing a thread that is NOT_STARTED or DYING is probably indicative of an error
				LOG.error(EXCEPTION.getMarker(), message, () -> name, originalStatus::name);
			}

			return false;
		}

		status.set(Status.DYING);

		if (originalStatus == Status.PAUSED) {
			unblockPausedThread();
		}

		try {
			if (interruptable) {
				interruptClose();
			} else {
				blockingClose();
			}
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}

		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean interrupt() {
		final Thread t = thread.get();
		if (t == null) {
			return false;
		}

		t.interrupt();
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isAlive() {
		return status.get() != Status.DEAD;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Status getStatus() {
		return status.get();
	}

	/**
	 * Indicate that the thread has started.
	 *
	 * @param thread
	 * 		the thread that is being used
	 */
	protected void markAsStarted(final Thread thread) {
		this.thread.set(thread);
		status.set(Status.ALIVE);
		started.countDown();
	}

	/**
	 * An implementation of close that will interrupt the work thread if it doesn't terminate quickly enough.
	 */
	private void interruptClose() throws InterruptedException {
		join(joinWaitMs);

		if (isAlive()) {
			interrupt();
			joinInternal();
		}
	}

	/**
	 * An implementation of close that will block until the work thread terminates.
	 */
	private void blockingClose() throws InterruptedException {
		joinInternal();
		doFinalCycleWork();
	}

	/**
	 * Perform a join with hanging thread detection (if configured).
	 */
	private void joinInternal() throws InterruptedException {
		if (!hangingThreadDuration.isZero()) {
			join(hangingThreadDuration.toMillis());
			if (isAlive()) {
				logHangingThread();
				join();
			}
		} else {
			join();
		}
	}

	/**
	 * Attempt to do some work.
	 *
	 * @throws InterruptedException
	 * 		if the thread running the work is interrupted
	 */
	private void doWork() throws InterruptedException {
		work.run();
	}

	/**
	 * Perform the last cycle of work. Only called if {@link #interruptable} is false.
	 *
	 * @throws InterruptedException
	 * 		if the thread running the work is interrupted
	 */
	private void doFinalCycleWork() throws InterruptedException {
		if (finalCycleWork != null) {
			finalCycleWork.run();
		}
	}

	/**
	 * Write a log message if this thread becomes a hanging thread.
	 */
	private void logHangingThread() {
		hanging = true;

		StringBuilder sb = new StringBuilder();
		sb.append("hanging thread detected: ")
				.append(getName())
				.append(" was requested to stop but is still alive after ")
				.append(hangingThreadDuration)
				.append("ms. Interrupt enabled = ").append(interruptable);
		LOG.error(EXCEPTION.getMarker(), sb);


		sb = new StringBuilder("stack trace for hanging thread ").append(getName()).append(":\n")
				.append(getStackTrace(uninterruptableGetThread()));

		LOG.error(EXCEPTION.getMarker(), sb);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isHanging() {
		return uninterruptableGetThread().isAlive() && hanging;
	}

	/**
	 * Get the name of this thread.
	 */
	public String getName() {
		return configuration.getThreadName();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T getWork() {
		return work;
	}
}
