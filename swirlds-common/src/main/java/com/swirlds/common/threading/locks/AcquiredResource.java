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

package com.swirlds.common.threading.locks;

import com.swirlds.common.AutoCloseableNonThrowing;

/**
 * An instance which is returned by the {@link ResourceLock} when the lock is acquired. Provides access to the locked
 * resource.
 *
 * @param <T>
 * 		the type of resource
 */
public class AcquiredResource<T> implements MaybeLockedResource<T> {
	private final AutoCloseableNonThrowing unlock;
	private T resource;

	public AcquiredResource(final AutoCloseableNonThrowing unlock, final T resource) {
		this.unlock = unlock;
		this.resource = resource;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T getResource() {
		return resource;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setResource(final T resource) {
		this.resource = resource;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isLockAcquired() {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		unlock.close();
	}
}
