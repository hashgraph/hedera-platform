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

import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.route.MerkleRoute;

import java.util.Set;

import static com.swirlds.common.merkle.utility.MerkleSerializationStrategy.SELF_SERIALIZATION;

/**
 * This abstract class implements boiler plate functionality for a {@link MerkleLeaf}. Classes that implement
 * {@link MerkleLeaf} are not required to extend this class, but absent a reason it is recommended to avoid
 * re-implementation of this code.
 */
public abstract class AbstractMerkleLeaf extends AbstractMerkleNode implements MerkleLeaf {

	private static final Set<MerkleSerializationStrategy> DEFAULT_STRATEGIES = Set.of(SELF_SERIALIZATION);

	protected AbstractMerkleLeaf() {

	}

	/**
	 * Copy constructor.
	 */
	protected AbstractMerkleLeaf(final AbstractMerkleLeaf that) {
		super(that);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void setRoute(final MerkleRoute route) {
		super.setRoute(route);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract AbstractMerkleLeaf copy();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<MerkleSerializationStrategy> supportedSerialization(final int version) {
		return DEFAULT_STRATEGIES;
	}
}
