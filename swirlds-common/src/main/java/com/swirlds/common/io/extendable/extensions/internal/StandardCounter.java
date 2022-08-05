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

package com.swirlds.common.io.extendable.extensions.internal;

/**
 * A counter that is not thread safe.
 */
public class StandardCounter implements Counter {

	private long count;

	public StandardCounter() {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void resetCount() {
		count = 0;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getCount() {
		return count;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getAndResetCount() {
		final long ret = count;
		count = 0;
		return ret;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long addToCount(final long value) {
		count += value;
		return count;
	}
}
