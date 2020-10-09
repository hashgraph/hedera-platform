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

package com.swirlds.common;

/**
 * A wrapper around an arbitrary object that is auto closeable.
 *
 * @param <T>
 * 		the type of the wrapped object
 */
public class AutoCloseableWrapper<T> implements AutoCloseable {

	private final T object;
	private final Runnable closeCallback;

	/**
	 * Create a new AutoCloseable wrapper.
	 *
	 * @param object
	 * 		The object that is being wrapped.
	 * @param closeCallback
	 * 		The function that is called when the wrapper is closed.
	 */
	public AutoCloseableWrapper(T object, Runnable closeCallback) {
		this.object = object;
		this.closeCallback = closeCallback;
	}

	/**
	 * Get the wrapped object.
	 */
	public T get() {
		return object;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		closeCallback.run();
	}
}
