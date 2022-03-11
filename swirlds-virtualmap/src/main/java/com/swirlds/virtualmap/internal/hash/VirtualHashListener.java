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

package com.swirlds.virtualmap.internal.hash;

import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;

/**
 * Listens to various events that occur during the hashing process.
 */
public interface VirtualHashListener<K extends VirtualKey<? super K>, V extends VirtualValue> {
	/**
	 * Called when starting a new fresh hash operation.
	 */
	default void onHashingStarted() {
	}

	/**
	 * Called when a batch (that is, a sub-tree) is about to be hashed. Batches are
	 * hashed sequentially, one at a time. You ar guaranteed that {@link #onHashingStarted()}
	 * will be called before this method, and that {@link #onBatchCompleted()} will be called
	 * before another invocation of this method.
	 */
	default void onBatchStarted() {
	}

	/**
	 * Called when hashing a rank within the batch.
	 * It may be that multiple batches work on the same rank, just a different subset of it.
	 * This will be called once for each combination of batch and rank. The ranks are called
	 * from bottom-to-top (that is, from the deepest rank to the lowest rank). This is
	 * called between {@link #onBatchStarted()} and {@link #onBatchCompleted()} methods.
	 */
	default void onRankStarted() {
	}

	/**
	 * Called after each internal node on a rank is hashed. This is called between
	 * {@link #onRankStarted()} and {@link #onRankCompleted()}. Each call within the rank
	 * will send internal nodes in <strong>ascending path order</strong>.  There is no guarantee
	 * 	 * on the relative order between batches.
	 *
	 * @param internal
	 * 		A non-null internal record representing the hashed internal node.
	 */
	default void onInternalHashed(VirtualInternalRecord internal) {
	}

	/**
	 * Called after each leaf node on a rank is hashed. This is called between
	 * {@link #onRankStarted()} and {@link #onRankCompleted()}. Each call within the rank
	 * will send leaf nodes in <strong>ascending path order</strong>. There is no guarantee
	 * on the relative order between batches.
	 *
	 * @param leaf
	 * 		A non-null leaf record representing the hashed leaf.
	 */
	default void onLeafHashed(VirtualLeafRecord<K, V> leaf) {
	}

	/**
	 * Called when processing a rank within a batch has completed.
	 */
	default void onRankCompleted() {
	}

	/**
	 * Called when processing a batch has completed.
	 */
	default void onBatchCompleted() {
	}

	/**
	 * Called when all hashing has completed.
	 */
	default void onHashingCompleted() {
	}
}
