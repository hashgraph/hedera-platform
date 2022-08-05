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

package com.swirlds.common.crypto;

import com.swirlds.common.AbstractHashable;
import com.swirlds.common.threading.futures.WaitingFuture;

/**
 * Represents a single {@link Hash} instance taken of a given moment in time from a running hash chain. A given {@link
 * RunningHash} instance may represent a past moment in time when the hash is already known or a future moment in time
 * for which the hash has yet to be computed.
 *
 * If this instance represents a past moment in time, then the {@link #getHash()} method is guaranteed to return a valid
 * hash and the {@link #getFutureHash()} method is guaranteed to return a resolved future.
 *
 * If this instance represents a future moment in time, then the {@link #getHash()} method will immediately return a
 * {@code null} value and the {@link #getFutureHash()} method is guaranteed to return an unresolved future. Once the
 * future is resolved the behavior will be identical to an instance which represents a past moment in time as described
 * above.
 */
public class RunningHash extends AbstractHashable implements FutureHashable {

	/**
	 * The {@link java.util.concurrent.Future} instance which is resolved when the hash value is provided.
	 */
	private final WaitingFuture<Hash> futureHash;

	/**
	 * Creates a new instance without a hash value and an unresolved future.
	 */
	public RunningHash() {
		futureHash = new WaitingFuture<>();
	}

	/**
	 * Creates a new instance with the future already resolved and the hash already set to the provided value.
	 *
	 * @param hash
	 * 		the hash value with which to initialize this instance.
	 */
	public RunningHash(final Hash hash) {
		futureHash = new WaitingFuture<>(hash);
		super.setHash(hash);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public WaitingFuture<Hash> getFutureHash() {
		return futureHash;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setHash(final Hash hash) {
		futureHash.done(hash);
		super.setHash(hash);
	}
}
