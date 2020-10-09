/*
 * (c) 2016-2020 Swirlds, Inc.
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

package com.swirlds.common.merkle.io;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Container for holding data gathered during the deserialization MerkleInternal.
 */
public class PartiallyConstructedMerkleInternal {

	protected MerkleInternal node;
	int version;
	protected int expectedChildCount;

	protected List<MerkleNode> children;

	public PartiallyConstructedMerkleInternal(MerkleInternal node, int version, int expectedChildCount) {
		this.node = node;
		this.version = version;
		this.expectedChildCount = expectedChildCount;
		this.children = new LinkedList<>();
	}

	public boolean hasAllChildren() {
		return expectedChildCount == children.size();
	}

	public void addChild(MerkleNode child) {
		children.add(child);
	}

	public void finishConstruction() {
		node.addDeserializedChildren(children, version);
	}
}
