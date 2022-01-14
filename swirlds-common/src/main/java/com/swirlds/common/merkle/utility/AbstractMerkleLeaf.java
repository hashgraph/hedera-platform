/*
 * (c) 2016-2022 Swirlds, Inc.
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

package com.swirlds.common.merkle.utility;

import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.route.MerkleRoute;

/**
 * This abstract class implements boiler plate functionality for a {@link MerkleLeaf}. Classes that implement
 * {@link MerkleLeaf} are not required to extend this class, but absent a reason it is recommended to avoid
 * re-implementation of this code.
 */
public abstract class AbstractMerkleLeaf extends AbstractMerkleNode implements MerkleLeaf {

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
}
