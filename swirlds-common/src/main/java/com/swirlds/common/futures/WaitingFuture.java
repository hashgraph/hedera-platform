/*
 * (c) 2016-2020 Swirlds, Inc.
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

package com.swirlds.common.futures;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A lightweight future implementation that always returns a {@link Void} result. The {@link #get()} methods utilize a
 * blocking implementation based on {@link Object#wait()} and {@link Object#notifyAll()} synchronization.
 * <p>
 * This future implementation may only be cancelled internally and may never be cancelled by the requester. The
 * {@link #cancel()} methods will always return false due to this constraint.
 */
public class WaitingFuture<T> implements Future<T> {

	private volatile boolean done;
	private volatile boolean cancelled;
	private volatile T value;
	private final CountDownLatch latch;

	/**
	 * Internal Use - Default Constructor
	 */
	public WaitingFuture() {
		this.latch = new CountDownLatch(1);
		this.done = false;
		this.cancelled = false;
	}

	/**
	 * Internal Use - Instantly Completed Future
	 *
	 * @param value
	 * 		the computed result
	 */
	public WaitingFuture(final T value) {
		this();
		this.value = value;
		this.done = true;
		this.latch.countDown();
	}

	/**
	 * Attempts to cancel execution of this task. This attempt will fail if the task has already completed, has already
	 * been cancelled, or could not be cancelled for some other reason. If successful, and this task has not started
	 * when {@code cancel} is called, this task should never run. If the task has already started, then the
	 * {@code mayInterruptIfRunning} parameter determines whether the thread executing this task should be interrupted
	 * in an attempt to stop the task.
	 *
	 * <p>
	 * After this method returns, subsequent calls to {@link #isDone} will always return {@code true}. Subsequent calls
	 * to {@link #isCancelled} will always return {@code true} if this method returned {@code true}.
	 *
	 * @param mayInterruptIfRunning
	 *        {@code true} if the thread executing this task should be interrupted; otherwise,
	 * 		in-progress tasks are allowed to complete
	 * @return {@code false} if the task could not be cancelled, typically because it has already completed normally;
	 *        {@code true} otherwise
	 */
	@Override
	public boolean cancel(final boolean mayInterruptIfRunning) {
		cancel();
		return !isDone();
	}

	/**
	 * Returns {@code true} if this task was cancelled before it completed normally.
	 *
	 * @return {@code true} if this task was cancelled before it completed
	 */
	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	/**
	 * Returns {@code true} if this task completed.
	 * <p>
	 * Completion may be due to normal termination, an exception, or cancellation -- in all of these cases, this method
	 * will return {@code true}.
	 *
	 * @return {@code true} if this task completed
	 */
	@Override
	public boolean isDone() {
		return done;
	}

	/**
	 * Waits if necessary for the computation to complete, and then retrieves its result.
	 *
	 * @return the computed result
	 * @throws CancellationException
	 * 		if the computation was cancelled
	 * @throws InterruptedException
	 * 		if the current thread was interrupted while waiting
	 */
	@Override
	public T get() throws InterruptedException {
		this.latch.await();

		if (cancelled) {
			throw new CancellationException();
		}

		return value;
	}

	/**
	 * Waits if necessary for at most the given time for the computation to complete, and then retrieves its result, if
	 * available.
	 *
	 * @param timeout
	 * 		the maximum time to wait
	 * @param unit
	 * 		the time unit of the timeout argument
	 * @return the computed result
	 * @throws CancellationException
	 * 		if the computation was cancelled
	 * @throws InterruptedException
	 * 		if the current thread was interrupted while waiting
	 */
	@Override
	public T get(final long timeout, final TimeUnit unit)
			throws InterruptedException {
		this.latch.await(timeout, unit);

		if (cancelled) {
			throw new CancellationException();
		}

		return value;
	}

	/**
	 * Internal use only helper method for signaling the completion of the future.
	 *
	 * @param value
	 * 		the computed result
	 */
	public void done(final T value) {
		this.value = value;
		this.done = true;
		this.latch.countDown();
	}

	/**
	 * Internal use only helper method for signaling the cancellation of the future.
	 */
	public void cancel() {
		this.cancelled = true;
		this.done = false;
		this.latch.countDown();
	}

}
