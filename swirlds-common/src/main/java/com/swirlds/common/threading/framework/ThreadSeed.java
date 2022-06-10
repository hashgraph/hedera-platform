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

/**
 * Encapsulates an operation that is normally executed onto its own thread
 * so that it can be injected into a pre-existing thread.
 */
@FunctionalInterface
public interface ThreadSeed {

	/**
	 * Inject this seed onto a thread. The seed will take over the thread and may
	 * change thread settings. When the seed is finished with all of its work,
	 * it will restore the original thread configuration and yield control back
	 * to the caller. Until it yields control, this method will block.
	 */
	void inject();

}
