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

package com.swirlds.common.merkle.synchronization.settings;

import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;

import java.time.Duration;

public interface ReconnectSettings {

	/**
	 * Is reconnect enabled? If a node falls behind, if this is false the node will die, and if true the node
	 * will attempt to reconnect.
	 */
	boolean isActive();

	/**
	 * If -1 then reconnect is always allowed (as long as {@link #isActive()} is true). If a positive integer,
	 * only allow reconnects if the reconnect falls within a time window starting when the node first turns on.
	 */
	int getReconnectWindowSeconds();

	/**
	 * The fraction of neighbors that this node will require to report fallen behind before the node
	 * will consider itself to have fallen behind.
	 */
	double getFallenBehindThreshold();

	/**
	 * The amount of time that an {@link AsyncInputStream} and
	 * {@link AsyncOutputStream} will wait before throwing a timeout.
	 */
	int getAsyncStreamTimeoutMilliseconds();

	/**
	 * @return The maximum time between {@link AsyncInputStream} flushes.
	 */
	int getAsyncOutputStreamFlushMilliseconds();

	/**
	 * The size of the buffers for async input and output streams.
	 */
	int getAsyncStreamBufferSize();

	/**
	 * If no ACK is received and this many time passes then send the potentially redundant node.
	 * @return The maximum amount of time to wait for an ACK message.
	 */
	int getMaxAckDelayMilliseconds();

	/**
	 * The maximum number of allowable reconnect failures in a row before a node shuts itself down.
	 */
	int getMaximumReconnectFailuresBeforeShutdown();

	/**
	 * The minimum time that must pass before a node is willing to help another to reconnect.
	 */
	Duration getMinimumTimeBetweenReconnects();
}
