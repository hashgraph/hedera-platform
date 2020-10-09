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

import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.events.BaseEventHashedData;
import com.swirlds.common.events.BaseEventUnhashedData;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.platform.internal.CreatorSeqPair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.swirlds.blob.internal.Utilities.hex;
import static com.swirlds.logging.LogMarker.EVENT_PARSER;
import static com.swirlds.logging.LogMarker.EVENT_STREAM;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.platform.Settings.STREAM_EVENT_START_NO_TRANS_WITH_VERSION;
import static com.swirlds.platform.Settings.STREAM_EVENT_START_WITH_VERSION;

public class StreamUtilities {
	private static final Logger log = LogManager.getLogger();

	static private final byte TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 of previous files
	static private final byte TYPE_SIGNATURE = 3;       // next bytes are signature
	static private final byte TYPE_FILE_HASH = 4;       // next 48 bytes are hash384 of content of the file to be signed

	/**
	 * Event object returned is not fully instantiated since its hash value is invalid due to missing parent event and
	 * other
	 * parent event.
	 *
	 * Using EventTriplet as an container to hold all event related values
	 *
	 * @param dis
	 * 		input data stream
	 * @return return EventTriplet object contains Event and its related hash values
	 */
	static public EventImpl readStreamEvent(SerializableDataInputStream dis) {
		EventImpl event = null;
		try {
			event = dis.readSerializable(true, EventImpl::new);
			com.swirlds.common.crypto.Hash hash = dis.readSerializable(true, com.swirlds.common.crypto.Hash::new);
			event.getBaseEventHashedData().setHash(hash);
		} catch (IOException e) {
			event = null; // reset event since it could be partially instantiated
			log.warn(EVENT_PARSER.getMarker(), "Parsing event warning", e);
		} catch (Exception e) {
			event = null; // reset event since it could be partially instantiated
			log.warn(EVENT_PARSER.getMarker(), "Unexpected parsing event warning", e);
		}
		return event;
	}

	/**
	 * Read the FileHash contained in the signature file
	 *
	 * @param file
	 * 		Signature file instance
	 * @return hash bytes fo the signature file
	 */
	static byte[] getFileHashFromSigFile(File file) {
		Pair<byte[], byte[]> pair = parseSigFile(file);
		if (pair == null) {
			return null;
		}
		return pair.getLeft();
	}

	/**
	 * Read and return the hash value of the previous event file
	 *
	 * @param file
	 * 		Event stream file instance
	 * @return hash bytes of the previous event file
	 */
	static byte[] getPrevFileHashFromEventFile(File file) {
		try (FileInputStream stream = new FileInputStream(file);
			 DataInputStream dis = new DataInputStream(stream)) {

			int streamFileVersion = dis.readInt();
			final byte type_prev_hash = dis.readByte();
			byte[] prevFileHash = new byte[48];
			dis.readFully(prevFileHash);
			return prevFileHash;
		} catch (IOException e) {
			log.info(EXCEPTION.getMarker(), "IOException when read event signature file {}. Exception {}",
					file.getPath(), e);
			return null;
		} // try
	}

	/**
	 * Check if a file is a EventStream signature file
	 *
	 * @param file
	 * 		File instance of signature file
	 * @return return true if it is a event signature file
	 */
	static boolean isEventSigFile(File file) {
		return file.getName().endsWith(".evts_sig");
	}

