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

package com.swirlds.common.merkle.utility;

import com.swirlds.common.merkle.io.SerializationStrategy;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.route.MerkleRoute;

import java.util.Set;

import static com.swirlds.common.merkle.io.SerializationStrategy.SELF_SERIALIZATION;

/**
 * This abstract class implements boiler plate functionality for a {@link MerkleLeaf}. Classes that implement
 * {@link MerkleLeaf} are not required to extend this class, but absent a reason it is recommended to avoid
 * re-implementation of this code.
 */
public abstract class AbstractMerkleLeaf extends AbstractMerkleNode implements MerkleLeaf {

	private static final Set<SerializationStrategy> DEFAULT_STRATEGIES = Set.of(SELF_SERIALIZATION);

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
	public Set<SerializationStrategy> supportedSerialization(final int version) {
		return DEFAULT_STRATEGIES;
	}
}
