/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.crypto.engine;

import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.logging.LogMarker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static com.swirlds.common.crypto.engine.CryptoEngine.THREAD_COMPONENT_NAME;

/**
 * Implementation of a reusable background thread that dispatches asynchronous work items to the provided {@link
 * AsyncOperationHandler} by removing the work items from the provided {@link Queue}.
 */
public class IntakeDispatcher<Element, Provider extends OperationProvider, Handler extends AsyncOperationHandler> {

	/**
	 * use this for all logging, as controlled by the optional data/log4j2.xml file
	 */
	private static final Logger LOG = LogManager.getLogger(IntakeDispatcher.class);

	/**
	 * The thread used for polling the {@code backingQueue} and submitting tasks to the {@code executorService} thread
	 * pool.
	 */
	private final Thread worker;

	/**
	 * The queue of incoming items to be processed by the {@code worker} thread.
	 */
	private final BlockingQueue<List<Element>> backingQueue;

	/**
	 * The underlying {@link OperationProvider} to use for handling the work items.
	 */
	private final Provider provider;

	/**
	 * A {@link BiFunction} that accepts an {@link OperationProvider} and a list of work items then returns an {@link
	 * AsyncOperationHandler} instance.
	 */
	private final BiFunction<Provider, List<Element>, Handler> handlerSupplier;

	/**
	 * The {@link ExecutorService} that provides the thread pool on which the {@link OperationProvider} does its work.
	 */
	private final ExecutorService executorService;

	/**
	 * Flag indicating the current execution state of the {@code worker} thread.
	 */
	private volatile boolean running = true;

	/**
	 * Constructor that initializes all internal variables and launches the background thread. All background threads
	 * are launched with a {@link java.lang.Thread.UncaughtExceptionHandler} to handle and log all exceptions thrown by
	 * the thread.
	 *
	 * All threads constructed by this class are launched with the {@link Thread#setDaemon(boolean)} value specified as
	 * {@code true}. This class will launch a total of {@code parallelism + 1} threads.
	 *
	 * @param elementType
	 * 		the type of Element
	 * @param backingQueue
	 * 		the queue of Elements to be processed
	 * @param provider
	 * 		the cryptographic transformation provider
	 * @param parallelism
	 * 		the number of threads in the pool
	 * @param handlerSupplier
	 * 		the supplier of the handler
	 */
	public IntakeDispatcher(final Class<Element> elementType, final BlockingQueue<List<Element>> backingQueue,
			final Provider provider, final int parallelism,
			final BiFunction<Provider, List<Element>, Handler> handlerSupplier) {
		this.backingQueue = backingQueue;
		this.provider = provider;
		this.handlerSupplier = handlerSupplier;

		final ThreadFactory threadFactory = new ThreadConfiguration()
				.setDaemon(true)
				.setPriority(Thread.NORM_PRIORITY)
				.setComponent(THREAD_COMPONENT_NAME)
				.setThreadName(String.format("%s tp worker", elementType.getSimpleName()))
				.setExceptionHandler(this::handleThreadException)
				.buildFactory();

		this.executorService = Executors.newFixedThreadPool(parallelism, threadFactory);

		this.worker = new ThreadConfiguration()
				.setDaemon(true)
				.setPriority(Thread.NORM_PRIORITY)
				.setComponent(THREAD_COMPONENT_NAME)
				.setThreadName(String.format("%s intake dispatcher", elementType.getSimpleName()))
				.setExceptionHandler(this::handleThreadException)
				.setRunnable(this::execute)
				.build();

		this.worker.start();
	}

	/**
	 * Attempts to forcibly terminate all running threads and free any acquired resources.
	 */
	public void shutdown() {
		this.running = false;
		this.worker.interrupt();

		this.executorService.shutdown();

		try {
			if (!this.executorService.awaitTermination(5, TimeUnit.SECONDS)) {
				this.executorService.shutdownNow();
			}
		} catch (InterruptedException ex) {
			this.executorService.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}


	/**
	 * The main dispatcher thread entry point.
	 */
	private void execute() {
		while (running) {
			try {
				final List<Element> workItems = backingQueue.poll(10, TimeUnit.MILLISECONDS);

				if (workItems != null && workItems.size() > 0) {
					executorService.submit(handlerSupplier.apply(provider, workItems));
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * An {@link java.lang.Thread.UncaughtExceptionHandler} implementation to ensure that all uncaught exceptions on the
	 * dispatcher threads are properly logged.
	 */
	private void handleThreadException(final Thread thread, final Throwable ex) {
		LOG.error(LogMarker.EXCEPTION.getMarker(),
				String.format("Intercepted Uncaught Exception [ threadName = '%s' ]", thread.getName()), ex);
	}
}