	/**
	 * Read the FileHash and the signature byte array contained in the signature file;
	 * return a pair of FileHash and signature
	 *
	 * @param file
	 * 		File instance of event signature file
	 * @return An pair instance of file hash byte array and signature byte array
	 */
	static private Pair<byte[], byte[]> parseSigFile(File file) {
		if (!file.getName().endsWith("_sig")) {
			log.info(EVENT_STREAM.getMarker(), "{} is not a signature file", file);
			return null;
		}
		try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
			byte[] fileHash = null;
			byte[] sig = null;
			while (dis.available() != 0) {
				byte typeDelimiter = dis.readByte();
				switch (typeDelimiter) {
					case TYPE_FILE_HASH:
						fileHash = new byte[48];
						dis.readFully(fileHash);
						break;
					case TYPE_SIGNATURE:
						int sigLength = dis.readInt();
						sig = new byte[sigLength];
						dis.readFully(sig);
						break;
					default:
						log.error(EVENT_STREAM.getMarker(), "parseSigFile :: Unknown file delimiter {}",
								typeDelimiter);
				}
			}
			return Pair.of(fileHash, sig);
		} catch (IOException e) {
			log.error(EXCEPTION.getMarker(),
					"readHashFromSigFile :: Fail to read Hash from {}. Exception:", file.getName(), e);
			return null;
		}
	}

	private static byte[] integerToBytes(int number) {
		ByteBuffer b = ByteBuffer.allocate(4);
		b.putInt(number);
		return b.array();
	}


	/**
	 * Parse event stream and use callback function to handle parsed events
	 *
	 * @param dis
	 * 		Input stream
	 * @param eventHandler
	 * 		A Function to handle event also return a boolean value to indicate whether continue parsing remained stream
	 * @param prevFileHashHandler
	 * 		call back function to handle previous hash bytes read from the file
	 */
	static void parseEventStream(SerializableDataInputStream dis, Function<EventImpl, Boolean> eventHandler,
			Consumer<byte[]> prevFileHashHandler) {
		try (dis) {
			int streamFileVersion = dis.readInt();
			final byte type_prev_hash = dis.readByte();
			if (type_prev_hash != TYPE_PREV_HASH) {
				log.error(EVENT_PARSER.getMarker(), "Invalid delimiter : {}, expecting TYPE_PREV_HASH",
						type_prev_hash);
				return;
			}
			byte[] localFileHash = new byte[48];
			dis.readFully(localFileHash);
			if (prevFileHashHandler != null) {
				prevFileHashHandler.accept(localFileHash);
			}

			while (dis.available() != 0) {
				final byte noTransMarker = dis.readByte();
				if (noTransMarker != STREAM_EVENT_START_NO_TRANS_WITH_VERSION &&
						noTransMarker != STREAM_EVENT_START_WITH_VERSION) {
					throw new IOException("Unexpected marker: " + noTransMarker);
				}
				final boolean transactionStripped = noTransMarker == STREAM_EVENT_START_NO_TRANS_WITH_VERSION;
				final int streamEventVersion = dis.readInt();

				EventImpl event = streamEventVersion == 2 ? readStreamEventVersion2(dis,
						transactionStripped) : readStreamEvent(dis);
				if (event != null && eventHandler != null) {
					event.setConsensus(true); //TBD to be deleted ?
					Boolean result = eventHandler.apply(event);
					if (!result) {
						break;

					}
				}
			}//while
		} catch (IOException e) {
			log.info(EXCEPTION.getMarker(), "Unexpected", e);
		} // try
	}


	/**
	 * Parse event file
	 *
	 * @param fileName
	 * 		event stream file name
	 * @param eventHandler
	 * 		call back function for handling parsed event object. When call back function returns true the function
	 * 		will continue to parse the event from the input stream, otherwise, the function will stop parsing remained
	 * 		data
	 */
	public static boolean parseEventFile(String fileName, Function<EventImpl, Boolean> eventHandler) {
		File file = new File(fileName);

		// Populate the SettingsCommon object with the defaults or configured values from the Settings class.
		// This is necessary because this method may be called from a utility program which may or may not
		// read the settings.txt file and follow the normal initialization routines in the Browser class.
		Browser.populateSettingsCommon();

		if (!file.exists()) {
			log.info(EXCEPTION.getMarker(), "File {} does not exist: ", fileName);
			return false;
		} else {
			log.info(EVENT_PARSER.getMarker(), "Processing file {}", fileName);
		}

		try (FileInputStream stream = new FileInputStream(file);
			 SerializableDataInputStream dis = new SerializableDataInputStream(stream)) {
			parseEventStream(dis, eventHandler, null);
		} catch (IOException e) {
			log.info(EXCEPTION.getMarker(), "Unexpected", e);
			return false;
		} // try
		return true;
	}

	static public EventImpl readStreamEventVersion2(SerializableDataInputStream dis, boolean transactionStripped) {
		EventImpl event = null;
		/** sent last while writing an event as part of a sync */
		final byte commEventLast = 0x46 /* 70 */;
		try {
			HashMap<CreatorSeqPair, EventImpl> eventsByCreatorSeq = new HashMap<>();

			int[] byteCount = new int[1];
			byteCount[0] = 0;

			long creatorId = dis.readLong();
			long creatorSeq = dis.readLong();
			long otherId = dis.readLong();
			long otherSeq = dis.readLong();
			byteCount[0] += 4 * Long.BYTES;

			long selfParentGen = dis.readLong();
			long otherParentGen = dis.readLong();
			byteCount[0] += 2 * Long.BYTES;

			byte[] selfParentHash = dis.readByteArray(DigestType.getMaxLength(), true);
			byte[] otherParentHash = dis.readByteArray(DigestType.getMaxLength(), true);

			Transaction[] transactions = new Transaction[0];
			if (!transactionStripped) transactions = Transaction.readArray(dis, byteCount);

			Instant timeCreated = SyncUtils.readInstant(dis, byteCount);
			byte[] signature = SyncUtils.readByteArray(dis, byteCount, SignatureType.getMaxLength());

			if (dis.readByte() != commEventLast) {
				log.warn(EVENT_STREAM.getMarker(), "Stream server : event end marker incorrect");
				return null;
			}
			byteCount[0] += Byte.BYTES;

			byte[] recvHash = SyncUtils.readByteArray(dis, byteCount, DigestType.getMaxLength());
			Instant consensusTimeStamp = SyncUtils.readInstant(dis, byteCount);
			long consensusOrder = dis.readLong();

			EventImpl selfParent = null;
			EventImpl otherParent = null;
			if (eventsByCreatorSeq != null) {
				// find the parents, if they exist
				selfParent = eventsByCreatorSeq.get(new CreatorSeqPair(creatorId, creatorSeq - 1));
				otherParent = eventsByCreatorSeq.get(new CreatorSeqPair(otherId, otherSeq));
			}

			BaseEventHashedData hashedData = new BaseEventHashedData(
					creatorId,
					selfParentGen,
					otherParentGen,
					selfParentHash,
					otherParentHash,
					timeCreated,
					transactions);
			hashedData.setHash(new com.swirlds.common.crypto.Hash(recvHash));
			BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
					creatorSeq,
					otherId,
					otherSeq,
					signature);

			event = new EventImpl(hashedData, unhashedData, selfParent, otherParent);

			event.setConsensusTimestamp(consensusTimeStamp);
			event.setConsensusOrder(consensusOrder);
			event.setAbbreviatedStateEvent(true);

		} catch (IOException e) {
			log.warn(EVENT_STREAM.getMarker(), " Error", e);
		} catch (Exception e) {
			log.warn(EVENT_STREAM.getMarker(), "", e);
		}

		return event;
	}

	/**
	 * A check instance to event sequence numbers of multiple node
	 */
	static class EventSeqChecker {
		private HashMap<Long, Long> writeSeqMap = new HashMap<>();

		/**
		 * Check whether the sequence number of a stream of events is in strictly increasing order
		 *
		 * @param event
		 * 		Event object to be checked
		 * @return return true of no out or order was detected
		 */
		boolean checkEventSeq(EventImpl event) {
			final long creatorId = event.getCreatorId();
			final long eventSeq = event.getSeq();
			if (writeSeqMap.containsKey(creatorId)) {
				final long lastSeq = writeSeqMap.get(creatorId);
				final long expectSeq = lastSeq + 1;
				if (eventSeq <= lastSeq || eventSeq != expectSeq) {
					//it's possible that some stale events could be dropped
					log.info(EVENT_STREAM.getMarker(),
							"Writing out of order, expect creatorId {} seq {}, but " +
									"actual seq: {} lastseq {}, possible due to dropped stale events",
							() -> creatorId,
							() -> expectSeq,
							() -> eventSeq,
							() -> lastSeq);
				}
			}
			writeSeqMap.put(creatorId, eventSeq);
			return true;
		}
	}
}
