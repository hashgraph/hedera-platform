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

import com.swirlds.common.NodeId;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.events.ConsensusEvent;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.stream.ObjectStreamUtilities;
import com.swirlds.platform.event.EventUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.swirlds.blob.internal.Utilities.hex;
import static com.swirlds.common.stream.ObjectStreamUtilities.isStreamFile;
import static com.swirlds.common.stream.TimestampStreamFileWriter.OBJECT_STREAM_FILE_EXTENSION;
import static com.swirlds.logging.LogMarker.EVENT_PARSER;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.platform.StreamUtilities.parseEventStream;

/**
 * Parse event stream files and playback event on given SwirldState object
 *
 * Running a different thread, parsing event files from given directory.
 * Searching event whose consensus timestamp following in the range of
 * start timestamp (exclusive) and end timestamp (inclusive), i.e., (startTimestamp, endTimestamp]
 */
class StreamEventParser extends Thread {
	public static final String OLD_EVENT_STREAM_FILE_EXTENSION = ".evts";

	private static final Logger log = LogManager.getLogger();

	private LinkedBlockingQueue<EventImpl> events = new LinkedBlockingQueue<>();

	private boolean isParsingDone = false;

	private final static int POLL_WAIT = 5000;
	static final String MD_ALGORITHM = "SHA-384";

	private String fileDir;
	private Instant startTimestamp;
	private Instant endTimestamp;
	private long eventsCounter;
	private byte[] prevFileHash;
	private EventImpl prevParsedEvent;

	private StreamUtilities.EventSeqChecker eventSeqChecker;

	private MessageDigest md;

	/** the round number of last recover state with valid new user transactions */
	private volatile long lastRecoverRoundWithNewUserTran;

	StreamEventParser(String fileDir, Instant startTimestamp, Instant endTimestamp,
			long roundOfLoadedSignedState, NodeId nodeId) {
		this.fileDir = fileDir;
		this.startTimestamp = startTimestamp;
		this.endTimestamp = endTimestamp;
		this.lastRecoverRoundWithNewUserTran = roundOfLoadedSignedState;

		try {
			md = MessageDigest.getInstance(MD_ALGORITHM);
		} catch (NoSuchAlgorithmException e) {
			log.error(EXCEPTION.getMarker(), "Got error in StreamEventParser {}", e);
		}
		eventSeqChecker = new StreamUtilities.EventSeqChecker();
	}

