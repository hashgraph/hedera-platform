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

package com.swirlds.common.utility;

import com.swirlds.common.Releasable;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Keeps track of a {@link com.swirlds.common.constructable.RuntimeConstructable} object. When the object is released,
 * this record will also be released.
 */
public class RuntimeObjectRecord implements Releasable {

	/**
	 * Has this object been released?
	 */
	private boolean released;

	/**
	 * An action to run when this record becomes released.
	 */
	private final Consumer<RuntimeObjectRecord> cleanupAction;

	/**
	 * The time when this object was created.
	 */
	private final Instant creationTime;

	public RuntimeObjectRecord(final Instant creationTime, Consumer<RuntimeObjectRecord> cleanupAction) {
		released = false;
		this.creationTime = creationTime;
		this.cleanupAction = cleanupAction;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void release() {
		throwIfDestroyed();
		released = true;
		if (cleanupAction != null) {
			cleanupAction.accept(this);
		}
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
}
