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

package com.swirlds.common.merkle.iterators;

/**
 * Defines different iteration orders for merkle trees.
 */
public enum MerkleIterationOrder {
	/**
	 * A depth first traversal where parents are visited after their children (i.e. standard depth first).
	 * This is the default iteration order for a {@link MerkleIterator}.
	 */
	POST_ORDERED_DEPTH_FIRST,
	/**
	 * Similar to {@link #POST_ORDERED_DEPTH_FIRST}, but with order between sibling subtrees randomized.
	 */
	POST_ORDERED_DEPTH_FIRST_RANDOM,
	/**
	 * A depth first traversal where parents are visited before their children.
	 */
	PRE_ORDERED_DEPTH_FIRST,
	/**
	 * A breadth first traversal.
	 */
	BREADTH_FIRST,
}
