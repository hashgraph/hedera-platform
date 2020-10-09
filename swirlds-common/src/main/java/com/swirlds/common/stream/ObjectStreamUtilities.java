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
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidParameterException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;

import static com.swirlds.common.stream.StreamValidationResult.CALCULATED_LAST_HASH_NOT_MATCH;
import static com.swirlds.common.stream.StreamValidationResult.OK;
import static com.swirlds.common.stream.StreamValidationResult.PARSE_SIG_FILE_FAIL;
import static com.swirlds.common.stream.StreamValidationResult.PARSE_STREAM_FILE_FAIL;
import static com.swirlds.common.stream.StreamValidationResult.SIG_NOT_MATCH_FILE;
import static com.swirlds.common.stream.StreamValidationResult.STREAM_FILE_EMPTY;
import static com.swirlds.common.stream.StreamValidationResult.STREAM_FILE_MISS_INITIAL_HASH;
import static com.swirlds.common.stream.StreamValidationResult.STREAM_FILE_MISS_LAST_HASH;
import static com.swirlds.common.stream.StreamValidationResult.STREAM_FILE_MISS_OBJECTS;
import static com.swirlds.common.stream.TimestampStreamFileWriter.OBJECT_STREAM_FILE_EXTENSION;
import static com.swirlds.common.stream.TimestampStreamFileWriter.OBJECT_STREAM_SIG_EXTENSION;

public class ObjectStreamUtilities {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOGGER = LogManager.getLogger();

	private static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	private ObjectStreamUtilities() {
		// Utility classes are not meant to be instantiated
		throw new IllegalStateException("Utility class");
	}

	static {
		// set the settings so that when deserialization we would not have transactionMaxBytes be 0
		SettingsCommon.maxTransactionCountPerEvent = 245_760;
		SettingsCommon.maxTransactionBytesPerEvent = 245_760;
		SettingsCommon.transactionMaxBytes = 6_144;
	}

	/**
	 * parse an inputStream, return an Iterator from which we can get all SelfSerializable objects in the inputStream
	 *
	 * @param inputStream
	 * 		an inputStream to be parsed
	 * @return an Iterator from which we can get all SelfSerializable objects in the inputStream
	 */
	public static <T extends SelfSerializable> Iterator<T> parseStream(InputStream inputStream) {
		return new SingleStreamIterator<>(inputStream);
	}

	/**
	 * Parse a single stream file, return an Iterator from which we can get all SelfSerializable objects in the file
	 * the first object is initialRunningHash;
	 * the last object is lastRunningHash;
	 * the other objects are stream objects;
	 *
	 * @param file
	 * 		a .soc stream file
	 * @return an Iterator from which we can get all SelfSerializable objects in the file
	 */
	public static <T extends SelfSerializable> SingleStreamIterator<T> parseStreamFile(
			File file) throws InvalidParameterException {
		// if this file's extension name doesn't match expected
		if (!isStreamFile(file)) {
			String msg = String.format("Fail to parse File %s, its extension doesn't match %s",
					file.getName(), OBJECT_STREAM_FILE_EXTENSION);
			throw new InvalidParameterException(msg);
		}

		return new SingleStreamIterator<>(file);
	}

	/**
	 * Parses a single stream file, return initialRunningHash saved in the .soc file
	 *
	 * @param file a .soc stream file
	 * @return initial RunningHash
	 */
	public static Hash readInitialHashFromStreamFile(final File file) {
		SingleStreamIterator<SelfSerializable> singleStreamIterator = parseStreamFile(file);
		Hash initialRunningHash = (Hash) singleStreamIterator.next();
		singleStreamIterator.closeStream();
		return initialRunningHash;
	}

	/**
	 * Reads lastRunningHash from a .soc stream file
	 *
	 * @param file
	 * 		a .soc stream file
	 * @return lastRunningHash
	 */
	public static <T extends SelfSerializable> Hash readLastHashFromStreamFile(
			final File file) throws IllegalStateException {
		SingleStreamIterator<T> iterator = parseStreamFile(file);
		T object = null;
		while (iterator.hasNext()) {
			object = iterator.next();
		}
		if (object == null || !(object instanceof Hash)) {
			throw new IllegalStateException("Fail to read lastRunningHash from file: " + file.getName());
		}
		return (Hash) object;
	}

