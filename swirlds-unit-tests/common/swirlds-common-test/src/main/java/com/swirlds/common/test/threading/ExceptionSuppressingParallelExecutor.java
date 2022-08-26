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

package com.swirlds.common.test.threading;

import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.common.threading.pool.ParallelExecutor;

import java.util.concurrent.Callable;

/**
 * Parallel executor that suppressed all exceptions.
 */
public class ExceptionSuppressingParallelExecutor implements ParallelExecutor {

	private final ParallelExecutor executor;

	public ExceptionSuppressingParallelExecutor() {
		executor = new CachedPoolParallelExecutor("sync-phase-thread");
	}

	@Override
	public <T> T doParallel(
			final Callable<T> task1,
			final Callable<Void> task2)
			throws ParallelExecutionException {
		try {
			return executor.doParallel(task1, task2);
		} catch (final ParallelExecutionException e) {
			// suppress exceptions
			return null;
		}
	}
}
