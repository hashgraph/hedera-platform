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

package com.swirlds.common.merkle.hash;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This future object is used by {@link MerkleHashBuilder#digestTreeAsync(MerkleNode)} to return a hash to the user.
 */
public class FutureMerkleHash implements Future<Hash> {

	private volatile Hash hash;
	private volatile Throwable exception;
	private final CountDownLatch latch;

	/**
	 * Create a future that will eventually have the hash specified.
	 */
	public FutureMerkleHash() {
		latch = new CountDownLatch(1);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean cancel(final boolean mayInterruptIfRunning) {
		throw new UnsupportedOperationException();
	}

	/**
	 * This method is used to register that an exception was encountered while hashing the tree.
	 */
	public synchronized void cancelWithException(final Throwable exception) {
		if (exception != null) {
			// Only the first exception gets rethrown
			this.exception = exception;
			latch.countDown();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isCancelled() {
		return exception != null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isDone() {
		return hash != null;
	}

	/**
	 * If there were any exceptions encountered during hashing, rethrow that exception.
	 */
	private void rethrowException() throws ExecutionException {
		if (exception != null) {
			throw new ExecutionException(exception);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Hash get() throws InterruptedException, ExecutionException {
		latch.await();
		rethrowException();
		return hash;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Hash get(final long timeout, final TimeUnit unit)
			throws InterruptedException, TimeoutException, ExecutionException {
		if (!latch.await(timeout, unit)) {
			throw new TimeoutException();
		}
		rethrowException();
		return hash;
	}

	/**
	 * Set the hash for the tree.
	 *
	 * @param hash
	 * 		the hash
	 */
	public synchronized void set(Hash hash) {
		if (exception == null) {
			this.hash = hash;
			latch.countDown();
		}
	}
}
