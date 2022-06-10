/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.threading;

import java.util.concurrent.Callable;

/**
 * Same as {@link Runnable} but can throw an {@link Exception}. Also extends {@link Callable} for convenience.
 */
@FunctionalInterface
public interface ThrowingRunnable extends Callable<Void> {
	/**
	 * Execute this runnable
	 *
	 * @throws Exception
	 * 		if any issue occurs
	 */
	void run() throws Exception;

	/**
	 * {@inheritDoc}
	 */
	@Override
	default Void call() throws Exception {
		run();
		return null;
	}
}
