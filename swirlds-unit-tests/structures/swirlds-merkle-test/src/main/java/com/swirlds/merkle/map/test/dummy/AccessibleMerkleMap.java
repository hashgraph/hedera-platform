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
package com.swirlds.merkle.map.test.dummy;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkle.tree.MerkleBinaryTree;

/** A MerkleMap with several methods publicly exposed. */
@ConstructableIgnored
public class AccessibleMerkleMap<K, V extends MerkleNode & Keyed<K>> extends MerkleMap<K, V> {

    public AccessibleMerkleMap() {
        super();
    }

    public AccessibleMerkleMap(final int initialCapacity) {
        super(initialCapacity);
    }

    public AccessibleMerkleMap(final MerkleMap<K, V> map) {
        super(map);
    }

    /** {@inheritDoc} */
    @Override
    public MerkleBinaryTree<V> getTree() {
        return super.getTree();
    }

    /** {@inheritDoc} */
    @Override
    public FCHashMap<K, V> getIndex() {
        return super.getIndex();
    }
}
