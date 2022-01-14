/*
 * (c) 2016-2022 Swirlds, Inc.
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

package com.swirlds.logging.payloads;

/**
 * This payload is logged when the queue of an FCHashMapGarbageCollector grows too large.
 */
public class GarbageCollectionQueuePayload extends AbstractLogPayload {

	private int queueSize;

	public GarbageCollectionQueuePayload(final int queueSize) {
		super("FCHashMap garbage collection queue size exceeds threshold");
		this.queueSize = queueSize;
	}

	/**
	 * Get the size of the queue.
	 */
	public int getQueueSize() {
		return queueSize;
	}

	/**
	 * Set the size of the queue.
	 */
	public void setQueueSize(final int queueSize) {
		this.queueSize = queueSize;
	}
}