	public EventImpl getNextEvent() {
		try {
			return events.poll(POLL_WAIT, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			log.info(EXCEPTION.getMarker(), "Unexpected", e);
			return null;
		}
	}

	public long getEventsCounter() {
		return eventsCounter;
	}

	/** whether we got all event */
	public boolean noMoreEvents() {
		return isParsingDone && events.size() == 0;
	}

	/**
	 * Parsing event stream files from a specific folder with a search timestamp,
	 * then playback transactions inside those events
	 */
	private void eventPlayback() {
		parseEventFolder(this.fileDir, this::handleEvent);
		handleEvent(null); //push the last prevParsedEvent to queue
		isParsingDone = true;
		log.info(EVENT_PARSER.getMarker(), "Recovered {} event from stream file",
				() -> eventsCounter);
	}

	/**
	 * Parsing event stream files from a specific folder with a search timestamp
	 *
	 * @param fileDir
	 * 		directory where event files are stored
	 * @param eventHandler
	 * 		call back function for handling parsed event object
	 */
	private void parseEventFolder(String fileDir,
			Function<EventImpl, Boolean> eventHandler) {
		if (fileDir != null) {
			log.info(EVENT_PARSER.getMarker(), "Loading event file from path {} ",
					() -> fileDir);

			// Only get .soc or .evts files from the directory
			File folder = new File(fileDir);
			File[] files = folder.listFiles((dir, name) ->
					isStreamFile(name) || isOldEventStreamFile(name));
			log.info(EVENT_PARSER.getMarker(), "Files before sorting {}",
					() -> Arrays.toString(files));
			//sort file by its name and timestamp order
			Arrays.sort(files);

			boolean parseResult = true;
			for (int i = 0; i < files.length; i++) {
				String fullPathName = files[i].getAbsolutePath();

				Instant firstTimestamp = eventFileName2Instant(files[i].getName());

				if (firstTimestamp.compareTo(endTimestamp) > 0) {
					log.info(EVENT_PARSER.getMarker(),
							"Search event file ended because file timestamp {} is after endTimestamp {}",
							() -> firstTimestamp, () -> endTimestamp);
					break;
				}
				if (i < files.length - 1) {
					//if this is not the last file, we can compare timestamp from two file
					Instant secondTimestamp = eventFileName2Instant(files[i + 1].getName());

					// if  startTimestamp < secondTimestamp
					if (startTimestamp.compareTo(secondTimestamp) < 0) {
						parseResult = parseEventFile(fullPathName, eventHandler);
					} else {
						log.info(EVENT_PARSER.getMarker(), " Skip file {}: first {}  start {} second {}",
								() -> fullPathName,
								() -> firstTimestamp,
								() -> startTimestamp,
								() -> secondTimestamp);
					}
				} else {
					// last file will always be opened and parsed since we could not know
					// what is the timestamp of the last event within the file
					parseResult = parseEventFile(fullPathName, eventHandler);
				}

				if (!parseResult) {
					log.error(EXCEPTION.getMarker(), "Experienced error during parsing file {}", fullPathName);
					break;
				}
			}//for
		}
	}

	/**
	 * Parse event stream file (.soc or .evts)
	 * and only callback if the timestamp of the event which is greater than start search time stamp
	 *
	 * If startTimestamp is null then return all parsed events
	 *
	 * @param fileName
	 * 		event stream file name
	 * @param eventHandler
	 * 		call back function for handling parsed event object
	 * @return return false if experienced any error otherwise return true
	 */
	boolean parseEventFile(String fileName, Function<EventImpl, Boolean> eventHandler) {
		File file = new File(fileName);
		if (!file.exists()) {
			log.info(EXCEPTION.getMarker(), "File {} does not exist: ", fileName);
			return false;
		}
		log.info(EVENT_PARSER.getMarker(), "Processing file {}", () -> fileName);

		boolean isObjectStreamFile = isStreamFile(file);
		if (!isObjectStreamFile) {
			if (!isOldEventStreamFile(fileName)) {
				log.error(EXCEPTION.getMarker(), "parseEventFile fails :: {} is not an event stream file", fileName);
				return false;
			}
			// if this is an old event stream file, we read prevFileHash
			return parseOldEventFile(file, eventHandler);
		}

		// if this is an object stream file, parse it with ObjectStreamUtilities
		return parseObjectStreamFile(file, eventHandler);
	}

	/**
	 * Parse event object stream file (.soc)
	 * and only callback if the timestamp of the event which is greater than start search time stamp
	 *
	 * If startTimestamp is null then return all parsed events
	 *
	 * @param file
	 * 		event object stream file
	 * @param eventHandler
	 * 		call back function for handling parsed event object
	 * @return return false if experienced any error otherwise return true
	 */
	private boolean parseObjectStreamFile(File file, Function<EventImpl, Boolean> eventHandler) {
		Iterator<SelfSerializable> iterator = ObjectStreamUtilities.parseStreamFile(file);
		boolean readFirstObject = false;
		while (iterator.hasNext()) {
			SelfSerializable object = iterator.next();
			if (!readFirstObject) {
				readFirstObject = true;
				log.info(EVENT_PARSER.getMarker(), "read initialRunningHash {}", () -> object);
				continue;
			}
			if (object instanceof Hash) {
				log.info(EVENT_PARSER.getMarker(), "read lastRunningHash {}", () -> object);
				if (iterator.hasNext()) {
					log.error(EXCEPTION.getMarker(), "The file still has objects after reading lastRunningHash {}",
							iterator.next());
					return false;
				}
				return true;
			}
			if (object == null) {
				log.error(EXCEPTION.getMarker(), "read null from {}", file.getName());
				return false;
			}
			// EventImpl serializes itself as a ConsensusEvent object
			// thus the object read from stream is a ConsensusEvent object
			EventImpl event = new EventImpl((ConsensusEvent) object);
			if (event.hasUserTransactions() && startTimestamp.isBefore(event.getConsensusTimestamp())
					&& !event.getConsensusTimestamp().isAfter(endTimestamp)) {
				// for event to be recovered
				// update lastRecoverRoundWithNewUserTran, so that state recover would not stop
				// before recovering the last signed state
				lastRecoverRoundWithNewUserTran = event.getRoundReceived();
			}
			// calculate Hash for this event
			CryptoFactory.getInstance().digestSync(event.getBaseEventHashedData());
			//log.info(EVENT_PARSER, "Hash: {}", () -> event.getHash());
			event.setConsensus(true);
			eventHandler.apply(event);
		}
		return true;
	}

	/**
	 * Parse old event stream file (.evts)
	 * and only callback if the timestamp of the event which is equal or greater than search time stamp
	 *
	 * If startTimestamp is null then return all parsed events
	 *
	 * @param file
	 * 		event stream file
	 * @param eventHandler
	 * 		call back function for handling parsed event object
	 * @return return false if experienced any error otherwise return true
	 */
	private boolean parseOldEventFile(File file, Function<EventImpl, Boolean> eventHandler) {

		prevFileHash = StreamUtilities.getPrevFileHashFromEventFile(file);
		log.info(EVENT_PARSER.getMarker(), "From file {} read prevFileHash = {}",
				() -> file.getName(),
				() -> hex(prevFileHash));

		try (FileInputStream stream = new FileInputStream(file);
			 SerializableDataInputStream dis = new SerializableDataInputStream(new DigestInputStream(stream, md))) {

			long eventCounterBeforeParse = eventsCounter;
			parseEventStream(dis, eventHandler, this::resetContentMD);

			if (eventCounterBeforeParse == 0 && eventsCounter > 0) {
				// this is the first file that contains the first recovered event
				// checking running hash is the same as original one
				byte[] contentHash = md.digest();
				log.info(EVENT_PARSER.getMarker(), "contentHash from digest {}",
						() -> hex(contentHash));

				byte[] readBackContentHash = getEventFileContentHash(file.getName());
				log.info(EVENT_PARSER.getMarker(), "contentHash from file read {}",
						() -> hex(readBackContentHash));

				if (!Arrays.equals(contentHash, readBackContentHash)) {
					log.error(EXCEPTION.getMarker(), "Calculated running has is different as expected");
					return false;
				}
			}
		} catch (IOException e) {
			log.info(EXCEPTION.getMarker(), "Unexpected", e);
			return false;
		} // try
		return true;
	}


	private static byte[] getEventFileContentHash(String fileName) {
		byte[] contentHash = null;
		try {
			MessageDigest localMD = MessageDigest.getInstance(MD_ALGORITHM);
			localMD.reset();
			byte[] fileBytes = Files.readAllBytes(Paths.get(fileName));

			int preContentLength = 4 + 1 + 48;
			localMD.update(fileBytes, preContentLength, fileBytes.length - preContentLength);
			contentHash = localMD.digest();

		} catch (NoSuchAlgorithmException e) {
			log.error(EXCEPTION.getMarker(), "Experienced error in StreamEventParser {}", e);
		} catch (IOException e) {
			log.error(EXCEPTION.getMarker(), "Exception {}", e);
		}
		return contentHash;
	}

	//Event file hash calculating should not contain file header, this call back handler
	//making sure parser has finished reading file header
	private void resetContentMD(byte[] prevFileHash) {
		this.md.reset();
	}

	private void addToQueue(EventImpl event) {
		if (event != null) {
			if (event.hasUserTransactions()) {
				lastRecoverRoundWithNewUserTran = event.getRoundReceived();
			}
			events.offer(event);
			eventsCounter++;
		}
	}

	/**
	 * @param event
	 * 		Event to be handled
	 * @return indicate whether should continue parse event from input stream
	 */
	private Boolean handleEvent(EventImpl event) {
		if (event == null) {
			log.info(EVENT_PARSER.getMarker(), "Finished parsing events");
			if (prevParsedEvent != null) {
				log.info(EVENT_PARSER.getMarker(), "Last recovered event consensus timestamp {}, round {}",
						prevParsedEvent.getConsensusTimestamp(), prevParsedEvent.getRoundReceived());
				addToQueue(prevParsedEvent);
			}
			return false;
		}

		Instant consensusTimestamp = event.getConsensusTimestamp();

		if (!eventSeqChecker.checkEventSeq(event)) {
			return false;
		}

		// Search criteria :
		// 		startTimestamp < consensusTimestamp <= endTimestamp
		//
		// startTimestamp < consensusTimestamp ->  startTimestamp isBefore consensusTimestamp
		// consensusTimestamp <= endTimestamp ->   consensusTimestamp is NOT after endTimestamp
		if (startTimestamp.isBefore(consensusTimestamp) && !consensusTimestamp.isAfter(endTimestamp)) {
			if (prevParsedEvent != null) {
				//this is not the first parsed event, push prevParsedEvent to queue
				addToQueue(prevParsedEvent);
			}
			prevParsedEvent = event;
		} else if (consensusTimestamp.isAfter(endTimestamp)) {
			log.info(EVENT_PARSER.getMarker(),
					"Search finished due to consensusTimestamp is after endTimestamp");
			return false;
			// if consensusTimestamp is before or equal to startTimestamp, we ignore such event, because this event
			// should not play back in swirdsState;
			// we cannot write this event to event stream, because we only have eventsRunningHash loaded from signed
			// state. we must start to update eventsRunningHash for events whose consensus timestamp is after the
			// loaded signed state, and then start to write event stream file at the first complete window
		}
		return true;
	}

	/**
	 * Extract timestamp from event file name and convert to Instant type
	 *
	 * Event file name in the format of "2019-09-30T16:19:59.710944Z.soc(or .evts)"
	 *
	 * And Instant toString() generate string in the format of "2019:09:30T16:19:59.710944Z"
	 *
	 * @param fileName
	 * 		file name to be converted
	 * @return converted instant object or Instant.MAX
	 */
	static private Instant eventFileName2Instant(String fileName) {
		String revertedName;
		if (isOldEventStreamFile(fileName)) {
			revertedName = fileName.replace(OLD_EVENT_STREAM_FILE_EXTENSION, "").replace("_", ":");
		} else if (isStreamFile(fileName)) {
			revertedName = fileName.replace(OBJECT_STREAM_FILE_EXTENSION, "").replace("_", ":");
		} else {
			log.error(EXCEPTION.getMarker(), "{} is not an event stream file", fileName);
			return Instant.MAX;
		}
		try {
			return Instant.parse(revertedName);
		} catch (DateTimeParseException e) {
			log.error(EXCEPTION.getMarker(), "Parsing instant string {} cause exception", revertedName, e);
			return Instant.MAX;
		}
	}

	@Override
	public void run() {
		eventPlayback();
	}

	static boolean isOldEventStreamFile(String fileName) {
		return fileName.endsWith(OLD_EVENT_STREAM_FILE_EXTENSION);
	}

	long getLastRecoverRoundWithNewUserTran() {
		return lastRecoverRoundWithNewUserTran;
	}
}
