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
package com.swirlds.platform.state.signed;

import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRoute;

/**
 * A pair of mismatched nodes. Nodes may be null.
 *
 * @param nodeA the node from tree A
 * @param nodeB the node from tree B
 */
public record MismatchedNodes(MerkleNode nodeA, MerkleNode nodeB) {

    /**
     * Append information describing the differences in the nodes to a string builder.
     *
     * @param stringBuilder the string builder to add to
     */
    public void appendNodeDescriptions(final StringBuilder stringBuilder) {
        final MerkleRoute route = nodeA == null ? nodeB.getRoute() : nodeA.getRoute();

        final int routeSize = route.size();
        stringBuilder.append("  ".repeat(routeSize));

        if (route.isEmpty()) {
            stringBuilder.append("(root)");
        } else {
            stringBuilder.append(route.getStep(-1));
        }
        stringBuilder.append(" ");

        if (nodeA == null) {
            stringBuilder
                    .append("Node from tree A is NULL, node from tree B is a ")
                    .append(nodeB.getClass().getSimpleName());
        } else if (nodeB == null) {
            stringBuilder
                    .append("Node from tree A is is a ")
                    .append(nodeA.getClass().getSimpleName())
                    .append(", node from tree B is NULL");
        } else if (nodeA.getClassId() != nodeB.getClassId()) {
            stringBuilder
                    .append("Node from tree A is a ")
                    .append(nodeA.getClass().getSimpleName())
                    .append(", node from tree B is a ")
                    .append(nodeB.getClass().getSimpleName());
        } else {
            stringBuilder
                    .append(nodeA.getClass().getSimpleName())
                    .append(" A = ")
                    .append(nodeA.getHash())
                    .append(", B = ")
                    .append(nodeB.getHash());
        }

        stringBuilder.append("\n");

        if (nodeA instanceof MerkleLeaf || nodeB instanceof MerkleLeaf) {
            stringBuilder.append("  ".repeat(routeSize + 1));
            stringBuilder
                    .append("Full route: ")
                    .append(nodeA == null ? nodeB.getRoute() : nodeA.getRoute());
            stringBuilder.append("\n");
        }

        if (nodeA instanceof MerkleLeaf) {
            stringBuilder.append("  ".repeat(routeSize + 1));
            stringBuilder.append("A.toString(): ").append(nodeA);
            stringBuilder.append("\n");
        }

        if (nodeB instanceof MerkleLeaf) {
            stringBuilder.append("  ".repeat(routeSize + 1));
            stringBuilder.append("B.toString(): ").append(nodeB);
            stringBuilder.append("\n");
        }
    }
}
