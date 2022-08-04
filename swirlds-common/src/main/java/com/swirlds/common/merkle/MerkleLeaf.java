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

package com.swirlds.common.merkle;

import com.swirlds.common.crypto.SerializableHashable;

/**
 * A Merkle Leaf has the following properties:
 * <ul>
 *     <li>Has only data, no children</li>
 *     <li>Data can be internal or external</li>
 * </ul>
 */
public interface MerkleLeaf extends MerkleNode, SerializableHashable {

	@Override
	default boolean isLeaf() {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	MerkleLeaf copy();
}
