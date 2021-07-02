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

package com.swirlds.platform.reconnect;

import com.swirlds.common.merkle.synchronization.AsyncInputStream;
import com.swirlds.platform.internal.SubSetting;

import java.time.Duration;

public class ReconnectSettingsImpl extends SubSetting
		implements com.swirlds.common.merkle.synchronization.ReconnectSettings {

	/**
	 * Determines what a node will do when it falls behind. If true, it will attempt a reconnect, if false, it will die.
	 */
	public boolean active = false;

	/**
	 * Defines a window of time after the node starts up when the node is allowed to reconnect. If -1 then
	 * a node is always allowed to reconnect. Respects {@link #active} -- if active is false then reconnect
	 * is never allowed.
	 */
	public int reconnectWindowSeconds = -1;

	/**
	 * The fraction of neighbors needed to tell us we have fallen behind before we initiate a reconnect.
	 */
	public double fallenBehindThreshold = 0.50;

	/**
	 * The amount of time that an {@link AsyncInputStream} will wait for a message before aborting the reconnect.
	 */
	public static int asyncInputStreamTimeoutMilliseconds = 10_000;

	/**
	 * In order to ensure that data is not languishing in the asyncOutputStream buffer a periodic flush
	 * is performed. The maximum amount of time between these periodic flushes is specified as a fraction
	 * of {@link ReconnectSettingsImpl#asyncInputStreamTimeoutMilliseconds}.
	 */
	public static double asyncOutputStreamFlushFraction = 0.01;

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
	 * The maximum number of failed reconnects in a row before shutdown.
	 */
	public int maximumReconnectFailuresBeforeShutdown = 10;

	/**
	 * The minimum time that must pass before a node is willing to help another node
	 * to reconnect another time. This prevents a node from intentionally or unintentionally slowing
	 * another node down by continuously reconnecting with it. Time is measured starting from when
	 * a reconnect attempt is initialized.
	 */
	public Duration minimumTimeBetweenReconnects = Duration.ofMinutes(10);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isActive() {
		return active;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getReconnectWindowSeconds() {
		return reconnectWindowSeconds;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
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
	public double getAsyncOutputStreamFlushFraction() {
		return asyncOutputStreamFlushFraction;
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
	 */
	@Override
	public int getMaximumReconnectFailuresBeforeShutdown() {
		return maximumReconnectFailuresBeforeShutdown;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getHashValidationThreadPoolSize() {
		return hashValidationThreadPoolSize;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Duration getMinimumTimeBetweenReconnects() {
		return minimumTimeBetweenReconnects;
	}
}
