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

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.system.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.function.Predicate;

import static com.swirlds.common.utility.Units.SECONDS_TO_MILLISECONDS;
import static com.swirlds.logging.LogMarker.EVENT_STREAM;

/**
 * This class is used for generating event stream files when enableEventStreaming is true,
 * and for calculating runningHash for consensus Events.
 */
public class EventStreamManager<T extends StreamAligned & Timestamped & RunningHashable & SerializableHashable> {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOGGER = LogManager.getLogger();

	/**
	 * receives consensus events from ConsensusRoundHandler.addEvent(), then passes to hashQueueThread and
	 * writeQueueThread
	 */
	private final MultiStream<T> multiStream;

	/** receives consensus events from multiStream, then passes to hashCalculator */
	private QueueThreadObjectStream<T> hashQueueThread;
	/**
	 * receives consensus events from hashQueueThread, calculates this event's Hash, then passes to
	 * runningHashCalculator
	 */
	private HashCalculatorForStream<T> hashCalculator;

	/** receives consensus events from multiStream, then passes to streamFileWriter */
	private QueueThreadObjectStream<T> writeQueueThread;
	/** receives consensus events from writeQueueThread, serializes consensus events to event stream files */
	private TimestampStreamFileWriter<T> streamFileWriter;

	/** initialHash loaded from signed state */
	private Hash initialHash = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

	/**
	 * When we freeze the platform, the last event to be written to EventStream file is the last event in the freeze
	 * round. The freeze round is defined as the first round with a consensus timestamp after the start of the freeze
	 * period.
	 */
	private volatile boolean freezePeriodStarted = false;

	/**
	 * check whether this event is the last event before restart
	 */
	private final Predicate<T> isLastEventInFreezeCheck;

	/**
	 * @param platform
	 * 		the platform which initializes this EventStreamManager instance
	 * @param nodeName
	 * 		name of this node
	 * @param enableEventStreaming
	 * 		whether write event stream files or not
	 * @param eventsLogDir
	 * 		eventStream files will be generated in this directory
	 * @param eventsLogPeriod
	 * 		period of generating eventStream file
	 * @param eventStreamQueueCapacity
	 * 		capacity of the blockingQueue from which we take events and write to EventStream files
	 * @param isLastEventInFreezeCheck
	 * 		a predicate which checks whether this event is the last event before restart
	 * @throws NoSuchAlgorithmException
	 * 		is thrown when fails to get required MessageDigest instance
	 * @throws IOException
	 * 		is thrown when fails to create directory for event streaming
	 */
	public EventStreamManager(final Platform platform,
			final String nodeName, final boolean enableEventStreaming,
			final String eventsLogDir,
			final long eventsLogPeriod,
			final int eventStreamQueueCapacity,
			final Predicate<T> isLastEventInFreezeCheck) throws NoSuchAlgorithmException, IOException {
		if (enableEventStreaming) {
			// the directory to which event stream files are written
			final String eventStreamDir = eventsLogDir + "/events_" + nodeName;
			Files.createDirectories(Paths.get(eventStreamDir));

			streamFileWriter = new TimestampStreamFileWriter<>(
					eventStreamDir,
					eventsLogPeriod * SECONDS_TO_MILLISECONDS,
					platform,
					/** when event streaming is started after reconnect, or at state recovering,
					 * startWriteAtCompleteWindow should be set to be true; when event streaming is started after
					 * restart, it should be set to be false */
					false,
					EventStreamType.EVENT);

			writeQueueThread = new QueueThreadObjectStreamConfiguration<T>()
					.setNodeId(platform.getSelfId().getId())
					.setComponent("event-stream")
					.setThreadName("write-queue")
					.setForwardTo(streamFileWriter)
					.build();
			writeQueueThread.start();
		}

		// receives consensus events from hashCalculator, calculates and set runningHash for this event
		final RunningHashCalculatorForStream<T> runningHashCalculator = new RunningHashCalculatorForStream<>();
		hashCalculator = new HashCalculatorForStream<>(runningHashCalculator);
		hashQueueThread = new QueueThreadObjectStreamConfiguration<T>()
				.setNodeId(platform.getSelfId().getId())
				.setComponent("event-stream")
				.setThreadName("hash-queue")
				.setForwardTo(hashCalculator)
				.build();
		hashQueueThread.start();

		multiStream = new MultiStream<>(
				enableEventStreaming ? List.of(hashQueueThread, writeQueueThread) : List.of(hashQueueThread));
		multiStream.setRunningHash(initialHash);

		this.isLastEventInFreezeCheck = isLastEventInFreezeCheck;
	}

