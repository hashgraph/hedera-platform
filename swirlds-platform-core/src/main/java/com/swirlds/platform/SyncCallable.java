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
package com.swirlds.platform;

import java.util.concurrent.Callable;

/**
 * SyncCallable is identical to Callable, except that it gives its thread a new temporary name while it's
 * running the given code, then changes the name back when it is done. Also, when implementing this abstract
 * class, override the method syncCall(), rather than overriding call().
 *
 * When SyncUtils.sync() calls doParallel(), it needs to send it two Callable objects, which define code to
 * be run in two parallel threads from a pool. By using SyncCallable instead of Callable, it is possible to
 * pass in a name for each of the two threads. This can be useful for debugging.
 */
abstract class SyncCallable implements Callable<Object> {
	/** the old name of the thread before call() was executed, and after it is done */
	private String oldName;
	/** the new name of the thread during the time that call() is being executed */
	private String runName;

	/**
	 * Construct a Callable that will change its thread to the given name while running, then change it
	 * back.
	 *
	 * @param runName
	 * 		the name to give the thread while this.call() is executing
	 */
	public SyncCallable(String runName) {
		super();
		this.runName = runName;
	}

	/**
	 * This method is called by the thread pool, it changes the name of the thread while work is being done
	 * in syncCall(), the changes it back. Cannot be overridden as it is final.
	 */
	@Override
	final public Object call() throws Exception {
		oldName = Thread.currentThread().getName();
		// the runName will start with "<tp " to show it's part of the thread pool
		Thread.currentThread().setName(runName);
		// Thread.currentThread().setName("exPool " + runName);
		Object ret;
		try {
			ret = syncCall();
		} catch (Exception e) {
			Thread.currentThread().setName(oldName);
			throw e;
		}
		Thread.currentThread().setName(oldName);
		return ret;
	}

	/**
	 * Override this method; this is the code that will actually run. When something like doParallel calls
	 * the call() method, that method will change the thread name, then call syncCall(), then change the
	 * name back. So all the real work should be done in syncCall() rather than in call().
	 */
	public Object syncCall() throws Exception {
		throw new Exception("must implement method syncCall()");
	}
}
