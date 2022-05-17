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

package com.swirlds.common.stream;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.threading.QueueThread;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * An object stream that takes all of its items and puts them into another object stream in an asynchronous thread.
 *
 * @param <T>
 * 		type of the objects
 */
public class QueueThreadObjectStream<T extends RunningHashable> implements LinkedObjectStream<T> {

	/**
	 * use this for all logging, as controlled by the optional data/log4j2.xml file
	 */
	private static final Logger log = LogManager.getLogger();

	/**
	 * the next stream in the flow
	 */
	private final LinkedObjectStream<T> nextStream;

	private final QueueThread<T> queueThread;

	/**
	 * This constructor is intentionally package private. All instances of this class should be created via
	 * {@link QueueThreadObjectStreamConfiguration#build()}.
	 *
	 * @param configuration
	 * 		the configuration object
	 */
	QueueThreadObjectStream(final QueueThreadObjectStreamConfiguration<T> configuration) {
		this.nextStream = configuration.getForwardTo();
		queueThread = configuration.getQueueThreadConfiguration().setHandler(this::handle).build();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setRunningHash(final Hash hash) {
		nextStream.setRunningHash(hash);
	}

	/**
	 * Handle the next item in the queue.
	 */
	private final void handle(final T item) {
		nextStream.addObject(item);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addObject(final T t) {
		try {
			queueThread.put(t);
		} catch (InterruptedException e) {
			log.error(EXCEPTION.getMarker(), "interrupted while attempting to add object to stream");
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		queueThread.clear();
		nextStream.clear();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		queueThread.stop();
		nextStream.close();
	}

	/**
	 * Start the thread.
	 */
	public void start() {
		queueThread.start();
	}

	/**
	 * Causes the thread to finish the current call to doWork() and to then block. Thread remains blocked
	 * until {@link #resume()} is called.
	 */
	public void pause() throws InterruptedException {
		queueThread.pause();
	}

	/**
	 * This method can be called to resume work on the thread after a {@link #pause()} call.
	 */
	public void resume() {
		queueThread.resume();
	}

	/**
	 * Join the thread. Equivalent to {@link Thread#join()}.
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	public void join() throws InterruptedException {
		queueThread.join();
	}

	/**
	 * Join the thread. Equivalent to {@link Thread#join(long)}.
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	public void join(long millis) throws InterruptedException {
		queueThread.join(millis);
	}

	/**
	 * Join the thread. Equivalent to {@link Thread#join(long, int)}.
	 *
	 * @throws InterruptedException
	 * 		if interrupted before join is complete
	 */
	public void join(long millis, int nanos) throws InterruptedException {
		queueThread.join(millis, nanos);
	}

	/**
	 * Attempt to gracefully stop the thread.
	 */
	public void stop() {
		queueThread.stop();
	}

	/**
	 * Interrupt this thread.
	 */
	public void interrupt() {
		queueThread.interrupt();
	}

	/**
	 * Check if this thread is currently alive.
	 */
	public boolean isAlive() {
		return queueThread.isAlive();
	}

	/**
	 * Get the underlying queue.
	 */
	public Queue<T> getQueue() {
		return queueThread;
	}

}
