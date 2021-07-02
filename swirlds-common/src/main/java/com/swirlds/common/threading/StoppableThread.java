/*
 * (c) 2016-2021 Swirlds, Inc.
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
/*li
 * (c) 2016-2021 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

package com.swirlds.common.threading;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.concurrent.Semaphore;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * Implements the concept of a thread that can be gracefully stopped. Once stopped this instance can no longer be used
 * and must be recreated.
 */
public class StoppableThread {

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
	 * The work to perform on the thread. Called over and over.
	 */
	private final InterruptableRunnable work;

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
	 * Intentionally package private.
	 */
	StoppableThread(StoppableThreadConfiguration configuration) {
		alive = true;
		interruptable = configuration.isInterruptable();
		pausable = configuration.isPausable();
		pauseSemaphore = pausable ? new Semaphore(1, true) : null;
		joinWaitMs = configuration.getJoinWaitMs();
		hangingThreadDuration = configuration.getHangingThreadPeriod();

		work = configuration.getWork();
		finalCycleWork = configuration.getFinalCycleWork();

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
				doWork();
				if (pausable) {
					pauseSemaphore.release();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Start the thread.
	 */
	public void start() {
		thread.start();
	}

	/**
	 * Causes the thread to finish the current call to {@link #doWork()} and to then block. Thread remains blocked
	 * until {@link #resume()} is called.
	 */
	public void pause() throws InterruptedException {
		if (!pausable) {
			throw new IllegalStateException("this thread is not pausable");
		}
		pauseSemaphore.acquire();
	}

	/**
	 * This method can be called to resume work on the thread after a {@link #pause()} call.
	 */
	public void resume() {
		if (!pausable) {
			throw new IllegalStateException("this thread is not pausable");
		}
		pauseSemaphore.release();
	}

	/**
	 * Join the thread. Equivalent to {@link Thread#join()}.
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	public void join() throws InterruptedException {
		thread.join();
	}

	/**
	 * Join the thread. Equivalent to {@link Thread#join(long)}.
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	public void join(long millis) throws InterruptedException {
		thread.join(millis);
	}

	/**
	 * Join the thread. Equivalent to {@link Thread#join(long, int)}.
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	public void join(long millis, int nanos) throws InterruptedException {
		thread.join(millis, nanos);
	}

	/**
	 * Attempt to gracefully stop the thread. If thread does not terminate in a timely manner then it is interrupted.
	 */
	public void stop() {
		try {
			if (interruptable) {
				interruptClose();
			} else {
				blockingClose();
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Interrupt this thread.
	 */
	public void interrupt() {
		thread.interrupt();
	}

	/**
	 * Check if this thread is currently alive.
	 */
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
	 * Check if this thread is currently in a hanging state.
	 *
	 * @return true if this thread is currently in a hanging state
	 */
	public boolean isHanging() {
		return thread.isAlive() && hanging;
	}

	/**
	 * Get the name of this thread.
	 */
	public String getName() {
		return thread.getName();
	}
}
