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

package com.swirlds.platform.state;

import com.swirlds.common.AutoCloseableWrapper;
import com.swirlds.common.threading.StoppableThread;
import com.swirlds.common.threading.StoppableThreadConfiguration;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.swirlds.common.merkle.hash.MerkleHashChecker.checkHashAndLog;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

/**
 * A debug utility that checks the hashes of states in a background thread.
 */
public class BackgroundHashChecker {

	private final Supplier<AutoCloseableWrapper<SignedState>> stateSupplier;
	private SignedState previousState;

	private final Consumer<SignedState> passedValidationCallback;
	private final Consumer<SignedState> failedValidationCallback;

	private final StoppableThread thread;

	private static final int WAIT_FOR_NEW_STATE_PERIOD_MS = 100;

	/**
	 * Create a new background hash checker. This constructor starts a background thread.
	 *
	 * @param stateSupplier
	 * 		a method that is used to get signed states
	 */
	public BackgroundHashChecker(final Supplier<AutoCloseableWrapper<SignedState>> stateSupplier) {
		this(stateSupplier, null, null);
	}

	/**
	 * Create a new background hash checker. This constructor starts a background thread.
	 *
	 * @param stateSupplier
	 * 		a method that is used to get signed states
	 * @param passedValidationCallback
	 * 		this method his called with each signed state that passes validation. State passed to this callback
	 * 		should not be used once the callback returns. A null callback is permitted.
	 * @param failedValidationCallback
	 * 		this method his called with each signed state that fails validation. State passed to this callback
	 * 		should not be used once the callback returns. A null callback is permitted.
	 */
	public BackgroundHashChecker(
			final Supplier<AutoCloseableWrapper<SignedState>> stateSupplier,
			final Consumer<SignedState> passedValidationCallback,
			final Consumer<SignedState> failedValidationCallback) {

		this.stateSupplier = stateSupplier;
		this.passedValidationCallback = passedValidationCallback;
		this.failedValidationCallback = failedValidationCallback;

		this.thread = new StoppableThreadConfiguration<>()
				.setComponent(PLATFORM_THREAD_POOL_NAME)
				.setThreadName("background-hash-checker")
				.setPriority(Thread.MIN_PRIORITY)
				.setWork(this::doWork)
				.build();
		this.thread.start();
	}

	/**
	 * Stop the background thread. Once stopped can not be restarted.
	 */
	public void stop() {
		thread.stop();
	}

	/**
	 * Join the background thread.
	 */
	public void join() throws InterruptedException {
		thread.join();
	}

	/**
	 * Join the background thread with a given timeout.
	 *
	 * @param millis
	 * 		the amount of time to wait to join
	 */
	public void join(final long millis) throws InterruptedException {
		thread.join(millis);
	}

	/**
	 * Check if the background thread is alive.
	 */
	public boolean isAlive() {
		return thread.isAlive();
	}

	private void doWork() throws InterruptedException {
		try (final AutoCloseableWrapper<SignedState> wrapper = stateSupplier.get()) {

			final SignedState signedState = wrapper.get();
			if (signedState == previousState || signedState == null) {
				Thread.sleep(WAIT_FOR_NEW_STATE_PERIOD_MS);
				return;
			}
			previousState = signedState;

			final State state = wrapper.get().getState();
			final boolean passed = checkHashAndLog(
					state,
					"background state hash check, round = " + state.getPlatformState().getRound(),
					10);

			if (passed) {
				if (passedValidationCallback != null) {
					passedValidationCallback.accept(signedState);
				}
			} else {
				if (failedValidationCallback != null) {
					failedValidationCallback.accept(signedState);
				}
			}
		}
	}
}
