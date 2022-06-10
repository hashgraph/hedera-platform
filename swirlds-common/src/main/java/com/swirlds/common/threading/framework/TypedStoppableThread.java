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

package com.swirlds.common.threading.framework;

import com.swirlds.common.threading.interrupt.InterruptableRunnable;

/**
 * A {@link StoppableThread} that is aware of the type of object being used to do work.
 *
 * @param <T>
 * 		the type of object used to do work
 */
public interface TypedStoppableThread<T extends InterruptableRunnable> extends StoppableThread {

	/**
	 * Get the object used to do work.
	 *
	 * @return the object used to do work
	 */
	T getWork();

}

