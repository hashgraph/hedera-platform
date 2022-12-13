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

import com.swirlds.common.FastCopyable;
import com.swirlds.common.Reservable;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.common.io.SerializableDet;
import com.swirlds.common.merkle.interfaces.MerkleMigratable;
import com.swirlds.common.merkle.interfaces.MerkleTraversable;
import com.swirlds.common.merkle.iterators.MerkleIterator;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.route.MerkleRouteIterator;
import com.swirlds.common.merkle.synchronization.views.MaybeCustomReconnectRoot;

/**
 * A MerkleNode object has the following properties
 *
 * <ul>
 *   <li>Doesn't need to compute its hash
 *   <li>It's not aware of Cryptographic Modules
 *   <li>Doesn't need to perform rsync
 *   <li>Doesn't need to provide hints to the Crypto Module
 * </ul>
 */
public interface MerkleNode
        extends FastCopyable,
                Hashable,
                MerkleMigratable,
                MerkleTraversable,
                MaybeCustomReconnectRoot,
                Reservable,
                SerializableDet {

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    MerkleNode copy();

    /** {@inheritDoc} */
    @Override
    default MerkleNode migrate(final int version) {
        return this;
    }

    /** {@inheritDoc} */
    @Override
    default MerkleNode getNodeAtRoute(final MerkleRoute route) {
        return new MerkleRouteIterator(this, route).getLast();
    }

    /** {@inheritDoc} */
    @Override
    default <T extends MerkleNode> MerkleIterator<T> treeIterator() {
        return new MerkleIterator<>(this);
    }
}
