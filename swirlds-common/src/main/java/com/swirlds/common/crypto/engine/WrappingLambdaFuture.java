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

package com.swirlds.common.crypto.engine;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * A lightweight wrapping future implementation that delegates to the inner future. This implementation uses {@link
 * Supplier} to provide the inner future and the final value to be returned.
 *
 * @param <InnerReturnType>
 * 		the type of object returned by the inner {@link Future}
 * @param <ReturnType>
 * 		the type of object returned by the {@link #get()} methods
 */
public class WrappingLambdaFuture<ReturnType, InnerReturnType> implements Future<ReturnType> {

	private volatile Future<InnerReturnType> innerFuture;

	private Supplier<Future<InnerReturnType>> innerFutureSupplier;
	private Supplier<ReturnType> returnTypeSupplier;

	/**
	 * Internal Use - Default Constructor
	 *
	 * @param innerFutureSupplier
	 * 		the supplier of the inner future
	 * @param returnTypeSupplier
	 * 		the supplier of the ReturnType
	 */
	public WrappingLambdaFuture(final Supplier<Future<InnerReturnType>> innerFutureSupplier,
			final Supplier<ReturnType> returnTypeSupplier) {
		this.innerFutureSupplier = innerFutureSupplier;
		this.returnTypeSupplier = returnTypeSupplier;
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
		initialize();

		return innerFuture.cancel(mayInterruptIfRunning);
	}

	/**
	 * Returns {@code true} if this task was cancelled before it completed normally.
	 *
	 * @return {@code true} if this task was cancelled before it completed
	 */
	@Override
	public boolean isCancelled() {
		initialize();

		return (innerFuture != null) && innerFuture.isCancelled();
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
		initialize();

		return (innerFuture != null) && innerFuture.isDone();
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
	public ReturnType get() throws InterruptedException, ExecutionException {
		initialize();
		innerFuture.get();

		return returnTypeSupplier.get();
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
	public ReturnType get(final long timeout, final TimeUnit unit)
			throws InterruptedException, TimeoutException, ExecutionException {
		initialize();
		innerFuture.get(timeout, unit);

		return returnTypeSupplier.get();
	}


	private synchronized void initialize() {
		if (innerFuture == null) {
			innerFuture = innerFutureSupplier.get();
		}
	}
}
