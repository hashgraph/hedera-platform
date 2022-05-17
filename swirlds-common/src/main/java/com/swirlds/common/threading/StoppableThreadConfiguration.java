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

import com.swirlds.common.threading.internal.StoppableThreadImpl;

import java.time.Duration;

import static com.swirlds.common.Units.NANOSECONDS_TO_SECONDS;
import static com.swirlds.common.Units.SECONDS_TO_NANOSECONDS;

/**
 * An object responsible for configuring and constructing {@link StoppableThread}s.
 *
 * @param <T>
 * 		the type of instance that will do work
 */
public class StoppableThreadConfiguration<T extends InterruptableRunnable> {

	/**
	 * Can this thread be interrupted?
	 */
	private boolean interruptable;

	/**
	 * If this thread can be interrupted, how long should be waited after a close request before interrupting?
	 */
	private int joinWaitMs;

	/**
	 * Can this thread be paused?
	 */
	private boolean pausable;

	/**
	 * If not null, then this is the minimum amount of time that a cycle is allowed to take.
	 */
	private Duration minimumPeriod;

	/**
	 * The work to be performed on the thread. Called over and over until the thread is stopped.
	 */
	private T work;

	/**
	 * A method that is called right before stopping the thread. Ignored if thread is interruptable.
	 */
	private InterruptableRunnable finalCycleWork;

	/**
	 * If a thread is requested to stop but does not it is considered to be a hanging thread after this
	 * period. If 0 then thread is never considered to be a hanging thread.
	 */
	private Duration hangingThreadPeriod;

	/**
	 * Used to configure the underlying thread.
	 */
	private final ThreadConfiguration threadConfiguration;

	public static final boolean DEFAULT_INTERRUPTABLE = true;
	public static final boolean DEFAULT_PAUSABLE = false;
	public static final int DEFAULT_JOIN_WAIT_MS = 50;
	public static final Duration DEFAULT_HANGING_PERIOD = Duration.ofMinutes(1);

	/**
	 * Build a new stoppable thread configuration with default values.
	 */
	public StoppableThreadConfiguration() {
		threadConfiguration = new ThreadConfiguration();

		interruptable = DEFAULT_INTERRUPTABLE;
		pausable = DEFAULT_PAUSABLE;
		joinWaitMs = DEFAULT_JOIN_WAIT_MS;
		hangingThreadPeriod = DEFAULT_HANGING_PERIOD;
	}

	/**
	 * Build a new thread. Do not start it automatically.
	 *
	 * @return a stoppable thread built using this configuration
	 */
	public TypedStoppableThread<T> build() {
		return build(false);
	}

	/**
	 * Build a new thread.
	 *
	 * @param start
	 * 		if true then start the thread before returning it
	 * @return a stoppable thread built using this configuration
	 */
	public TypedStoppableThread<T> build(final boolean start) {
		final TypedStoppableThread<T> thread = new StoppableThreadImpl<>(this);

		if (start) {
			thread.start();
		}

		return thread;
	}

	/**
	 * Get the method that will be run after the thread is stopped. Ignored if {@link #isInterruptable()} is true.
	 */
	public InterruptableRunnable getFinalCycleWork() {
		return finalCycleWork;
	}

	/**
	 * Set the method that will be run after the thread is stopped. Ignored if {@link #isInterruptable()} is true.
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setFinalCycleWork(final InterruptableRunnable finalCycleWork) {
		this.finalCycleWork = finalCycleWork;
		return this;
	}

	/**
	 * Get the work that will be run on the thread.
	 */
	public T getWork() {
		return work;
	}

	/**
	 * Set the work that will be run on the thread.
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setWork(final T work) {
		this.work = work;
		return this;
	}

	/**
	 * Should threads created by this object interrupt if close takes too long?
	 */
	public boolean isInterruptable() {
		return interruptable;
	}

	/**
	 * Set if threads created by this object interrupt if close takes too long.
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setInterruptable(final boolean interruptable) {
		this.interruptable = interruptable;
		return this;
	}

	/**
	 * Can threads created by this object be paused?
	 */
	public boolean isPausable() {
		return pausable;
	}

	/**
	 * Set if threads created by this object can be paused.
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setPausable(final boolean pausable) {
		this.pausable = pausable;
		return this;
	}

	/**
	 * Get the minimum amount of time that a single cycle is allowed to take, or null if no such minimum is defined.
	 *
	 * @return the minimum cycle time
	 */
	public Duration getMinimumPeriod() {
		return minimumPeriod;
	}

	/**
	 * Set the minimum amount of time that a single cycle is allowed to take, or null if no such minimum is defined.
	 *
	 * @param minimumPeriod
	 * 		the minimum cycle time
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setMinimumPeriod(final Duration minimumPeriod) {
		this.minimumPeriod = minimumPeriod;
		return this;
	}

	/**
	 * Get the maximum rate in cycles per second that this operation is permitted to run at.
	 *
	 * @return the maximum rate
	 */
	public double getMaximumRate() {
		if (minimumPeriod == null) {
			return -1;
		}

		return 1 / (minimumPeriod.toNanos() * NANOSECONDS_TO_SECONDS);
	}

