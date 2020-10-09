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

package com.swirlds.platform;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.stream.ObjectStreamCreator;
import com.swirlds.common.stream.TimestampStreamFileWriter;
import com.swirlds.platform.event.EventUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static com.swirlds.common.Constants.SEC_TO_MS;
import static com.swirlds.logging.LogMarker.EVENT_STREAM;
import static com.swirlds.logging.LogMarker.OBJECT_STREAM_DETAIL;
import static com.swirlds.logging.LogMarker.RECONNECT;

public class RunningHashCalculator {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/**
	 * Consensus events for calculating RunningHash;
	 * A running hash of hashes of all consensus events have there been throughout all of history, up
	 * through the round received that the SignedState represents, is stored in the SignedState;
	 * when eventStreaming is enabled, a running hash of hashes of all consensus events have been written
	 * is saved in the beginning of each new event stream file.
	 */
	private BlockingQueue<EventImpl> forRunningHash;
	/** A thread that calculates RunningHash for consensus events */
	private StoppableThread threadCalcRunningHash;
	/** calculates RunningHash for consensus events */
	private ObjectStreamCreator<EventImpl> objectStreamCreator;
	/** serializes consensus events to event stream files */
	private TimestampStreamFileWriter<EventImpl> consumer;
	/** initialHash loaded from signed state */
	private Hash initialHash = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);
	/**
	 * when event streaming is started after reconnect, or at state recovering, startWriteAtCompleteWindow should be set
	 * to be true;
	 * when event streaming is started after restart, it should be set to be false
	 */
	private boolean startWriteAtCompleteWindow = false;
	/**
	 * When we freeze the platform, the last event to be written to EventStream file is the last event in the freeze
	 * round. The freeze round is defined as the first round with a consensus timestamp after the start of the freeze
	 * period.
	 */
	private boolean freezePeriodStarted = false;

	/** the directory to which event stream files are written */
	private String eventStreamDir;

	private AbstractPlatform platform;


	public RunningHashCalculator(final AbstractPlatform platform) {
		this.platform = platform;
		forRunningHash = new ArrayBlockingQueue<>(Settings.eventStreamQueueCapacity);
	}

	/**
	 * @param eventStreamDir
	 */
	void setEventStreamDir(final String eventStreamDir) {
		directoryAssurance(eventStreamDir);
		this.eventStreamDir = eventStreamDir;
	}

	/**
	 * initialize ObjectStreamCreator and set initial RunningHash;
	 * initialize and start TimestampStreamFileWriter if event streaming is enabled
	 */
	void startCalcRunningHashThread() {
		if (Settings.enableEventStreaming) {
			//initialize and start TimestampStreamFileWriter, set directory and set startWriteAtCompleteWindow;
			consumer = new TimestampStreamFileWriter<>(initialHash, eventStreamDir,
					Settings.eventsLogPeriod * SEC_TO_MS,
					this.platform,
					startWriteAtCompleteWindow,
					Settings.eventStreamQueueCapacity);
		}
		objectStreamCreator = new ObjectStreamCreator<>(initialHash, consumer);
		threadCalcRunningHash = new StoppableThread("threadCalcRunningHash",
				this::calcRunningHash, platform.getSelfId());
		threadCalcRunningHash.start();
		log.info(EVENT_STREAM.getMarker(), "threadCalcRunningHash started. initialHash: {}",
				() -> initialHash);
	}

	/**
	 * calcRunningHash is repeatedly called by the threadCalRunningHash thread.
	 * Each time, it takes one event from forRunningHash queue
	 * streamCreator updates RunningHash,
	 * and sends this event to consumer which serializes this event to file if eventStreaming is enabled
	 *
	 * @throws InterruptedException
	 */
	private void calcRunningHash() throws InterruptedException {
		EventImpl event = forRunningHash.take();

		// if freeze period is started, we don't update runningHash,
		// don't put events to forCons queue, don't write events to stream file
		if (freezePeriodStarted) {
			return;
		}

		if (Settings.enableEventStreaming) {
			// When we freeze the platform, the last event to be written to EventStream file is the last event in the
			// freeze round.
			// The freeze round is defined as the first round with a consensus timestamp after freeze period starts
			//
			// if this event is the last Event to be written before restarting,
			// we should close file and generate signature after writing this event
			if (event.isLastInRoundReceived() && FreezeManager.isInFreezePeriod(event.getConsensusTimestamp())) {
				freezePeriodStarted = true;
				event.setLastOneBeforeShutdown(true);
				log.info(EVENT_STREAM.getMarker(),
						"the last Event to be written into EventStream file before restarting: {}, round: {}, " +
								"consensusOrder: {}",
						event::getCreatorSeqPair,
						event::getRoundReceived,
						event::getConsensusOrder);
			}

			// event.setLastOneBeforeShutdown() might also be called during state recover
			if (event.isLastOneBeforeShutdown()) {
				// if this event is the last event, we send a close notification to the consumer
				// to let the consumer know it should close and sign file after finish writing all workloads
				// in its working queue.
				// we should close consumer before putting this last event,
				// because otherwise the consumer might have consumed the last event before it get the
				// notification, thus never close and sign the last file.
				consumer.close();
			}
		}

		// update runningHash, send this object and new runningHash to the consumer if eventStreaming is enabled
		Hash runningHash = objectStreamCreator.addObject(event);

		log.info(OBJECT_STREAM_DETAIL.getMarker(),
				"RunningHash after adding event {} : {}",
				() -> EventUtils.toShortString(event), () -> runningHash);
		// set runningHash for this consensus event
		event.setRunningHash(runningHash);
		// put this consensus event to forCons queue to be handled by doCons()
		// this may block until the queue isn't full
		platform.getEventFlow().forConsPut(event);

		if (event.isLastOneBeforeShutdown()) {
			// if this event is the last event, we close the objectStreamCreator,
			objectStreamCreator.close();
		}
	}

	/**
	 * set startWriteAtCompleteWindow:
	 * it should be set to be true after reconnect, or at state recovering;
	 * it should be set to be false at restart
	 *
	 * @param startWriteAtCompleteWindow
	 */
	void setStartWriteAtCompleteWindow(boolean startWriteAtCompleteWindow) {
		this.startWriteAtCompleteWindow = startWriteAtCompleteWindow;
		log.info(EVENT_STREAM.getMarker(),
				"RunningHashCalculator::setStartWriteAtCompleteWindow: {}", () -> startWriteAtCompleteWindow);
	}

	/**
	 * set initialHash and isReconnect after loading from signed state
	 *
	 * @param initialHash
	 */
	void setInitialHash(final Hash initialHash) {
		this.initialHash = initialHash;
		log.info(EVENT_STREAM.getMarker(),
				"RunningHashCalculator::setInitialHash: {}", () -> initialHash);
	}

	/** Creates parent if necessary */
	private static void directoryAssurance(String directory) {
		File dir = new File(directory);
		if (!dir.exists()) dir.mkdirs();
	}

	int getForStreamSize() {
		return forRunningHash == null ? 0 : forRunningHash.size();
	}

	/**
	 * put consensus events into forRunningHash queue
	 *
	 * This method is called by Hashgraph when it first finds consensus for an event. If the queue is full,
	 * then this will block until it isn't full, which will block whatever thread called Hashgraph.addEvent.
	 *
	 * @param event
	 */
	void forRunningHashPut(final EventImpl event) {
		try {
			// put this consensus event into the queue for calculating running Hash
			// later this event will be put into forCons queue
			forRunningHash.put(event);
		} catch (InterruptedException e) {
			log.info(RECONNECT.getMarker(), "forRunningHashPut interrupted");
		}
	}

	/**
	 * this method is called when the node falls behind,
	 * clears the queue, stops threadCalcRunningHash and streamFileWriter.
	 */
	void stopAndClear() {
		forRunningHash.clear();
		threadCalcRunningHash.stop();
		if (consumer != null) {
			consumer.stopAndClear();
		}
		log.info(EVENT_STREAM.getMarker(), "threadCalcRunningHash stopped");
	}
}
