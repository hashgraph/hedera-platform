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

package com.swirlds.common.threading.framework;

/**
 * Describes a thread or a collection of threads that can be started, stopped, paused, resumed, and joined.
 */
public interface Stoppable {

	/**
	 * <p>
	 * Start the thread(s).
	 * </p>
	 *
	 * @return true if operation was successful, or false if the thread is in the incorrect state to be started
	 */
	boolean start();

	/**
	 * <p>
	 * Attempt to gracefully stop the thread.
	 * </p>
	 *
	 * <p>
	 * Should not be called before the thread has been started, or in the case of a seed before
	 * the seed has been built.
	 * </p>
	 *
	 * @return true if operation was successful, or false if the thread is in the incorrect state to be stopped
	 */
	boolean stop();

	/**
	 * <p>
	 * Causes the thread to finish its current work and to then block. Thread remains blocked
	 * until {@link #resume()} is called.
	 * </p>
	 *
	 * <p>
	 * This method must not be called unless the thread is alive and {@link #resume()} has already
	 * been called and has returned.
	 * </p>
	 *
	 * @return true if operation was successful, or false if the thread is in the incorrect state to be paused
	 */
	boolean pause();

	/**
	 * <p>
	 * This method can be called to resume work on the thread after a {@link #pause()} call.
	 * </p>
	 *
	 * <p>
	 * This method should only be called if the thread is paused.
	 * </p>
	 *
	 * @return true if operation was successful, or false if the thread is in the incorrect state to be resumed
	 */
	boolean resume();

	/**
	 * Joins the thread. Equivalent to {@link Thread#join()}. If this thread was injected, join
	 * waits for the stoppable thread to be finished, but does not wait for the thread itself to die.
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	void join() throws InterruptedException;

	/**
	 * Join the thread. Equivalent to {@link Thread#join(long)}.  If this thread was injected, join
	 * waits for the stoppable thread to be finished, but does not wait for the thread itself to die.
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	void join(long millis) throws InterruptedException;

	/**
	 * Join the thread. Equivalent to {@link Thread#join(long, int)}. If this thread was injected, join
	 * waits for the stoppable thread to be finished, but does not wait for the thread itself to die.
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	void join(long millis, int nanos) throws InterruptedException;
}
