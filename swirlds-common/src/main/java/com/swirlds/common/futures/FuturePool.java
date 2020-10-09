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

package com.swirlds.common.futures;

import com.swirlds.common.PlatformException;
import com.swirlds.logging.LogMarker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class FuturePool<V> extends LinkedList<Future<V>> {

	private final Consumer<Exception> exceptionHandler;

	private static volatile boolean shuttingDown = false;

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			shuttingDown = true;
		}));
	}

	/**
	 * Constructs an empty list.
	 */
	public FuturePool() {
		exceptionHandler = null;
	}

	/**
	 * Constructs an empty list.
	 */
	public FuturePool(final Consumer<Exception> exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
	}

	/**
	 * Constructs a list containing the elements of the specified collection, in the order they are returned by the
	 * collection's iterator.
	 *
	 * @param c
	 * 		the collection whose elements are to be placed into this list
	 * @throws NullPointerException
	 * 		if the specified collection is null
	 */
	public FuturePool(final Collection<? extends Future<V>> c, final Consumer<Exception> exceptionHandler) {
		super(c);
		this.exceptionHandler = exceptionHandler;
	}

	/**
	 * Constructs a list containing the elements of the specified collection, in the order they are returned by the
	 * collection's iterator.
	 *
	 * @param c
	 * 		the collection whose elements are to be placed into this list
	 * @throws NullPointerException
	 * 		if the specified collection is null
	 */
	public FuturePool(final Collection<? extends Future<V>> c) {
		super(c);
		this.exceptionHandler = null;
	}

	/**
	 * Determines if all futures have been completed or cancelled.
	 *
	 * @return true if all futures are completed or cancelled, otherwise false
	 */
	public boolean isComplete() {
		removeIf((v) -> v.isDone() || v.isCancelled());
		return size() == 0;
	}

	/**
	 * Getter that returns true if the shutdown hook has been called by the JVM.
	 *
	 * @return true if the JVM is shutting down; false otherwise
	 */
	private static boolean isShuttingDown() {
		return shuttingDown;
	}

	/**
	 * Waits (indefinitely) for all futures to either complete or be cancelled.
	 *
	 * @return an ordered {@link List} containing all values (or {@code null} if cancelled) returned by the futures
	 * 		contained in this {@link FuturePool}.
	 */
	public List<V> waitForCompletion() {
		final List<V> results = new ArrayList<>(size());

		forEach((f) -> {
			if (f.isCancelled()) {
				results.add(null);
				return;
			}

			try {
				results.add(f.get());
			} catch (InterruptedException | ExecutionException ex) {
				if (exceptionHandler != null) {
					if (!isShuttingDown()) {
						exceptionHandler.accept(ex);
					}
				} else {
					if (!isShuttingDown()) {

						if (ex instanceof InterruptedException) {
							Thread.currentThread().interrupt();
						}

						throw new PlatformException(ex, LogMarker.EXCEPTION);
					}
				}
			}
		});

		return results;
	}
}
