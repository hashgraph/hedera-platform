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
package com.swirlds.platform.test.state;

import static com.swirlds.common.test.merkle.util.MerkleTestUtils.buildLessSimpleTree;
import static com.swirlds.platform.state.signed.SignedStateComparisonUtility.mismatchedNodeIterator;
import static com.swirlds.platform.state.signed.SignedStateComparisonUtility.printMismatchedNodes;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.utility.MerkleLong;
import com.swirlds.common.test.merkle.dummy.DummyMerkleInternal;
import com.swirlds.common.test.merkle.dummy.DummyMerkleLeaf;
import com.swirlds.platform.state.signed.SignedStateComparisonUtility;
import java.util.Iterator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignedState Comparison Test")
class SignedStateComparisonTest {

    @Test
    @DisplayName("Null States")
    void nullStates() {
        final MerkleNode stateA = null;
        final MerkleNode stateB = null;

        final Iterator<SignedStateComparisonUtility.MismatchedNodes> iterator =
                mismatchedNodeIterator(stateA, stateB);

        assertFalse(iterator.hasNext(), "iterator should be empty");

        // There should be no exceptions thrown by the printing version.
        printMismatchedNodes(stateA, stateB, 1000);
    }

    @Test
    @DisplayName("State A Is Null")
    void stateAIsNull() {

        final MerkleNode stateA = null;
        final MerkleNode stateB = buildLessSimpleTree();
        MerkleCryptoFactory.getInstance().digestTreeSync(stateB);

        final Iterator<SignedStateComparisonUtility.MismatchedNodes> iterator =
                mismatchedNodeIterator(stateA, stateB);

        final SignedStateComparisonUtility.MismatchedNodes nodes = iterator.next();

        assertNull(nodes.nodeA(), "node A should be null");
        assertSame(nodes.nodeB(), stateB, "node B should be the root of tree B");

        assertFalse(iterator.hasNext(), "iterator should be empty");

        // There should be no exceptions thrown by the printing version.
        printMismatchedNodes(stateA, stateB, 1000);
    }

    @Test
    @DisplayName("State B Is Null")
    void stateBIsNull() {
        final MerkleNode stateA = buildLessSimpleTree();
        MerkleCryptoFactory.getInstance().digestTreeSync(stateA);
        final MerkleNode stateB = null;

        final Iterator<SignedStateComparisonUtility.MismatchedNodes> iterator =
                mismatchedNodeIterator(stateA, stateB);

        final SignedStateComparisonUtility.MismatchedNodes nodes = iterator.next();

        assertSame(nodes.nodeA(), stateA, "node A should be the root of tree A");
        assertNull(nodes.nodeB(), "node B should be null");

        assertFalse(iterator.hasNext(), "iterator should be empty");

        // There should be no exceptions thrown by the printing version.
        printMismatchedNodes(stateA, stateB, 1000);
    }

    @Test
    @DisplayName("Matching States")
    void matchingStates() {
        final MerkleNode stateA = buildLessSimpleTree();
        MerkleCryptoFactory.getInstance().digestTreeSync(stateA);
        final MerkleNode stateB = buildLessSimpleTree();
        MerkleCryptoFactory.getInstance().digestTreeSync(stateB);

        final Iterator<SignedStateComparisonUtility.MismatchedNodes> iterator =
                mismatchedNodeIterator(stateA, stateB);

        assertFalse(iterator.hasNext(), "iterator should be empty");

        // There should be no exceptions thrown by the printing version.
        printMismatchedNodes(stateA, stateB, 1000);
    }

