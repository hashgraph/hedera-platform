/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.common.threading;

import java.util.concurrent.Callable;

/**
 * Used for executing tasks in parallel
 */
public interface ParallelExecutor {
	/**
	 * Run two tasks in parallel
	 *
	 * @param task1
	 * 		a task to execute in parallel
	 * @param task2
	 * 		a task to execute in parallel
	 * @throws ParallelExecutionException
	 * 		if anything goes wrong
	 */
	void doParallel(Callable<Object> task1, Callable<Object> task2) throws ParallelExecutionException;
}
