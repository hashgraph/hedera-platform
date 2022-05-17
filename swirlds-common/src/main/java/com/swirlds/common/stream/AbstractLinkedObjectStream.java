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

package com.swirlds.common.stream;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SerializableRunningHashable;

import java.util.Objects;

/**
 * This abstract class implements boiler plate functionality for a {@link LinkedObjectStream}.
 *
 * @param <T>
 * 		type of the objects to be processed by this stream
 */
public abstract class AbstractLinkedObjectStream<T extends SerializableRunningHashable>
		implements LinkedObjectStream<T> {
	private LinkedObjectStream<T> nextStream;

	protected AbstractLinkedObjectStream() {
	}

	protected AbstractLinkedObjectStream(final LinkedObjectStream<T> nextStream) {
		this();
		this.nextStream = nextStream;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setRunningHash(final Hash hash) {
		if (nextStream != null) {
			nextStream.setRunningHash(hash);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addObject(T t) {
		if (nextStream != null) {
			nextStream.addObject(Objects.requireNonNull(t));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		if (nextStream != null) {
			nextStream.clear();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		if (nextStream != null) {
			nextStream.close();
		}
	}
}
