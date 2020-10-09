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

package com.swirlds.common.merkle.hash;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.common.crypto.engine.CryptoThreadFactory;
import com.swirlds.common.crypto.engine.ThreadExceptionHandler;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.swirlds.common.crypto.engine.CryptoEngine.LOGM_EXCEPTION;
import static com.swirlds.common.merkle.utility.MerkleConstants.MERKLE_DIGEST_TYPE;

/**
 * This object is responsible for validating hashes of MerkleNodes which were
 * received over an untrusted channel (i.e. a network).
 */
public class MerkleHashValidator implements AutoCloseable {

	private static final Logger log = LogManager.getLogger();

	private final ExecutorService threadPool;

	/**
	 * The number of validations that still need to be performed.
	 */
	protected AtomicInteger outstandingValidations;

	/**
	 * Set to false if an invalid hash is discovered.
	 */
	private volatile boolean isValid;

	protected Exception exception;

	public MerkleHashValidator(final int threadPoolSize) {
		this.threadPool = Executors.newFixedThreadPool(threadPoolSize,
				new CryptoThreadFactory("merkle_hash_validator", new ThreadExceptionHandler(this.getClass()) {
					@Override
					public void uncaughtException(final Thread t, final Throwable ex) {
						log.error(CryptoEngine.LOGM_EXCEPTION,
								"Uncaught exception in MerkleHashValidator thread pool", ex);
					}
				}));
		this.outstandingValidations = new AtomicInteger(0);
		this.isValid = true;
		this.exception = null;
	}


	/**
	 * Validate the hash for a MerkleLeaf.
	 *
	 * @param expectedHash
	 * 		The hash that the MerkleLeaf is expected to have.
	 * @param node
	 * 		The leaf node to hash. Is expected to not have its hash already set via setHash. This is to allow
	 * 		classes that provide custom hash implementations a chance to compute a hash. If the leaf does not
	 * 		compute its own hash, it will asynchronously have its hash set via setHash() as a side effect of
	 * 		the validation process.
	 */
	public void validateAsync(Hash expectedHash, MerkleLeaf node) {
		outstandingValidations.getAndIncrement();
		Runnable runnable = () -> {
			try {
				if (!isValid) {
					return;
				}
				Hash nodeHash;
				if (node == null) {
					nodeHash = CryptoFactory.getInstance().getNullHash(MERKLE_DIGEST_TYPE);
				} else {
					nodeHash = node.getHash(); // Some nodes compute their own hash
					if (nodeHash == null) {
						CryptoFactory.getInstance().digestSync(node);
						nodeHash = node.getHash();
					}
				}
				if (expectedHash.equals(nodeHash)) {
					outstandingValidations.getAndDecrement();
				} else {
					isValid = false;
					log.error(LOGM_EXCEPTION, "Invalid Hash detected for node: " + node.getClass());
				}
			} catch (Exception e) {
				exception = e;
				isValid = false;
			}
		};
		threadPool.execute(runnable);
	}

	/**
	 * Validate the hash of a MerkleInternal.
	 *
	 * @param expectedHash
	 * 		The hash that the MerkleInternal is expected to have.
	 * @param node
	 * 		The internal node that is being validated. This node will have its hash set via setHash()
	 * 		as a side effect of the validation process.
	 * @param childHashes
	 * 		The internal node may not yet have its children set. This list contains the hashes of those
	 * 		future children.
	 */
	public void validateAsync(Hash expectedHash, MerkleInternal node, List<Hash> childHashes) {
		outstandingValidations.getAndIncrement();
		Runnable runnable = () -> {
			if (!isValid) {
				return;
			}
			try {
				CryptoFactory.getInstance().digestSync(node, childHashes);
				if (expectedHash.equals(node.getHash())) {
					outstandingValidations.getAndDecrement();
				} else {
					isValid = false;
					log.error(LOGM_EXCEPTION, "Invalid Hash detected for node: " + node.getClass());
				}
			} catch (Exception e) {
				this.exception = e;
				isValid = false;
			}
		};
		threadPool.execute(runnable);
	}

	/**
	 * Validate that two hashes match. This is done on the caller's thread since a simple comparison is very fast.
	 */
	public void validate(Hash expectedHash, Hash givenHash) {
		if (!expectedHash.equals(givenHash)) {
			isValid = false;
		}
	}

	/**
	 * Hashes are asynchronously validated. Returns true if no invalid hashes have yet been encountered.
	 */
	public boolean isValidSoFar() {
		return isValid;
	}

	/**
	 * Blocks until all currently submitted hashes have been checked. Returns true if all those hashes are valid.
	 *
	 * This method should only be called once all hashes requiring validation have been submitted.
	 */
	public boolean isValid() throws ExecutionException {
		while (outstandingValidations.get() > 0 && isValid) {
			Thread.yield();
		}
		if (exception != null) {
			throw new ExecutionException(exception);
		}
		return isValid;
	}

	public boolean isValid(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException {
		long timeoutDuration = TimeUnit.MILLISECONDS.convert(timeout, unit);
		long timeoutTime = System.currentTimeMillis() + timeoutDuration;
		while (outstandingValidations.get() > 0 && isValid) {
			if (System.currentTimeMillis() > timeoutTime) {
				throw new TimeoutException();
			}
			Thread.yield();
		}
		if (exception != null) {
			throw new ExecutionException(exception);
		}
		return isValid;
	}

	@Override
	public void close() {
		threadPool.shutdown();
	}

}
