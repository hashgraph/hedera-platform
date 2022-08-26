/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.state;

import com.swirlds.common.Releasable;

import java.time.Duration;
import java.time.Instant;

/**
 * Keeps track of a {@link State} object. When the State is released this object will also be released.
 */
public class StateRecord implements Releasable {

	/**
	 * Has this object been released?
	 */
	private boolean released;

	/**
	 * The time when this object was created.
	 */
	private final Instant creationTime;

	/**
	 * The time that elapsed between this record being created and the previous record being created.
	 * Used to estimate quiescent time. Managed by the {@link StateRegistry}.
	 */
	private Duration timeSincePreviousRecord;

	public StateRecord(final Instant creationTime) {
		released = false;
		this.creationTime = creationTime;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void release() {
		throwIfDestroyed();
		released = true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean isDestroyed() {
		return released;
	}

	/**
	 * Get the time when this object was created.
	 */
	public Instant getCreationTime() {
		return creationTime;
	}

	/**
	 * Get the time that elapsed between this record being created and the previous record being created.
	 * Used to estimate quiescent time. Managed by the {@link StateRegistry}.
	 */
	public Duration getTimeSincePreviousRecord() {
		return timeSincePreviousRecord;
	}

	/**
	 * Set the time that elapsed between this record being created and the previous record being created.
	 * Used to estimate quiescent time. Managed by the {@link StateRegistry}.
	 */
	public void setTimeSincePreviousRecord(final Duration timeSincePreviousRecord) {
		this.timeSincePreviousRecord = timeSincePreviousRecord;
	}
}
