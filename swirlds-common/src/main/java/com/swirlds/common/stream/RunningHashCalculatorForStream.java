/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.common.stream;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SerializableRunningHashable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.swirlds.logging.LogMarker.OBJECT_STREAM;

/**
 * Accepts a SerializableRunningHashable object each time, calculates and sets its runningHash
 * when nextStream is not null, pass this object to the next stream
 *
 * @param <T>
 * 		type of the objects
 */
public class RunningHashCalculatorForStream<T extends SerializableRunningHashable>
		extends AbstractLinkedObjectStream<T> {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOGGER = LogManager.getLogger();
	/** current running Hash */
	private Hash runningHash;
	/** Used for hashing */
	private final Cryptography cryptography;

	public RunningHashCalculatorForStream() {
		this.cryptography = CryptoFactory.getInstance();
	}

	public RunningHashCalculatorForStream(LinkedObjectStream<T> nextStream) {
		super(nextStream);
		this.cryptography = CryptoFactory.getInstance();
	}

	public RunningHashCalculatorForStream(Cryptography cryptography) {
		this.cryptography = cryptography;
	}

	public RunningHashCalculatorForStream(LinkedObjectStream<T> nextStream, Cryptography cryptography) {
		super(nextStream);
		this.cryptography = cryptography;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setRunningHash(final Hash hash) {
		this.runningHash = hash;
		super.setRunningHash(hash);
		LOGGER.info(OBJECT_STREAM.getMarker(), "RunningHashCalculatorForStream :: setRunningHash: {}",
				() -> hash);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addObject(T t) {
		// if Hash of this object is not set yet, calculates and sets its Hash
		if (t.getHash() == null) {
			cryptography.digestSync(t);
		}

		final Hash newHashToAdd = t.getHash();
		// calculates and updates runningHash
		runningHash = cryptography.calcRunningHash(runningHash, newHashToAdd, DigestType.SHA_384);
		t.getRunningHash().setHash(runningHash);
		super.addObject(t);
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		super.close();
		LOGGER.info(OBJECT_STREAM.getMarker(), "RunningHashCalculatorForStream is closed");
	}

	public Hash getRunningHash() {
		return runningHash;
	}
}
