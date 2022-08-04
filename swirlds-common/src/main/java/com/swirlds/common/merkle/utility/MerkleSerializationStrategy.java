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

package com.swirlds.common.merkle.utility;

import com.swirlds.common.io.ExternalSelfSerializable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDet;

/**
 * Different algorithms that can be used for the serialization of various {@link SerializableDet} objects.
 */
public enum MerkleSerializationStrategy {
	/**
	 * The node is responsible for serializing itself. Must implement {@link SelfSerializable}.
	 */
	SELF_SERIALIZATION,
	/**
	 * The node is is responsible for serializing itself, but some of the data may be written to
	 * a location external to the primary data stream. Must implement {@link ExternalSelfSerializable}.
	 */
	EXTERNAL_SELF_SERIALIZATION,
	/**
	 * Only valid for {@link com.swirlds.common.merkle.MerkleInternal MerkleInternal} objects. If
	 * this strategy is supported then serialization is automatically handled by merkle utilities.
	 */
	DEFAULT_MERKLE_INTERNAL
}
