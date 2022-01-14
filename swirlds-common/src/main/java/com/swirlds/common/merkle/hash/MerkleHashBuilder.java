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

package com.swirlds.common.merkle.hash;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.engine.CryptoThreadFactory;
import com.swirlds.common.crypto.engine.ThreadExceptionHandler;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.IllegalChildHashException;
import com.swirlds.common.merkle.iterators.MerkleHashIterator;
import com.swirlds.common.merkle.iterators.MerkleRandomHashIterator;
import com.swirlds.logging.LogMarker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.swirlds.common.merkle.utility.MerkleConstants.MERKLE_DIGEST_TYPE;

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
		this.threadPool = Executors.newFixedThreadPool(cpuThreadCount,
				new CryptoThreadFactory("merkle_hash_builder", new ThreadExceptionHandler(this.getClass()) {
					@Override
					public void uncaughtException(final Thread t, final Throwable ex) {
						log.error(LogMarker.EXCEPTION.getMarker(),
								"Uncaught exception in MerkleHashBuilder thread pool", ex);
					}
				}));
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

		final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

		try {
			hashSubtree(new MerkleHashIterator(root), null);
			return root.getHash();
		} catch (IllegalChildHashException ex) {
			ex.setStackTrace(stackTrace);

			log.error(LogMarker.EXCEPTION.getMarker(), ex.getMessage(), ex);
			throw new IllegalChildHashException(ex);
		}
	}

	/**
	 * Compute the hash of the merkle tree on multiple worker threads.
	 *
	 * @param root
	 * 		the root of the tree to hash
	 * @return a Future which encapsulates the hash of the merkle tree
	 */
	public FutureMerkleHash digestTreeAsync(MerkleNode root) {
		final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		FutureMerkleHash result = new FutureMerkleHash();

		if (root == null) {
			result.set(cryptography.getNullHash(MERKLE_DIGEST_TYPE));
		} else {
			if (root.getHash() != null) {
				// Creating threads is expensive. If the hash is already known then just immediately return it.
				result.set(root.getHash());
			} else {
				AtomicInteger activeThreadCount = new AtomicInteger(cpuThreadCount);
				for (int threadIndex = 0; threadIndex < cpuThreadCount; threadIndex++) {
					threadPool.execute(createHashingRunnable(threadIndex, activeThreadCount, result, root, stackTrace));
				}
			}
		}

		return result;
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
			final MerkleNode root,
			final StackTraceElement[] stackTrace) {

		return () -> {

			Iterator<MerkleNode> it;
			if (threadId == 0) {
				// One thread can iterate in-order
				// This iterator doesn't have to randomize iteration order and is therefore faster
				it = new MerkleHashIterator(root);
			} else {
				it = new MerkleRandomHashIterator(root, threadId);
			}

			try {
				hashSubtree(it, activeThreadCount);

				// The last thread to terminate is responsible for setting the future
				int remainingActiveThreads = activeThreadCount.getAndDecrement() - 1;
				if (remainingActiveThreads == 0) {
					result.set(root.getHash());
				}
			} catch (IllegalChildHashException ex) {
				ex.setStackTrace(stackTrace);

				log.error(LogMarker.EXCEPTION.getMarker(), ex.getMessage(), ex);
				throw new IllegalChildHashException(ex);
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
	private void hashSubtree(Iterator<MerkleNode> it, AtomicInteger activeThreadCount) {
		while (it.hasNext()) {
			MerkleNode node = it.next();
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
