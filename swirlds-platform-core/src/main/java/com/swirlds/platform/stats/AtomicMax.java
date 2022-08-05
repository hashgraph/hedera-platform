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

package com.swirlds.platform.stats;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A maximum value that is updated atomically and is thread safe
 */
public class AtomicMax {
	/** default value to return if max is not initialized */
	private static final long DEFAULT_UNINITIALIZED = 0;

	private final AtomicLong max;
	/** the value to return before any values update the max */
	private final long uninitializedValue;

	public AtomicMax(final long uninitializedValue) {
		this.uninitializedValue = uninitializedValue;
		max = new AtomicLong(uninitializedValue);
	}

	public AtomicMax() {
		this(DEFAULT_UNINITIALIZED);
	}

	public long get() {
		return max.get();
	}

	public void reset(){
		max.set(uninitializedValue);
	}

	public long getAndReset(){
		return max.getAndSet(uninitializedValue);
	}

	public void update(final long value) {
		max.accumulateAndGet(value, Math::max);
	}
}
