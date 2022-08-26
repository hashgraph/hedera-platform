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

package com.swirlds.common.merkle.interfaces;

import com.swirlds.common.merkle.exceptions.MerkleRouteException;
import com.swirlds.common.merkle.route.MerkleRoute;

/**
 * An object with a merkle route.
 */
public interface HasMerkleRoute {

	/**
	 * Returns the value specified by setRoute(), i.e. the route from the root of the tree down to this node.
	 *
	 * If setRoute() has not yet been called, this method should return an empty merkle route.
	 */
	MerkleRoute getRoute();

	/**
	 * <p>
	 * Get the depth of this node, with the root of a tree having a depth of 0.
	 * </p>
	 *
	 * <p>
	 * Warning: this method may have high overhead depending on the {@link MerkleRoute} implementation, and should
	 * be used with appropriate caution.
	 * </p>
	 *
	 * @return the depth of this node within the tree
	 */
	default int getDepth() {
		return getRoute().size();
	}

	/**
	 * This method is used to store the route from the root to this node.
	 *
	 * It is expected that the value set by this method be stored and returned by getPath().
	 *
	 * This method should NEVER be called manually. Only merkle utility code in
	 * {@link com.swirlds.common.merkle.impl.internal.AbstractMerkleInternal AbstractMerkleInternal}
	 * should ever call this method.
	 *
	 * @throws MerkleRouteException
	 * 		if this node has a reference count is not exactly 1. Routes may only be changed
	 * 		when a node is first added as the child of another node or if there is a single parent
	 * 		and the route of that parent changes.
	 */
	void setRoute(final MerkleRoute route);
}
