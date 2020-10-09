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

package com.swirlds.fcmap.internal;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.FCMKey;
import com.swirlds.common.FCMValue;

import java.util.concurrent.atomic.AtomicInteger;

public interface FCMNode<K extends FCMKey, V extends FCMValue>
		extends MerkleInternal,
		FastCopyable<FCMNode<K, V>> {

	/**
	 * Empty hash value
	 */
	Hash EMPTY_HASH = new ImmutableHash(new byte[48]);

	FCMInternalNode<K, V> getParent();

	void setParent(final FCMInternalNode<K, V> parent);

	FCMNode<K, V> getLeftChild();

	FCMNode<K, V> getRightChild();

	boolean isFCMLeaf();

	boolean isPathUnique();

	void nullifyHashPath();

	BalanceInfo getBalanceInfo();

	String getMemoryReference();
}
