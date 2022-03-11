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

package com.swirlds.common.merkle.iterators.internal;

import com.swirlds.common.merkle.MerkleInternal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.ObjIntConsumer;

/**
 * Iteration algorithm for
 * {@link com.swirlds.common.merkle.iterators.MerkleIterationOrder#POST_ORDERED_DEPTH_FIRST_RANDOM
 * POST_ORDERED_DEPTH_FIRST_RANDOM}.
 */
public class PostOrderedDepthFirstRandomAlgorithm extends PostOrderedDepthFirstAlgorithm {

	private final Random random = new Random();

	/**
	 * Add children to the stack in a random order.
	 * {@inheritDoc}
	 */
	@Override
	public void pushChildren(final MerkleInternal parent, final ObjIntConsumer<MerkleInternal> pushNode) {
		final List<Integer> iterationOrder = new ArrayList<>(parent.getNumberOfChildren());
		for (int childIndex = 0; childIndex < parent.getNumberOfChildren(); childIndex++) {
			iterationOrder.add(childIndex);
		}

		Collections.shuffle(iterationOrder, random);
		for (final int childIndex : iterationOrder) {
			pushNode.accept(parent, childIndex);
		}
	}

}
