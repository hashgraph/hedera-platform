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

package com.swirlds.platform.reconnect;

import com.swirlds.common.merkle.synchronization.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.AsyncOutputStream;

public class ReconnectSettings implements com.swirlds.common.merkle.synchronization.ReconnectSettings {

	/**
	 * Determines what a node will do when it falls behind. If true, it will attempt a reconnect, if false, it will die.
	 */
	public boolean active = false;

	/**
	 * The fraction of neighbors needed to tell us we have fallen behind before we initiate a reconnect.
	 */
	public double fallenBehindThreshold = 0.50;


	/**
	 * The amount of time that an {@link AsyncInputStream} will wait for a message before aborting the reconnect.
	 */
	public static int asyncInputStreamTimeoutMilliseconds = 10_000;

	/**
	 * The maximum number of messages that an {@link AsyncOutputStream} will send before flushing.
	 */
	public int asyncOutputStreamMaxUnflushedMessages = 100;

	/**
	 * If false then the async streams behave as if they were synchronous. Significantly effects performance, should
	 * be true unless the async streams are being debugged.
	 */
	public boolean asyncStreams = true;

	/**
	 * The number of threads used by the receiving synchronizer to handle responses.
	 */
	public int sendingSynchronizerResponseThreadPoolSize = 4;

	/**
	 * The maximum amount of time to wait for an ACK message. If no ACK is received
	 * and sufficient time passes then send the potentially redundant node.
	 */
	public int maxAckDelayMilliseconds = 10;

	/**
	 * The number of threads to use for validating hashes during a reconnect.
	 */
	public int hashValidationThreadPoolSize = 40;

	/**
	 * {@inheritDoc}
	 */
	public boolean isActive() {
		return active;
	}

	/**
	 * {@inheritDoc}
	 */
	public double getFallenBehindThreshold() {
		return fallenBehindThreshold;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getAsyncInputStreamTimeoutMilliseconds() {
		return asyncInputStreamTimeoutMilliseconds;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getAsyncOutputStreamMaxUnflushedMessages() {
		return asyncOutputStreamMaxUnflushedMessages;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isAsyncStreams() {
		return asyncStreams;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSendingSynchronizerResponseThreadPoolSize() {
		return sendingSynchronizerResponseThreadPoolSize;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMaxAckDelayMilliseconds() {
		return maxAckDelayMilliseconds;
	}

	/**
	 * {@inheritDoc}
	 * @return
	 */
	@Override
	public int getHashValidationThreadPoolSize() {
		return hashValidationThreadPoolSize;
	}
}
