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
import com.swirlds.common.crypto.SerializableRunningHashable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

import static com.swirlds.logging.LogMarker.OBJECT_STREAM;

/**
 * Accepts a SerializableRunningHashable object each time, calculates and sets its Hash
 * when nextStream is not null, pass this object to the next stream
 *
 * @param <T>
 * 		type of the objects
 */
public class HashCalculatorForStream<T extends SerializableRunningHashable> extends AbstractLinkedObjectStream<T> {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOGGER = LogManager.getLogger();
	/** Used for hashing */
	private final Cryptography cryptography;

	public HashCalculatorForStream() {
		this.cryptography = CryptoFactory.getInstance();
	}

	public HashCalculatorForStream(LinkedObjectStream<T> nextStream) {
		super(nextStream);
		this.cryptography = CryptoFactory.getInstance();
	}

	public HashCalculatorForStream(LinkedObjectStream<T> nextStream, Cryptography cryptography) {
		super(nextStream);
		this.cryptography = Objects.requireNonNull(cryptography);
	}

	public HashCalculatorForStream(Cryptography cryptography) {
		this.cryptography = Objects.requireNonNull(cryptography);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addObject(T t) {
		// calculate and set Hash for this object
		if (Objects.requireNonNull(t).getHash() == null) {
			cryptography.digestSync(t);
		}
		super.addObject(t);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		super.close();
		LOGGER.info(OBJECT_STREAM.getMarker(), "HashCalculatorForStream is closed");
	}
}
