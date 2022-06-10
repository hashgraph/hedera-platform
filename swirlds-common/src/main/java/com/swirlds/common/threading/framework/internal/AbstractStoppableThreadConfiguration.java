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

package com.swirlds.common.threading.framework.internal;

import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.framework.ThreadSeed;
import com.swirlds.common.threading.framework.TypedStoppableThread;

import java.time.Duration;

import static com.swirlds.common.utility.Units.NANOSECONDS_TO_SECONDS;
import static com.swirlds.common.utility.Units.SECONDS_TO_NANOSECONDS;

/**
 * Boilerplate getters, setters, and configuration for stoppable configuration.
 *
 * @param <C>
 * 		the type of the class extending this class
 * @param <T>
 * 		the type of the object that provides a run method
 */
public abstract class AbstractStoppableThreadConfiguration<
		C extends AbstractStoppableThreadConfiguration<C, T>, T extends InterruptableRunnable>
		extends AbstractThreadConfiguration<C> {

	public static final boolean DEFAULT_INTERRUPTABLE = true;
	public static final int DEFAULT_JOIN_WAIT_MS = 50;
	public static final Duration DEFAULT_HANGING_PERIOD = Duration.ofMinutes(1);

	/**
	 * Can this thread be interrupted?
	 */
	private boolean interruptable = DEFAULT_INTERRUPTABLE;

	/**
	 * If this thread can be interrupted, how long should be waited after a close request before interrupting?
	 */
	private int joinWaitMs = DEFAULT_JOIN_WAIT_MS;

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
	private Duration hangingThreadPeriod = DEFAULT_HANGING_PERIOD;


	protected AbstractStoppableThreadConfiguration() {
		super();
	}

	/**
	 * Copy constructor.
	 *
	 * @param that
	 * 		the configuration to copy.
	 */
	protected AbstractStoppableThreadConfiguration(final AbstractStoppableThreadConfiguration<C, T> that) {
		super(that);

		this.interruptable = that.interruptable;
		this.joinWaitMs = that.joinWaitMs;
		this.work = that.work;
		this.finalCycleWork = that.finalCycleWork;
		this.hangingThreadPeriod = that.hangingThreadPeriod;
		this.minimumPeriod = that.minimumPeriod;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract AbstractStoppableThreadConfiguration<C, T> copy();

	/**
	 * Build a stoppable thread using the current configuration.
	 *
	 * @param start
	 * 		if true then start the thread
	 * @return a stoppable thread
	 */
	protected TypedStoppableThread<T> buildStoppableThread(final boolean start) {
		final TypedStoppableThread<T> thread = new StoppableThreadImpl<>(this);

		if (start) {
			thread.start();
		}

		return thread;
	}

	/**
	 * Build a seed for a stoppable thread.
	 *
	 * @param stoppableThread
	 * 		the stoppable thread to build a seed from
	 * @return an injectable seed
	 */
	protected ThreadSeed buildStoppableThreadSeed(final StoppableThreadImpl<T> stoppableThread) {
		stoppableThread.setInjected();
		return () -> {
			stoppableThread.markAsStarted(Thread.currentThread());
			buildThreadSeed().inject();
		};
	}

	/**
	 * Get the method that will be run after the thread is stopped. Ignored if {@link #isInterruptable()} is true.
	 */
	protected InterruptableRunnable getFinalCycleWork() {
		return finalCycleWork;
	}

	/**
	 * Set the method that will be run after the thread is stopped. Ignored if {@link #isInterruptable()} is true.
	 *
	 * @return this object
	 */
	@SuppressWarnings("unchecked")
	protected C setFinalCycleWork(final InterruptableRunnable finalCycleWork) {
		throwIfImmutable();
		this.finalCycleWork = finalCycleWork;
		return (C) this;
	}

	/**
	 * Get the work that will be run on the thread.
	 */
	protected T getWork() {
		return work;
	}

	/**
	 * Set the work that will be run on the thread.
	 *
	 * @return this object
	 */
	@SuppressWarnings("unchecked")
	protected C setWork(final T work) {
		throwIfImmutable();
		this.work = work;
		return (C) this;
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
	@SuppressWarnings("unchecked")
	public C setInterruptable(final boolean interruptable) {
		throwIfImmutable();
		this.interruptable = interruptable;
		return (C) this;
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
	@SuppressWarnings("unchecked")
	public C setMinimumPeriod(final Duration minimumPeriod) {
		throwIfImmutable();
		this.minimumPeriod = minimumPeriod;
		return (C) this;
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
	@SuppressWarnings("unchecked")
	public C setMaximumRate(final double hz) {
		throwIfImmutable();
		if (hz <= 0) {
			throw new IllegalArgumentException("invalid hertz value " + hz);
		}

		minimumPeriod = Duration.ofNanos((long) ((1 / hz) * SECONDS_TO_NANOSECONDS));

		return (C) this;
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
	@SuppressWarnings("unchecked")
	public C setJoinWaitMs(final int joinWaitMs) {
		throwIfImmutable();
		this.joinWaitMs = joinWaitMs;
		return (C) this;
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
	@SuppressWarnings("unchecked")
	public C setHangingThreadPeriod(final Duration hangingThreadPeriod) {
		throwIfImmutable();
		this.hangingThreadPeriod = hangingThreadPeriod;
		return (C) this;
	}
}