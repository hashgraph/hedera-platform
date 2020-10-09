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

import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * Implementation of a reusable background thread that dispatches asynchronous work items to the provided {@link
 * AsyncOperationHandler} by removing the work items from the provided {@link Queue}.
 */
public class IntakeDispatcher<Element, Provider extends OperationProvider, Handler extends AsyncOperationHandler> {

	private final CryptoEngine engine;
	private final Thread worker;
	private final BlockingQueue<List<Element>> backingQueue;
	private final Provider provider;
	private final BiFunction<Provider, List<Element>, Handler> handlerSupplier;

	private ExecutorService executorService;

	private volatile boolean running = true;

	/**
	 * Constructor that initializes all internal variables and launches the background thread. All background threads
	 * are launched with a {@link ThreadExceptionHandler} to handle and log all exceptions thrown by the thread.
	 *
	 * All threads constructed by this class are launched with the {@link Thread#setDaemon(boolean)} value specified as
	 * {@code true}. This class will launch a total of {@code parallelism + 1} threads.
	 *
	 * @param engine
	 * 		the {@link CryptoEngine} object for calculating Hash and verifying signatures
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
	public IntakeDispatcher(final CryptoEngine engine, final Class<Element> elementType,
			final BlockingQueue<List<Element>> backingQueue, final Provider provider, final int parallelism, final
	BiFunction<Provider, List<Element>, Handler> handlerSupplier) {
		this.engine = engine;
		this.backingQueue = backingQueue;
		this.provider = provider;
		this.handlerSupplier = handlerSupplier;

		this.executorService = Executors.newFixedThreadPool(parallelism,
				new CryptoThreadFactory(elementType.getSimpleName(),
						new ThreadExceptionHandler(IntakeDispatcher.class)));

		this.worker = new Thread(this::execute);
		this.worker.setName(String.format("< adv crypto: %s intake dispatcher >", elementType.getSimpleName()));
		this.worker.setDaemon(true);
		this.worker.setUncaughtExceptionHandler(new ThreadExceptionHandler(this.getClass()));
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

}
