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

package com.swirlds.common.merkle.synchronization;

public interface ReconnectSettings {

	/**
	 * The amount of time that an {@link AsyncInputStream} will wait for a message before aborting the reconnect.
	 */
	int getAsyncInputStreamTimeoutMilliseconds();

	/**
	 * The maximum number of messages that an {@link AsyncOutputStream} will send before flushing.
	 */
	int getAsyncOutputStreamMaxUnflushedMessages();

	/**
	 * If false then the async streams behave as if they were synchronous. Significantly effects performance, should
	 * be true unless the async streams are being debugged.
	 */
	boolean isAsyncStreams();

	/**
	 * The number of threads used by the receiving synchronizer to handle responses.
	 */
	int getSendingSynchronizerResponseThreadPoolSize();

	/**
	 * The number of threads used by the hash validator.
	 */
	public int getHashValidationThreadPoolSize();

	/**
	 * The maximum amount of time to wait for an ACK message. If no ACK is received
	 * and sufficient time passes then send the potentially redundant node.
	 */
	int getMaxAckDelayMilliseconds();
}
