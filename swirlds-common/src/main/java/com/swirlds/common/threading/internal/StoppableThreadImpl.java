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

package com.swirlds.common.threading.internal;

import com.swirlds.common.threading.InterruptableRunnable;
import com.swirlds.common.threading.StoppableThreadConfiguration;
import com.swirlds.common.threading.TypedStoppableThread;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;

import static com.swirlds.common.utility.CompareTo.isGreaterThan;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Implements the concept of a thread that can be gracefully stopped. Once stopped this instance can no longer be used
 * and must be recreated.
 *
 * @param <T>
 * 		the type of instance that will do work
 */
public class StoppableThreadImpl<T extends InterruptableRunnable> implements TypedStoppableThread<T> {

	private static final Logger log = LogManager.getLogger();

	/**
	 * Tracks if the thread should currently be active.
	 */
	private volatile boolean alive;

	/**
	 * If true then interrupt the thread when it is closed.
	 */
	private final boolean interruptable;

	/**
	 * If true then this thread can be paused. Threads to not need to be paused can disable this for improved
	 * performance.
	 */
	private final boolean pausable;

	/**
	 * The amount of time in milliseconds to wait after setting {@link #alive} to false before interrupting
	 * the thread if {@link #interruptable} is true.
	 */
	private final int joinWaitMs;

	/**
	 * Used to enforce pauses. Null if pauses are disabled.
	 */
	private final Semaphore pauseSemaphore;

	/**
	 * The minimum amount of time that a cycle is permitted to take. If a cycle completes in less time then
	 * sleep until the minimum period has been met.
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
	private final Thread thread;

	/**
	 * If a thread is requested to stop but does not it is considered to be hanging after this
	 * period. If 0 then thread is never considered to be hanging.
	 */
	private final Duration hangingThreadDuration;

	private volatile boolean hanging;

	/**
	 * Create a new stoppable thread.
	 */
	public StoppableThreadImpl(final StoppableThreadConfiguration<T> configuration) {
		alive = true;
		interruptable = configuration.isInterruptable();
		pausable = configuration.isPausable();
		pauseSemaphore = pausable ? new Semaphore(1, true) : null;
		joinWaitMs = configuration.getJoinWaitMs();
		hangingThreadDuration = configuration.getHangingThreadPeriod();

		work = configuration.getWork();
		finalCycleWork = configuration.getFinalCycleWork();

		minimumPeriod = configuration.getMinimumPeriod();

		thread = configuration.getThreadConfiguration().setRunnable(this::run).build();
	}

	/**
	 * The method to execute on the {@link #thread} object.
	 */
	private void run() {
		while (alive) {
			try {
				if (pausable) {
					pauseSemaphore.acquire();
				}
				enforceMinimumPeriod();
				doWork();
				if (pausable) {
					pauseSemaphore.release();
				}
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
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
	public void start() {
		thread.start();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void pause() throws InterruptedException {
		if (!pausable) {
			throw new IllegalStateException("this thread is not pausable");
		}
		pauseSemaphore.acquire();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void resume() {
		if (!pausable) {
			throw new IllegalStateException("this thread is not pausable");
		}
		pauseSemaphore.release();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void join() throws InterruptedException {
		thread.join();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void join(final long millis) throws InterruptedException {
		thread.join(millis);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void join(final long millis, final int nanos) throws InterruptedException {
		thread.join(millis, nanos);
	}

	/**
	 * <p>
	 * Attempt to gracefully stop the thread. If thread does not terminate in a timely manner then it is interrupted.
	 * </p>
	 *
	 * <p>
	 * Do not call this method inside the internal thread (i.e. the one calling the runnable over and over). This
	 * method joins on that thread, and will deadlock if it attempts to join on itself.
	 * </p>
	 */
	@Override
	public void stop() {
		try {
			if (interruptable) {
				interruptClose();
			} else {
				blockingClose();
			}
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void interrupt() {
		thread.interrupt();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isAlive() {
		return thread.isAlive();
	}

	/**
	 * An implementation of close that will interrupt the work thread if it doesn't terminate quickly enough.
	 */
	private void interruptClose() throws InterruptedException {
		if (alive) {
			alive = false;

			thread.join(joinWaitMs);
			thread.interrupt();
			joinInternal();
		}
	}

	/**
	 * An implementation of close that will block until the work thread terminates.
	 */
	private void blockingClose() throws InterruptedException {
		if (alive) {
			alive = false;
			joinInternal();
			doFinalCycleWork();
		}
	}

	/**
	 * Perform a join with hanging thread detection (if configured).
	 */
	private void joinInternal() throws InterruptedException {
		if (!hangingThreadDuration.isZero()) {
			thread.join(hangingThreadDuration.toMillis());
			if (thread.isAlive()) {
				logHangingThread();
				thread.join();
			}
		} else {
			thread.join();
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
				.append(thread.getName())
				.append(" was requested to stop but is still alive after ")
				.append(hangingThreadDuration)
				.append("ms. Interrupt enabled = ").append(interruptable);
		log.error(EXCEPTION.getMarker(), sb);


		sb = new StringBuilder("stack trace for hanging thread ").append(thread.getName()).append(":\n");
		for (final StackTraceElement element : thread.getStackTrace()) {
			sb.append("   ").append(element).append("\n");
		}

		log.error(EXCEPTION.getMarker(), sb);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isHanging() {
		return thread.isAlive() && hanging;
	}

	/**
	 * Get the name of this thread.
	 */
	public String getName() {
		return thread.getName();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T getWork() {
		return work;
	}
}