    @Test
    @DisplayName("Different Hashes")
    void differentHashes() {
        final MerkleNode stateA = buildLessSimpleTree();
        MerkleCryptoFactory.getInstance().digestTreeSync(stateA);
        final MerkleNode stateB = buildLessSimpleTree();
        ((DummyMerkleLeaf) stateB.getNodeAtRoute(1, 0)).setValue("X");
        MerkleCryptoFactory.getInstance().digestTreeSync(stateB);

        final Iterator<SignedStateComparisonUtility.MismatchedNodes> iterator =
                mismatchedNodeIterator(stateA, stateB);

        SignedStateComparisonUtility.MismatchedNodes nodes = iterator.next();
        assertSame(nodes.nodeA(), stateA, "nodes should match");
        assertSame(nodes.nodeB(), stateB, "nodes should match");

        nodes = iterator.next();
        assertSame(nodes.nodeA(), stateA.getNodeAtRoute(1), "nodes should match");
        assertSame(nodes.nodeB(), stateB.getNodeAtRoute(1), "nodes should match");

        nodes = iterator.next();
        assertSame(nodes.nodeA(), stateA.getNodeAtRoute(1, 0), "nodes should match");
        assertSame(nodes.nodeB(), stateB.getNodeAtRoute(1, 0), "nodes should match");

        assertFalse(iterator.hasNext(), "iterator should be empty");

        // There should be no exceptions thrown by the printing version.
        printMismatchedNodes(stateA, stateB, 1000);
    }

    @Test
    @DisplayName("Different Types")
    void differentTypes() {
        final MerkleNode stateA = buildLessSimpleTree();
        MerkleCryptoFactory.getInstance().digestTreeSync(stateA);
        final MerkleNode stateB = buildLessSimpleTree();
        ((DummyMerkleInternal) stateB.getNodeAtRoute(1)).setChild(0, new MerkleLong(1234));
        MerkleCryptoFactory.getInstance().digestTreeSync(stateB);

        final Iterator<SignedStateComparisonUtility.MismatchedNodes> iterator =
                mismatchedNodeIterator(stateA, stateB);

        SignedStateComparisonUtility.MismatchedNodes nodes = iterator.next();
        assertSame(nodes.nodeA(), stateA, "nodes should match");
        assertSame(nodes.nodeB(), stateB, "nodes should match");

        nodes = iterator.next();
        assertSame(nodes.nodeA(), stateA.getNodeAtRoute(1), "nodes should match");
        assertSame(nodes.nodeB(), stateB.getNodeAtRoute(1), "nodes should match");

        nodes = iterator.next();
        assertSame(nodes.nodeA(), stateA.getNodeAtRoute(1, 0), "nodes should match");
        assertSame(nodes.nodeB(), stateB.getNodeAtRoute(1, 0), "nodes should match");

        assertFalse(iterator.hasNext(), "iterator should be empty");

        // There should be no exceptions thrown by the printing version.
        printMismatchedNodes(stateA, stateB, 1000);
    }

    @Test
    @DisplayName("Different Topologies")
    void differentTopologies() {
        final MerkleNode stateA = buildLessSimpleTree();
        stateA.asInternal().setChild(0, null);
        MerkleCryptoFactory.getInstance().digestTreeSync(stateA);
        final MerkleNode stateB = buildLessSimpleTree();
        stateB.asInternal().setChild(1, null);
        MerkleCryptoFactory.getInstance().digestTreeSync(stateB);

        final Iterator<SignedStateComparisonUtility.MismatchedNodes> iterator =
                mismatchedNodeIterator(stateA, stateB);

        SignedStateComparisonUtility.MismatchedNodes nodes = iterator.next();
        assertSame(nodes.nodeA(), stateA, "nodes should match");
        assertSame(nodes.nodeB(), stateB, "nodes should match");

        nodes = iterator.next();
        assertNull(nodes.nodeA(), "node should be null");
        assertSame(nodes.nodeB(), stateB.getNodeAtRoute(0), "nodes should match");

        nodes = iterator.next();
        assertSame(nodes.nodeA(), stateA.getNodeAtRoute(1), "nodes should match");
        assertNull(nodes.nodeB(), "node should be null");

        assertFalse(iterator.hasNext(), "iterator should be empty");

        // There should be no exceptions thrown by the printing version.
        printMismatchedNodes(stateA, stateB, 1000);
        printMismatchedNodes(stateA, stateB, 2);
        printMismatchedNodes(stateA, stateB, 1);
        printMismatchedNodes(stateA, stateB, 0);
    }
}
