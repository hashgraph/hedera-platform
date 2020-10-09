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

package com.swirlds.platform;

import com.swirlds.common.NodeId;
import com.swirlds.common.threading.StandardThreadFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;

/**
 * Implements the concept of a thread that can be gracefully stopped. Once stopped this instance can no longer be used
 * and must be recreated.
 *
 * <pre>
 * Each StoppableThread object contains a Thread object which can be stopped.
 *
 * Usage:
 * Initialize a StoppableThread object by passing a name, a Runnable object which might
 * throw InterruptedException, and the platform's nodeId;
 * Users can optionally register a Thread.UncaughtExceptionHandler lambda to deal with any Throwable
 * thrown by the inner Runnable instance.
 * Call stop() method to stop the thread.
 * </pre>
 */
class StoppableThread {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/**
	 * the constant amount of time in milliseconds to wait after setting {@link #running} to false before interrupting
	 * the thread
	 */
	private static final int JOIN_WAIT_MS = 1;

	/** the name of the thread */
	private final String name;

	/** the underlying method to be executed repeatedly until stopped */
	private final InterruptableRunnable runnable;

	/** the underlying thread **/
	private final Thread thread;

	/** true if this thread has been started; otherwise false if this has not started or was stopped */
	private volatile boolean running;

	/**
	 * Constructs a new instance with the provided name and {@link InterruptableRunnable} instance.
	 *
	 * @param name
	 * 		the name of thread
	 * @param runnable
	 * 		the underlying method to be executed until stopped
	 * @param nodeId
	 * 		the id of the platform to which this instance belongs
	 */
	public StoppableThread(final String name, final InterruptableRunnable runnable, final NodeId nodeId) {
		// use default UncaughtExceptionHandler
		this(name, runnable, nodeId, true, null);
	}

	/**
	 * Constructs a new instance with the provided name and {@link InterruptableRunnable} instance.
	 *
	 * @param name
	 * 		the name of thread
	 * @param runnable
	 * 		the underlying method to be executed until stopped
	 * @param nodeId
	 * 		the id of the platform to which this instance belongs
	 * @param daemon
	 * 		if true then the underlying thread will be spawned as a daemon thread
	 */
	public StoppableThread(final String name, final InterruptableRunnable runnable,
			final NodeId nodeId, final boolean daemon) {
		this(name, runnable, nodeId, daemon, null);
	}

	/**
	 * Constructs a new instance with the provided name and {@link InterruptableRunnable} instance.
	 *
	 * @param name
	 * 		the name of thread
	 * @param runnable
	 * 		the underlying method to be executed until stopped
	 * @param nodeId
	 * 		the id of the platform to which this instance belongs
	 * @param exceptionHandler
	 * 		an optional exception handler to be notified for each uncaught exception, if null is passed then a default
	 * 		handler will be used
	 */
	public StoppableThread(final String name, final InterruptableRunnable runnable,
			final NodeId nodeId, final Thread.UncaughtExceptionHandler exceptionHandler) {
		this(name, runnable, nodeId, true, exceptionHandler);
	}

	/**
	 * Constructs a new instance with the provided name and {@link InterruptableRunnable} instance.
	 *
	 * @param name
	 * 		the name of thread
	 * @param runnable
	 * 		the underlying method to be executed until stopped
	 * @param nodeId
	 * 		the id of the platform to which this instance belongs
	 * @param daemon
	 * 		if true then the underlying thread will be spawned as a daemon thread
	 * @param exceptionHandler
	 * 		an optional exception handler to be notified for each uncaught exception, if null is passed then a default
	 * 		handler will be used
	 */
	public StoppableThread(final String name, final InterruptableRunnable runnable,
			final NodeId nodeId, final boolean daemon, final Thread.UncaughtExceptionHandler exceptionHandler) {
		this.name = name;
		this.runnable = runnable;

		thread = StandardThreadFactory.newThread(name, this::run, nodeId, Settings.threadPriorityNonSync, daemon);

		thread.setUncaughtExceptionHandler(Objects.requireNonNullElseGet(exceptionHandler,
				() -> (t, e) -> log.error(EXCEPTION.getMarker(),
						"Unhandled Exception [ name = {} ]", name, e)));
	}

	/**
	 * Starts executing the provided {@link InterruptableRunnable} until stopped.
	 */
	public synchronized void start() {
		running = true;
		thread.start();
	}

	/**
	 * Returns true if the underlying thread is still alive. Delegates to the {@link Thread#isAlive()} method.
	 *
	 * @return {@code true} if the underlying thread is alive; {@code false} otherwise.
	 */
	public boolean isAlive() {
		return thread.isAlive();
	}

	/**
	 * Attempts to gracefully stop the underlying thread and then forcefully interrupts if it takes longer than {@link
	 * #JOIN_WAIT_MS} milliseconds to terminate.
	 */
	public synchronized void stop() {
		running = false;

		try {
			thread.join(JOIN_WAIT_MS);

			if (thread.isAlive()) {
				thread.interrupt();
				thread.join();
			}
		} catch (InterruptedException ex) {
			log.info(RECONNECT.getMarker(), "Stop Interrupted [ name = {} ]", name);
			Thread.currentThread().interrupt();
			return;
		}

		log.info(RECONNECT.getMarker(), "Successfully Stopped [ name = {} ]", name);
	}

	/**
	 * Implements the internal thread loop which imposes a 1 nanosecond wait between method invocations to ensure we can
	 * reach a checkpoint.
	 */
	private void run() {
		while (running) {
			try {
				runnable.run();

				// Sleep to ensure we are able to reach a checkpoint
				Thread.sleep(0, 1);
			} catch (InterruptedException e) {
				log.info("Thread Interrupted [ name = {} ]", name);
				running = false;
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

}
