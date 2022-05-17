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

/**
 * A thread or a class with a thread that can be stopped.
 */
public interface StoppableThread {

	/**
	 * The name of this thread.
	 *
	 * @return the name
	 */
	String getName();

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
	void stop();

	/**
	 * Joins the thread. Equivalent to {@link Thread#join()}
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	void join() throws InterruptedException;

	/**
	 * Join the thread. Equivalent to {@link Thread#join(long)}.
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	void join(long millis) throws InterruptedException;

	/**
	 * Join the thread. Equivalent to {@link Thread#join(long, int)}.
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	void join(long millis, int nanos) throws InterruptedException;

	/**
	 * Start the thread.
	 */
	void start();

	/**
	 * Causes the thread to finish its current work and to then block. Thread remains blocked
	 * until {@link #resume()} is called.
	 */
	void pause() throws InterruptedException;

	/**
	 * This method can be called to resume work on the thread after a {@link #pause()} call.
	 */
	void resume();

	/**
	 * Interrupt this thread.
	 */
	void interrupt();

	/**
	 * Check if this thread is currently alive.
	 *
	 * @return true if this thread is alive
	 */
	boolean isAlive();

	/**
	 * Check if this thread is currently in a hanging state.
	 *
	 * @return true if this thread is currently in a hanging state
	 */
	boolean isHanging();
}
