/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.stream;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.crypto.SerializableHashable;
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
public class HashCalculatorForStream<T extends RunningHashable & SerializableHashable>
		extends AbstractLinkedObjectStream<T> {

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