	/**
	 * @param multiStream
	 * 		the instance which receives consensus events from ConsensusRoundHandler, then passes to nextStreams
	 * @param isLastEventInFreezeCheck
	 * 		a predicate which checks whether this event is the last event before restart
	 */
	public EventStreamManager(final MultiStream<T> multiStream,
			final Predicate<T> isLastEventInFreezeCheck) {
		this.multiStream = multiStream;
		multiStream.setRunningHash(initialHash);
		this.isLastEventInFreezeCheck = isLastEventInFreezeCheck;
	}


	public void addEvents(final List<T> events) {
		events.forEach(this::addEvent);
	}

	/**
	 * receives a consensus event from ConsensusRoundHandler each time,
	 * sends it to multiStream which then sends to two queueThread for calculating runningHash and writing to file
	 *
	 * @param event
	 * 		the consensus event to be added
	 */
	public void addEvent(final T event) {
		if (!freezePeriodStarted) {
			multiStream.addObject(event);
			if (isLastEventInFreezeCheck.test(event)) {
				freezePeriodStarted = true;
				LOGGER.info(EVENT_STREAM.getMarker(),
						"ConsensusTimestamp of the last Event to be written into file before restarting: " +
								"{}", event::getTimestamp);
				multiStream.close();
			}
		} else {
			LOGGER.warn(EVENT_STREAM.getMarker(), "Event {} dropped after freezePeriodStarted!", event.getTimestamp());
		}
	}

	/**
	 * sets initialHash after loading from signed state
	 *
	 * @param initialHash
	 * 		current runningHash of all consensus events
	 */
	public void setInitialHash(final Hash initialHash) {
		this.initialHash = initialHash;
		LOGGER.info(EVENT_STREAM.getMarker(),
				"EventStreamManager::setInitialHash: {}", () -> initialHash);
		multiStream.setRunningHash(initialHash);
	}

	/**
	 * sets startWriteAtCompleteWindow:
	 * it should be set to be true after reconnect, or at state recovering;
	 * it should be set to be false at restart
	 *
	 * @param startWriteAtCompleteWindow
	 * 		whether the writer should not write until the first complete window
	 */
	public void setStartWriteAtCompleteWindow(final boolean startWriteAtCompleteWindow) {
		if (streamFileWriter != null) {
			streamFileWriter.setStartWriteAtCompleteWindow(startWriteAtCompleteWindow);
		}
	}

	/**
	 * returns current size of working queue for calculating hash and runningHash
	 *
	 * @return current size of working queue for calculating hash and runningHash
	 */
	public int getHashQueueSize() {
		return hashQueueThread.getQueue().size();
	}

	/**
	 * returns current size of working queue for writing to event stream files
	 *
	 * @return current size of working queue for writing to event stream files
	 */
	public int getEventStreamingQueueSize() {
		return writeQueueThread == null ? 0 : writeQueueThread.getQueue().size();
	}

	/**
	 * for unit testing
	 *
	 * @return current multiStream instance
	 */
	public MultiStream<T> getMultiStream() {
		return multiStream;
	}

	/**
	 * for unit testing
	 *
	 * @return current TimestampStreamFileWriter instance
	 */
	public TimestampStreamFileWriter<T> getStreamFileWriter() {
		return streamFileWriter;
	}

	/**
	 * for unit testing
	 *
	 * @return current HashCalculatorForStream instance
	 */
	public HashCalculatorForStream<T> getHashCalculator() {
		return hashCalculator;
	}

	/**
	 * for unit testing
	 *
	 * @return whether freeze period has started
	 */
	public boolean getFreezePeriodStarted() {
		return freezePeriodStarted;
	}

	/**
	 * for unit testing
	 *
	 * @return a copy of initialHash
	 */
	public Hash getInitialHash() {
		return new Hash(initialHash);
	}
}