	/**
	 * if it is a single stream file, parse this file, and return an Iterator which contains
	 * all SelfSerializables contained in the file.
	 *
	 * if it is a directory, parse stream files in this directory in increasing order by fileName, also validate if a
	 * file's initialRunningHash matches its previous file's lastRunningHash.
	 * return an Iterator which contains the initialRunningHash in the first stream file,
	 * all stream objects contained in the directory, and the lastRunningHash in the last stream file
	 *
	 * @param objectStreamDirOrFile
	 * 		a single object stream file or a directory which contains stream files
	 */
	public static <T extends SelfSerializable> Iterator<T> parseStreamDirOrFile(File objectStreamDirOrFile) {
		if (objectStreamDirOrFile.isDirectory()) {
			return new StreamFilesIterator<>(objectStreamDirOrFile.listFiles(ObjectStreamUtilities::isStreamFile));
		} else if (!isStreamFile(objectStreamDirOrFile)) {
			// if it is not a stream file, return null
			return null;
		} else {
			return parseStreamFile(objectStreamDirOrFile);
		}
	}

	/**
	 * Parses a list of single object stream file
	 *
	 * @return an Iterator which contains the initialRunningHash in the first stream file,
	 * 		all stream objects contained in the directory, and the lastRunningHash in the last stream file
	 */
	public static <T extends SelfSerializable> Iterator<T> parseStreamFileList(List<File> list) {
		return new StreamFilesIterator<>(list.toArray(File[]::new));
	}

	/**
	 * read signature byte from a stream signature file
	 *
	 * @param file a signature file
	 * @return a Signature saved in this file
	 */
	public static Signature readSigFromFile(File file) {
		if (!isStreamSigFile(file)) {
			LOGGER.error(LOGM_EXCEPTION,
					"readSigFromFile :: fail to read signature from File {}, its extension doesn't match {}", file,
					OBJECT_STREAM_SIG_EXTENSION);
			return null;
		} else {
			try (SerializableDataInputStream inputStream = new SerializableDataInputStream(
					new BufferedInputStream(new FileInputStream(file)))) {
				inputStream.readInt();    // sig file version
				return inputStream.readSerializable();
			} catch (IOException ex) {
				LOGGER.error(LOGM_EXCEPTION, "readSigFromFile :: got IOException ", ex);
				return null;
			}
		}
	}

	/**
	 * check if given file is a stream file
	 *
	 * @param file a file
	 * @return whether the given file is a stream file
	 */
	public static boolean isStreamFile(File file) {
		return isStreamFile(file.getName());
	}

	/**
	 * check if it is a stream file name
	 *
	 * @param fileName a file name
	 * @return whether the given file name is a stream file name
	 */
	public static boolean isStreamFile(String fileName) {
		return fileName.endsWith(OBJECT_STREAM_FILE_EXTENSION);
	}

	/**
	 * check if given file is a stream signature file
	 *
	 * @param file a file
	 * @return whether the given file is a stream signature file
	 */
	public static boolean isStreamSigFile(File file) {
		return file.getName().endsWith(OBJECT_STREAM_SIG_EXTENSION);
	}

	/**
	 * for a single object stream file, validates if it is valid, i.e., saved lastRunningHash matches calculated
	 * lastRunningHash;
	 * for a directory of object stream files, validates if all files in it are valid and chained
	 *
	 * @param objectDirOrFile
	 * 		a directory or a single object stream file
	 * @return a pair of StreamValidationResult and lastRunningHash
	 */
	public static <T extends SelfSerializable> Pair<StreamValidationResult, Hash> validateDirOrFile(
			File objectDirOrFile) {
		return validateIterator(parseStreamDirOrFile(objectDirOrFile));
	}

	/**
	 * validate a list of stream object files
	 * @param fileList a list of stream object files
	 * @param <T> extends SelfSerializable
	 * @return a Pair of StreamValidationResult and last RunningHash
	 */
	public static <T extends SelfSerializable> Pair<StreamValidationResult, Hash> validateFileList(List<File> fileList) {
		return validateIterator(parseStreamFileList(fileList));
	}

