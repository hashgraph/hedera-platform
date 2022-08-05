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

package com.swirlds.common.utility;

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
	 *
	 * @return the wrapped object
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
