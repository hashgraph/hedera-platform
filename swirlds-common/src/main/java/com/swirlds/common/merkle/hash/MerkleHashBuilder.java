/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.merkle.hash;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.futures.WaitingFuture;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.iterators.MerkleIterator;
import com.swirlds.common.threading.ThreadConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static com.swirlds.common.crypto.engine.CryptoEngine.THREAD_COMPONENT_NAME;
import static com.swirlds.common.merkle.iterators.MerkleIterationOrder.POST_ORDERED_DEPTH_FIRST_RANDOM;
import static com.swirlds.common.merkle.utility.MerkleConstants.MERKLE_DIGEST_TYPE;
import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * This class is responsible for hashing a merkle tree.
 */
public class MerkleHashBuilder {
	private static final Logger log = LogManager.getLogger(MerkleHashBuilder.class);

	private final Executor threadPool;

	private final int cpuThreadCount;

	private final Cryptography cryptography;

	/**
	 * Construct an object which calculates the hash of a merkle tree.
	 *
	 * @param cryptography
	 * 		the {@link Cryptography} implementation to use
	 * @param cpuThreadCount
	 * 		the number of threads to be used for computing hash
	 */
	public MerkleHashBuilder(final Cryptography cryptography, final int cpuThreadCount) {
		this.cryptography = cryptography;
		this.cpuThreadCount = cpuThreadCount;

		final ThreadFactory threadFactory = new ThreadConfiguration()
				.setDaemon(true)
				.setComponent(THREAD_COMPONENT_NAME)
				.setThreadName("merkle hash")
				.setPriority(Thread.NORM_PRIORITY)
				.setExceptionHandler((t, ex) -> {
					log.error(EXCEPTION.getMarker(),
							"Uncaught exception in MerkleHashBuilder thread pool", ex);
				})
				.buildFactory();

		this.threadPool = Executors.newFixedThreadPool(cpuThreadCount, threadFactory);
	}

	/**
	 * Only return nodes that require a hash.
	 */
	private static boolean filter(MerkleNode node) {
		if (node == null) {
			return false;
		}

		if (node.isSelfHashing()) {
			return true;
		}

		return node.getHash() == null;
	}

	/**
	 * Don't bother with subtrees that have already been hashed.
	 */
	private static boolean descendantFilter(final MerkleNode child) {
		if (child.isSelfHashing()) {
			return false;
		}

		return child.getHash() == null;
	}

	/**
	 * Compute the hash of the merkle tree synchronously on the caller's thread.
	 *
	 * @param root
	 * 		the root of the tree to hash
	 * @return The hash of the tree.
	 */
	public Hash digestTreeSync(MerkleNode root) {
		if (root == null) {
			return cryptography.getNullHash(MERKLE_DIGEST_TYPE);
		}

		final Iterator<MerkleNode> iterator = root.treeIterator()
				.setFilter(MerkleHashBuilder::filter)
				.setDescendantFilter(MerkleHashBuilder::descendantFilter);
		hashSubtree(iterator, null);
		return root.getHash();
	}

	/**
	 * Compute the hash of the merkle tree on multiple worker threads.
	 *
	 * @param root
	 * 		the root of the tree to hash
	 * @return a Future which encapsulates the hash of the merkle tree
	 */
	public Future<Hash> digestTreeAsync(MerkleNode root) {
		if (root == null) {
			return new WaitingFuture<>(cryptography.getNullHash(MERKLE_DIGEST_TYPE));
		} else if (root.getHash() != null) {
			return new WaitingFuture<>(root.getHash());
		} else {
			final FutureMerkleHash result = new FutureMerkleHash();
			AtomicInteger activeThreadCount = new AtomicInteger(cpuThreadCount);
			for (int threadIndex = 0; threadIndex < cpuThreadCount; threadIndex++) {
				threadPool.execute(createHashingRunnable(threadIndex, activeThreadCount, result, root));
			}
			return result;
		}
	}

	/**
	 * Create a thread that will attempt to hash the tree starting at the root.
	 *
	 * @param threadId
	 * 		Used to generate a randomized iteration order
	 */
	private Runnable createHashingRunnable(
			final int threadId,
			AtomicInteger activeThreadCount,
			final FutureMerkleHash result,
			final MerkleNode root) {

		return () -> {
			final MerkleIterator<MerkleNode> it = root.treeIterator()
					.setFilter(MerkleHashBuilder::filter)
					.setDescendantFilter(MerkleHashBuilder::descendantFilter);
			if (threadId > 0) {
				// One thread can iterate in-order, all others will use random order.
				it.setOrder(POST_ORDERED_DEPTH_FIRST_RANDOM);
			}

			try {
				hashSubtree(it, activeThreadCount);

				// The last thread to terminate is responsible for setting the future
				int remainingActiveThreads = activeThreadCount.getAndDecrement() - 1;
				if (remainingActiveThreads == 0) {
					result.set(root.getHash());
				}
			} catch (final Throwable t) {
				result.cancelWithException(t);
			}
		};
	}

	/**
	 * The root of a merkle tree.
	 *
	 * @param it
	 * 		An iterator that walks through the tree.
	 * @param activeThreadCount
	 * 		If single threaded then this is null, otherwise contains the number of threads
	 * 		that are actively hashing. Once the active thread count dips below the maximum,
	 * 		this means that one thread has either finished or exploded.
	 */
	private void hashSubtree(final Iterator<MerkleNode> it, final AtomicInteger activeThreadCount) {
		while (it.hasNext()) {
			final MerkleNode node = it.next();
			// Potential optimization: if this node is currently locked, do not wait here. Skip it and continue.
			// This would require a lock object that support the "try lock" paradigm.
			synchronized (node) {

				if (activeThreadCount != null && activeThreadCount.get() != cpuThreadCount) {
					break;
				}

				if (node.getHash() != null) {
					continue;
				}

				if (node.isLeaf()) {
					cryptography.digestSync(node.asLeaf(), MERKLE_DIGEST_TYPE);
				} else {
					cryptography.digestSync(node.asInternal(), MERKLE_DIGEST_TYPE);
				}
			}
		}
	}
}
