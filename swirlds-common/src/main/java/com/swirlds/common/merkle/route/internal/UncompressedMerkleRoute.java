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

package com.swirlds.common.merkle.route.internal;

import com.swirlds.common.merkle.route.MerkleRoute;

import java.util.Arrays;
import java.util.Iterator;

/**
 * An encoding algorithm for merkle routes. Stores routes in uncompressed format.
 *
 * Requires more memory but is very fast.
 */
public class UncompressedMerkleRoute extends AbstractMerkleRoute {

	private static final int[] emptyData = new int[0];

	private final int[] data;

	public UncompressedMerkleRoute() {
		data = emptyData;
	}

	public UncompressedMerkleRoute(final UncompressedMerkleRoute baseRoute, final int step) {
		data = Arrays.copyOf(baseRoute.data, baseRoute.data.length + 1);
		data[baseRoute.data.length] = step;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Iterator<Integer> iterator() {
		return new UncompressedMerkleRouteIterator(data);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int size() {
		return data.length;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MerkleRoute extendRoute(final int step) {
		return new UncompressedMerkleRoute(this, step);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final UncompressedMerkleRoute that = (UncompressedMerkleRoute) o;
		return Arrays.equals(data, that.data);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Arrays.hashCode(data);
	}
}
