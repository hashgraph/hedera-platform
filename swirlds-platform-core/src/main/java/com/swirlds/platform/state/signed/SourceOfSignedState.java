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

package com.swirlds.platform.state.signed;

/**
 * Various ways that {@link com.swirlds.platform.state.signed.SignedState SignedState}s get created.
 */
public enum SourceOfSignedState {
	/**
	 * This signed state was read from the disk.
	 */
	DISK,
	/**
	 * This signed state was obtained through a reconnect.
	 */
	RECONNECT,
	/**
	 * This signed state was the product of applying consensus transactions to the state (standard pathway).
	 */
	TRANSACTIONS
}
