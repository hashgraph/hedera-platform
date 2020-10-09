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

import com.swirlds.common.futures.WaitingFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static com.swirlds.common.crypto.engine.CryptoEngine.LOGM_TESTING_EXCEPTIONS;

/**
 * Provides a generic way to process cryptographic transformations for a given {@link List} of work items in a
 * asynchronous manner on a background thread. This object also serves as the {@link java.util.concurrent.Future}
 * implementation assigned to each item contained in the {@link List}.
 *
 * @param <Element>
 * 		the type of the input to be transformed
 * @param <Provider>
 * 		the type of the {@link OperationProvider} implementation to be used
 */
public abstract class AsyncOperationHandler<Element, Provider extends OperationProvider> extends WaitingFuture<Void> implements Runnable {

	private final List<Element> workItems;
	private final Provider provider;
	private final Logger log;


	/**
	 * Constructs an {@link AsyncOperationHandler} which will operate on the provided {@link List} of items using the
	 * specified algorithm provider. This method does not make a copy of the list provided and expects exclusive access
	 * to the list.
	 *
	 * @param workItems
	 * 		the list of items to be asynchronously processed by the algorithm provider
	 * @param provider
	 * 		the algorithm provider used to perform cryptographic transformations on each item
	 */
	public AsyncOperationHandler(final List<Element> workItems, final Provider provider) {
		this(workItems, false, provider);
	}

	/**
	 * Constructs an {@link AsyncOperationHandler} which will operate on the provided {@link List} of items using the
	 * specified algorithm provider.
	 *
	 * @param workItems
	 * 		the list of items to be asynchronously processed by the algorithm provider
	 * @param shouldCopy
	 * 		if true, then a shallow copy of the provided list will be made; otherwise the original list will be used
	 * @param provider
	 * 		the algorithm provider used to perform cryptographic transformations on each item
	 */
	public AsyncOperationHandler(final List<Element> workItems, final boolean shouldCopy, final Provider provider) {
		super();

		if (shouldCopy) {
			this.workItems = new ArrayList<>(workItems);
		} else {
			this.workItems = workItems;
		}

		this.log = LogManager.getLogger(this.getClass());
		this.provider = provider;
	}

	/**
	 * When an object implementing interface <code>Runnable</code> is used
	 * to create a thread, starting the thread causes the object's
	 * <code>run</code> method to be called in that separately executing
	 * thread.
	 * <p>
	 * The general contract of the method <code>run</code> is that it may
	 * take any action whatsoever.
	 *
	 * @see Thread#run()
	 */
	@Override
	public void run() {
		for (Element item : workItems) {
			try {
				handleWorkItem(provider, item);
			} catch (RuntimeException | NoSuchAlgorithmException ex) {
				log.warn(LOGM_TESTING_EXCEPTIONS, "Intercepted Uncaught Exception", ex);
			}
		}

		done(null);
	}

	/**
	 * Called by the {@link #run()} method to process the cryptographic transformation for a single item on the
	 * background
	 * thread.
	 *
	 * @param provider
	 * 		the algorithm provider to use
	 * @param item
	 * 		the input to be transformed
	 * @throws NoSuchAlgorithmException
	 * 		if an implementation of the required algorithm cannot be located or loaded
	 */
	protected abstract void handleWorkItem(final Provider provider, final Element item) throws NoSuchAlgorithmException;

	/**
	 * Provides the implementor with access to the {@link Logger}.
	 *
	 * @return an initialized logger
	 */
	protected Logger log() {
		return log;
	}
}