	/**
	 * Set the maximum rate in cycles per second that this operation is permitted to run at.
	 *
	 * @param hz
	 * 		the rate, in hertz
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setMaximumRate(final double hz) {
		if (hz <= 0) {
			throw new IllegalArgumentException("invalid hertz value " + hz);
		}

		minimumPeriod = Duration.ofNanos((long) ((1 / hz) * SECONDS_TO_NANOSECONDS));

		return this;
	}

	/**
	 * Get the amount of time that this object will wait after being closed before interrupting.
	 */
	public int getJoinWaitMs() {
		return joinWaitMs;
	}

	/**
	 * Set the amount of time that this object will wait after being closed before interrupting.
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setJoinWaitMs(final int joinWaitMs) {
		this.joinWaitMs = joinWaitMs;
		return this;
	}

	/**
	 * Get the the thread group that new threads will be created in.
	 */
	public ThreadGroup getThreadGroup() {
		return threadConfiguration.getThreadGroup();
	}

	/**
	 * Set the the thread group that new threads will be created in.
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setThreadGroup(final ThreadGroup threadGroup) {
		threadConfiguration.setThreadGroup(threadGroup);
		return this;
	}

	/**
	 * Get the daemon behavior of new threads.
	 */
	public boolean isDaemon() {
		return threadConfiguration.isDaemon();
	}

	/**
	 * Set the daemon behavior of new threads.
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setDaemon(final boolean daemon) {
		threadConfiguration.setDaemon(daemon);
		return this;
	}

	/**
	 * Get the priority of new threads.
	 */
	public int getPriority() {
		return threadConfiguration.getPriority();
	}

	/**
	 * Set the priority of new threads.
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setPriority(final int priority) {
		threadConfiguration.setPriority(priority);
		return this;
	}

	/**
	 * Get the class loader for new threads.
	 */
	public ClassLoader getContextClassLoader() {
		return threadConfiguration.getContextClassLoader();
	}

	/**
	 * Set the class loader for new threads.
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setContextClassLoader(final ClassLoader contextClassLoader) {
		threadConfiguration.setContextClassLoader(contextClassLoader);
		return this;
	}

	/**
	 * Get the exception handler for new threads.
	 */
	public Thread.UncaughtExceptionHandler getExceptionHandler() {
		return threadConfiguration.getExceptionHandler();
	}

	/**
	 * Set the exception handler for new threads.
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setExceptionHandler(final Thread.UncaughtExceptionHandler exceptionHandler) {
		threadConfiguration.setExceptionHandler(exceptionHandler);
		return this;
	}

	/**
	 * Get the node ID that will run threads created by this object.
	 */
	public long getNodeId() {
		return threadConfiguration.getNodeId();
	}

	/**
	 * Set the node ID. Node IDs less than 0 are interpreted as "no node ID".
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setNodeId(final long nodeId) {
		threadConfiguration.setNodeId(nodeId);
		return this;
	}

	/**
	 * Get the name of the component that new threads will be associated with.
	 */
	public String getComponent() {
		return threadConfiguration.getComponent();
	}

	/**
	 * Set the name of the component that new threads will be associated with.
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setComponent(final String component) {
		threadConfiguration.setComponent(component);
		return this;
	}

	/**
	 * Get the name for created threads.
	 */
	public String getThreadName() {
		return threadConfiguration.getThreadName();
	}

	/**
	 * Set the name for created threads.
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setThreadName(final String threadName) {
		threadConfiguration.setThreadName(threadName);
		return this;
	}

	/**
	 * Set the node ID of the other node (if created threads will be dealing with a task related to a specific node).
	 */
	public long getOtherNodeId() {
		return threadConfiguration.getOtherNodeId();
	}

	/**
	 * Get the node ID of the other node (if created threads will be dealing with a task related to a specific node).
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setOtherNodeId(final long otherNodeId) {
		threadConfiguration.setOtherNodeId(otherNodeId);
		return this;
	}

	/**
	 * Get the period of time that must elapse after a stop request before this
	 * thread is considered to be a hanging thread. A value of 0 means that the thread will never be considered
	 * to be a hanging thread.
	 */
	public Duration getHangingThreadPeriod() {
		return hangingThreadPeriod;
	}

	/**
	 * Set the period of time that must elapse after a stop request before this
	 * thread is considered to be a hanging thread. A value of 0 means that the thread will never be considered
	 * to be a hanging thread.
	 *
	 * @return this object
	 */
	public StoppableThreadConfiguration<T> setHangingThreadPeriod(final Duration hangingThreadPeriod) {
		this.hangingThreadPeriod = hangingThreadPeriod;
		return this;
	}

	/**
	 * Intentionally package private. Get the underlying thread configuration object.
	 */
	public ThreadConfiguration getThreadConfiguration() {
		return threadConfiguration;
	}
}
