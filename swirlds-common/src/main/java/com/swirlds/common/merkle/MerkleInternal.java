/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.common.merkle;

import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.merkle.interfaces.MerkleParent;
import com.swirlds.common.merkle.interfaces.Rebuildable;

/**
 * A MerkleInternal is one interior node in a Merkle tree structure. It has the following
 * properties:
 *
 * <ul>
 *   <li>It only has children (no data)
 *   <li>Each child is a MerkleNode object
 *   <li>Requires a no-arg constructor (due to inheritance from RuntimeConstructable)
 *   <li>Requires a constructor that takes {@code List<Merkle> children}
 * </ul>
 *
 * A MerkleInternal node may have utility methods that increase the number of children after the
 * node has been constructed. Child nodes MUST NOT be generated or modified in the following
 * functions:
 *
 * <p>- The zero-argument constructor - getNumberOfChildren() - getChild() - isLeaf()
 *
 * <p>It is highly recommended that any class implementing this interface extend either {@link
 * PartialBinaryMerkleInternal} (if the node has 2 or fewer children) or {@link
 * PartialNaryMerkleInternal} (if the node has greater than 2 children).
 */
public interface MerkleInternal extends MerkleNode, MerkleParent, Rebuildable {

    /**
     * {@inheritDoc}
     *
     * <p>There are currently 3 strategies that internal nodes use to perform copy.
     *
     * <p>1) Cascading copy. When a node using this strategy is copied it simply calls copy on all
     * of its children and adds those copies to the new object. This is inefficient -- a copy of a
     * tree that uses cascading copy is an O(n) operation (where n is the number of nodes).
     *
     * <p>2) Smart copy (aka copy-on-write). When a node using this strategy is copied, it copies
     * the root of its subtree. When a descendant is modified, it creates a path from the root down
     * to the node and then modifies the copy of the node.
     *
     * <p>3) Self copy. When this internal node is copied it only copies metadata. Its children are
     * uncopied, and the new node has null in place of any children. This strategy can only
     * CURRENTLY be used on nodes where copying is managed by an ancestor node (for example, an
     * ancestor that does smart copies).
     *
     * <p>Eventually there will be utilities that manage all aspects of copying a merkle tree. At
     * that point in time, all internal nodes will be required to implement copy strategy 3.
     */
    @Override
    MerkleInternal copy();

    /** {@inheritDoc} */
    @Override
    default void rebuild() {}
}
