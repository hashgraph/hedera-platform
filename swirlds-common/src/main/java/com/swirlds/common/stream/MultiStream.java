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

package com.swirlds.common.stream;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHashable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.swirlds.logging.LogMarker.OBJECT_STREAM;

/**
 * A MultiStream instance might have multiple nextStreams.
 * It accepts a SerializableRunningHashable object each time, and sends it to each of its nextStreams
 *
 * @param <T>
 * 		type of the objects
 */
public class MultiStream<T extends RunningHashable> implements LinkedObjectStream<T> {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOGGER = LogManager.getLogger();

	/**
	 * message of the exception thrown when setting a nextStream to be null
	 */
	public static final String NEXT_STREAM_NULL = "MultiStream should not have null nextStream";

	/**
	 * nextStreams should have at least this many elements
	 */
	private static final int NEXT_STREAMS_MIN_SIZE = 1;

	/**
	 * message of the exception thrown when nextStreams has less than two elements
	 */
	public static final String NOT_ENOUGH_NEXT_STREAMS = String.format("MultiStream should have at least %d " +
			"nextStreams", NEXT_STREAMS_MIN_SIZE);

	/**
	 * a list of LinkedObjectStreams which receives objects from this multiStream
	 */
	private List<LinkedObjectStream<T>> nextStreams;

	public MultiStream(List<LinkedObjectStream<T>> nextStreams) {
		if (nextStreams == null || nextStreams.size() < NEXT_STREAMS_MIN_SIZE) {
			throw new IllegalArgumentException(NOT_ENOUGH_NEXT_STREAMS);
		}

		for (LinkedObjectStream<T> nextStream : nextStreams) {
			if (nextStream == null) {
				throw new IllegalArgumentException(NEXT_STREAM_NULL);
			}
		}
		this.nextStreams = nextStreams;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setRunningHash(final Hash hash) {
		for (LinkedObjectStream<T> nextStream : nextStreams) {
			nextStream.setRunningHash(hash);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addObject(T t) {
		for (LinkedObjectStream<T> nextStream : nextStreams) {
			nextStream.addObject(t);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		for (LinkedObjectStream<T> nextStream : nextStreams) {
			nextStream.clear();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		for (LinkedObjectStream<T> nextStream : nextStreams) {
			nextStream.close();
		}
		LOGGER.info(OBJECT_STREAM.getMarker(), "MultiStream is closed");
	}
}

