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

package com.swirlds.common.stream;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.io.SelfSerializable;

/**
 * Maintains a runningHash which is updated each time adding a {@link SelfSerializable} object;
 * Sends object and current runningHash to {@link ObjectStreamConsumer} for serialization if the consumer is not null;
 */
public class ObjectStreamCreator<T extends SerializableHashable> implements AutoCloseable {
	private RunningHash runningHash;
	private ObjectStreamConsumer<T> consumer;
	/**
	 * when it is true, this ObjectStreamCreator STOPs accepting any new object,
	 * updating runningHash, and sending to ObjectStreamConsumer
	 */
	private volatile boolean closed = false;

	public static final String PASS_OBJECT_AFTER_CLOSE = "Passing to ObjectStreamCreator" +
			" while it has been closed: ";

	public ObjectStreamCreator(Hash hash) {
		setRunningHash(hash);
	}

	public ObjectStreamCreator(Hash hash, ObjectStreamConsumer<T> consumer) {
		this(hash);
		setConsumer(consumer);
	}

	/**
	 * set initial runningHash
	 */
	public void setRunningHash(Hash hash) {
		runningHash = new RunningHash(hash);
	}

	/**
	 * set consumer
	 */
	public void setConsumer(ObjectStreamConsumer<T> consumer) {
		this.consumer = consumer;
	}

	/**
	 * (1) calculates Hash for given object;
	 * (2) calculates Hash for the concatenation of current runningHash and the object's Hash;
	 * (3) updates the runningHash with result of (2);
	 * (4) sends this object and new runningHash to the consumer if it is not null
	 *
	 * @param object
	 * @return current running Hash
	 * @throws IllegalStateException
	 * 		when this method is called after the creator has been closed
	 */
	public Hash addObject(T object) throws IllegalStateException {
		if (closed) {
			throw new IllegalStateException(String.format(PASS_OBJECT_AFTER_CLOSE + "%s",
					object));
		}

		if (object.getHash() == null) {
			CryptoFactory.getInstance().digestSync(object);
		}
		// calculate object's Hash
		Hash hashForStream = object.getHash();
		// update runningHash with the object's Hash
		Hash currentRunningHash = runningHash.addAndDigest(hashForStream);
		// send this object and current runningHash to consumer
		if (consumer != null) {
			consumer.addToObjectStream(object, currentRunningHash);
		}
		return currentRunningHash;
	}

	/**
	 * closes current ObjectStreamCreator
	 */
	@Override
	public void close() {
		closed = true;
	}
}
