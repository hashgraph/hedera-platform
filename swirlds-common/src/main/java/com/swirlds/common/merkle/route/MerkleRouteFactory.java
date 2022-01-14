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

package com.swirlds.common.merkle.route;

import com.swirlds.common.merkle.route.internal.BinaryMerkleRoute;
import com.swirlds.common.merkle.route.internal.UncompressedMerkleRoute;

/**
 * A factory for new merkle routes.
 */
public final class MerkleRouteFactory {

	private static MerkleRoute emptyRoute = new BinaryMerkleRoute();

	private MerkleRouteFactory() {

	}

	/**
	 * Various encoding strategies for merkle routes.
	 */
	public enum MerkleRouteEncoding {
		/**
		 * Routes are compressed. Optimized heavily for routes with sequences of binary steps.
		 */
		BINARY_COMPRESSION,
		/**
		 * Routes are completely uncompressed. Uses more memory but is faster to read and write.
		 */
		UNCOMPRESSED
	}

	/**
	 * Specify the algorithm for encoding merkle routes.
	 */
	public static void setRouteEncodingStrategy(final MerkleRouteEncoding encoding) {
		switch (encoding) {
			case BINARY_COMPRESSION:
				emptyRoute = new BinaryMerkleRoute();
				break;
			case UNCOMPRESSED:
				emptyRoute = new UncompressedMerkleRoute();
				break;
			default:
				throw new IllegalArgumentException("Unhandled type: " + encoding);
		}
	}

	/**
	 * Get an empty merkle route.
	 */
	public static MerkleRoute getEmptyRoute() {
		return emptyRoute;
	}

}