	/**
	 * Calculates a RunningHash for given initialRunningHash and objects in the iterator
	 * Verifies if the lastRunningHash in the Iterator matches the calculated RunningHash
	 *
	 * @param iterator an iterator parsed from stream file
	 * @param <T> extends SelfSerializable
	 * @return a pair of validation result and last running hash
	 */
	private static <T extends SelfSerializable> Pair<StreamValidationResult, Hash> validateIterator(
			final Iterator<T> iterator) {
		if (iterator == null) {
			return Pair.of(PARSE_STREAM_FILE_FAIL, null);
		}
		if (!iterator.hasNext()) {
			return Pair.of(STREAM_FILE_EMPTY, null);
		}
		T first = iterator.next();
		if (!(first instanceof Hash)) {
			return Pair.of(STREAM_FILE_MISS_INITIAL_HASH, null);
		}
		// initialize a RunningHash with initialRunnignHash
		RunningHash runningHash = new RunningHash((Hash) first);

		T selfSerializable = null;
		while (iterator.hasNext()) {
			selfSerializable = iterator.next();
			if (selfSerializable instanceof Hash) {
				break;
			}
			Hash objectHash = CryptoFactory.getInstance().digestSync(selfSerializable);
			runningHash.addAndDigest(objectHash);
		}
		if (selfSerializable == null) {
			return Pair.of(STREAM_FILE_MISS_OBJECTS, null);
		}
		if (!(selfSerializable instanceof Hash)) {
			return Pair.of(STREAM_FILE_MISS_LAST_HASH, null);
		}

		Hash lastHash = (Hash) selfSerializable;
		// check if calculated lastRunningHash matches the lastHash read from file
		if (!runningHash.getHash().equals(lastHash)) {
			return Pair.of(CALCULATED_LAST_HASH_NOT_MATCH, null);
		}
		return Pair.of(OK, lastHash);
	}

	/**
	 * validate if the objectFile is valid, i.e., saved lastRunningHash matches calculated lastRunningHash;
	 * if the objectFile is valid, then validate if the sigFile contains valid signatures of the objectFile's
	 * lastRunningHash;
	 *
	 * @param objectFile
	 * 		an .soc Object Stream file
	 * @param sigFile
	 * 		a .socs signature file
	 * @return validation result
	 */
	public static StreamValidationResult validateFileAndSignature(File objectFile, File sigFile, PublicKey publicKey) {
		Pair<StreamValidationResult, Hash> objectResult = validateDirOrFile(objectFile);
		if (objectResult.getLeft() != OK) {
			return objectResult.getLeft();
		}
		Hash lastRunningHash = objectResult.getRight();

		return validateSignature(lastRunningHash, sigFile, publicKey);
	}

	/**
	 * validates if the sigFile contains valid signatures of this lastRunningHash;
	 *
	 * @param lastRunningHash
	 * 		lastRunningHash extracted from an .soc Object Stream file
	 * @param sigFile
	 * 		a .socs signature file
	 * @return validation result
	 */
	public static StreamValidationResult validateSignature(Hash lastRunningHash, File sigFile, PublicKey publicKey) {
		Signature signature = readSigFromFile(sigFile);
		if (signature == null) {
			return PARSE_SIG_FILE_FAIL;
		}
		if (signature.verifySignature(lastRunningHash.getValue(), publicKey)) {
			return OK;
		} else {
			return SIG_NOT_MATCH_FILE;
		}
	}

	/**
	 * @param filename
	 * 		filename such as: 2020-09-21T15_16_56.978420Z.soc
	 * @return timestamp extracted from this filename
	 */
	public static Instant getTimeStampFromFileName(final String filename) {
		int indexOfZ = filename.indexOf("Z");
		if (indexOfZ != -1) {
			String dateInfo = filename.substring(0, indexOfZ + 1);
			dateInfo = dateInfo.replace("_", ":");
			return Instant.parse(dateInfo);
		}
		return null;
	}
}
