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

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.SerializableRunningHashable;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.logging.LogMarker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.generateSigFilePath;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.generateStreamFileNameFromInstant;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.OBJECT_STREAM;
import static com.swirlds.logging.LogMarker.OBJECT_STREAM_DETAIL;
import static com.swirlds.logging.LogMarker.OBJECT_STREAM_FILE;

/**
 * This class is used for writing stream files;
 * it receives object one by one from previous LinkedObjectStream;
 * writes objects into stream files, and generates stream signature files
 *
 * @param <T>
 * 		the type of the objects
 */
public class TimestampStreamFileWriter<T extends Timestamped & SerializableRunningHashable>
		implements LinkedObjectStream<T> {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/**
	 * defines the format of the remainder of the stream file
	 */
	public static final int OBJECT_STREAM_VERSION = 1;
	/**
	 * defines the format of the remainder of the stream signature file
	 */
	public static final int OBJECT_STREAM_SIG_VERSION = 1;
	/**
	 * stream file type: record stream, or event stream
	 */
	private final StreamType streamType;

	/** file stream and output stream for dump event bytes to file */
	private FileOutputStream stream = null;
	private SerializableDataOutputStream dos = null;
	/** output stream for digesting metaData */
	private SerializableDataOutputStream dosMeta = null;

	private String fileNameShort;
	private File file;
	/**
	 * the path to which we write object stream files and signature files
	 */
	private String dirPath;

	/**
	 * current runningHash before consuming the object added by calling {@link #addObject(Timestamped)} method
	 */
	private RunningHash runningHash;

	/**
	 * generate signature bytes for endRunningHash in corresponding file
	 */
	private Signer signer;

	/**
	 * period of generating object stream files in ms
	 */
	private long logPeriodMs;

	/**
	 * initially, set to be consensus timestamp of the first object;
	 * start to write a new file when the next object's consensus timestamp is in different periods with
	 * lastConsensusTimestamp;
	 * update its value each time receives a new object
	 */
	private Instant lastConsensusTimestamp;

	/**
	 * if it is true, we don't write object stream file until the first complete window. This is suitable for streaming
	 * after reconnect, so that reconnect node can generate the same stream files as other nodes after reconnect.
	 *
	 * if it is false, we start to write object stream file immediately. This is suitable for streaming after
	 * restart, so
	 * that no
	 * object would be missing in the nodes' stream files
	 */
	private boolean startWriteAtCompleteWindow;

	private static final SignatureType signatureType = SignatureType.RSA;
	/**
	 * a messageDigest object for digesting entire stream file and generating entire Hash
	 */
	private MessageDigest mdEntire;
	/**
	 * a messageDigest object for digesting Metadata in the stream file and generating Metadata Hash
	 * Metadata contains: bytes before startRunningHash || startRunningHash || endRunningHash
	 * || denotes concatenation
	 */
	private MessageDigest mdMeta;

	public TimestampStreamFileWriter(String dirPath,
			long logPeriodMs,
			Signer signer,
			boolean startWriteAtCompleteWindow,
			StreamType streamType) throws NoSuchAlgorithmException {
		this.dirPath = dirPath;
		this.logPeriodMs = logPeriodMs;
		this.signer = signer;
		this.startWriteAtCompleteWindow = startWriteAtCompleteWindow;
		this.streamType = streamType;

		mdEntire = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
		mdMeta = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
	}

	/**
	 * serialize given object with ClassId
	 *
	 * @param object
	 */
	private void consume(T object) {
		try {
			dos.writeSerializable(object, true);
			dos.flush();
			log.info(OBJECT_STREAM_DETAIL.getMarker(), "consume :: write object {}",
					() -> object);
		} catch (IOException e) {
			log.warn(EXCEPTION.getMarker(), "IOException when serializing {}", object, e);
		}
	}

	/**
	 * set the file to which we serialize objects
	 *
	 * @param object
	 * 		the first object to be written to new file
	 */
	private void startNewFile(T object) {
		this.file = new File(generateStreamFilePath(object));
		this.fileNameShort = file.getName();
		try {
			if (file.exists() && !file.isDirectory()) {
				log.info(OBJECT_STREAM.getMarker(), "Stream file already exists {}",
						() -> fileNameShort);
			} else {
				stream = new FileOutputStream(file, false);
				dos = new SerializableDataOutputStream(
						new BufferedOutputStream(new HashingOutputStream(mdEntire, stream)));
				dosMeta = new SerializableDataOutputStream(new HashingOutputStream(mdMeta));
				log.info(OBJECT_STREAM_FILE.getMarker(), "Stream file created {}", () -> fileNameShort);
			}
		} catch (FileNotFoundException e) {
			log.error(EXCEPTION.getMarker(), "startNewFile :: FileNotFound: ", e);
		}
	}

	/**
	 * write the beginning part of the file:
	 * File Version ID, and initial runningHash
	 */
	private void begin() {
		try {
			// write file header
			for (int num : streamType.getFileHeader()) {
				dos.writeInt(num);
				dosMeta.writeInt(num);
			}
			// write file version
			dos.writeInt(OBJECT_STREAM_VERSION);
			dosMeta.writeInt(OBJECT_STREAM_VERSION);

			log.info(OBJECT_STREAM_FILE.getMarker(), "begin :: write OBJECT_STREAM_VERSION {}",
					() -> OBJECT_STREAM_VERSION);
			// write startRunningHash
			Hash startRunningHash = runningHash.getFutureHash().get();
			dos.writeSerializable(startRunningHash, true);
			dosMeta.writeSerializable(startRunningHash, true);
			log.info(OBJECT_STREAM_FILE.getMarker(), "begin :: write startRunningHash {}", () -> startRunningHash);
		} catch (IOException e) {
			Thread.currentThread().interrupt();
			log.error(EXCEPTION.getMarker(), "begin :: Got IOException when writing startRunningHash to {}",
					fileNameShort, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error(EXCEPTION.getMarker(), "begin :: Got interrupted when getting startRunningHash for writing {}",
					fileNameShort, e);
		}
	}

	/**
	 * if stream is not null:
	 * write last runningHash to current file;
	 * close current file;
	 * and generate a corresponding signature file
	 */
	public void closeCurrentAndSign() {
		if (stream != null) {
			// write endRunningHash
			Hash endRunningHash;
			try {
				endRunningHash = runningHash.getFutureHash().get();
				dos.writeSerializable(endRunningHash, true);
				dosMeta.writeSerializable(endRunningHash, true);
				log.info(OBJECT_STREAM_FILE.getMarker(), "closeCurrentAndSign :: write endRunningHash {}",
						() -> endRunningHash);
			} catch (IOException e) {
				Thread.currentThread().interrupt();
				log.error(EXCEPTION.getMarker(),
						"closeCurrentAndSign :: Got Exception when writing endRunningHash to {}", fileNameShort, e);
				return;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.error(EXCEPTION.getMarker(),
						"closeCurrentAndSign :: Got interrupted when getting endRunningHash for writing {}",
						fileNameShort, e);
				return;
			}
			File currentFile = file;
			// close current file
			closeFile();

			// get entire Hash for this stream file
			Hash entireHash = new Hash(mdEntire.digest(), DigestType.SHA_384);
			// get metaData Hash for this stream file
			Hash metaHash = new Hash(mdMeta.digest(), DigestType.SHA_384);

			// generate signature for entire Hash
			Signature entireSignature = new Signature(signatureType, signer.sign(entireHash.getValue()));
			// generate signature for metaData Hash
			Signature metaSignature = new Signature(signatureType, signer.sign(metaHash.getValue()));
			try {
				writeSignatureFile(entireHash, entireSignature, metaHash, metaSignature,
						generateSigFilePath(currentFile), streamType);
			} catch (IOException e) {
				log.error(EXCEPTION.getMarker(),
						"closeCurrentAndSign ::  :: Fail to generate signature file for {}", fileNameShort, e);
			}
		}
	}

	/**
	 * generate signature file for current object stream file
	 * the signature bytes should be generated by signing on the endRunningHash
	 *
	 * @param entireHash
	 * 		a Hash calculated with all bytes in the entire stream file
	 * @param entireSignature
	 * 		a Signature which is generated by signing the value of entireHash
	 * @param metaHash
	 * 		a Hash calculated with metadata bytes in the stream file
	 * @param metaSignature
	 * 		a Signature which is generated by signing the value of metaHash
	 * @param sigFilePath
	 * 		path of the signature file to be written
	 * @param streamType
	 * 		type of this stream file
	 * @throws IOException
	 * 		thrown if any I/O related errors occur
	 */
	public static void writeSignatureFile(final Hash entireHash, final Signature entireSignature,
			final Hash metaHash, final Signature metaSignature, final String sigFilePath,
			final StreamType streamType) throws IOException {
		try (SerializableDataOutputStream output = new SerializableDataOutputStream(
				new BufferedOutputStream(new FileOutputStream(sigFilePath)))) {
			// write signature file header
			for (byte num : streamType.getSigFileHeader()) {
				output.writeByte(num);
			}
			output.writeInt(OBJECT_STREAM_SIG_VERSION);
			output.writeSerializable(entireHash, true);
			output.writeSerializable(entireSignature, true);
			output.writeSerializable(metaHash, true);
			output.writeSerializable(metaSignature, true);
			log.info(OBJECT_STREAM_FILE.getMarker(), "signature file saved: {}", sigFilePath);
		}
	}

	/**
	 * if stream is not null, close current file and save to disk
	 */
	private void closeFile() {
		if (stream != null) {
			try {
				dos.flush();
				stream.flush();

				stream.getChannel().force(true);
				stream.getFD().sync();

				dos.close();
				stream.close();
				dosMeta.close();

				file = null;
				stream = null;
				dos = null;
				dosMeta = null;
			} catch (IOException e) {
				log.warn(EXCEPTION.getMarker(), "Exception in close file", e);
			}
			log.info(OBJECT_STREAM_FILE.getMarker(), "File {} is closed at {}",
					() -> fileNameShort, () -> Instant.now());
		}
	}

	/**
	 * check whether need to start a new file
	 *
	 * @param object
	 * 		the object to be written into file
	 * @return whether we should start a new File for writing this object
	 */
	public boolean checkIfShouldWriteNewFile(T object) {
		Instant currentConsensusTimestamp = object.getTimestamp();
		boolean result;
		if (lastConsensusTimestamp == null && !startWriteAtCompleteWindow) {
			// this is the first object, we should start writing it to new File
			result = true;
		} else if (lastConsensusTimestamp == null && startWriteAtCompleteWindow) {
			// this is the first object, we should wait for the first complete window
			result = false;
		} else {
			// if lastConsensusTimestamp and currentConsensusTimestamp are in different periods,
			// we should start a new file
			result = getPeriod(lastConsensusTimestamp, logPeriodMs) != getPeriod(currentConsensusTimestamp,
					logPeriodMs);
		}
		// update lastConsensusTimestamp
		lastConsensusTimestamp = currentConsensusTimestamp;
		return result;
	}


	/**
	 * generate full fileName from given SelfSerializable object
	 */
	String generateStreamFilePath(T object) {
		return dirPath + File.separator + generateStreamFileNameFromInstant(object.getTimestamp(), streamType);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setRunningHash(final Hash hash) {
		this.runningHash = new RunningHash(hash);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addObject(T object) {
		if (checkIfShouldWriteNewFile(object)) {
			// if we have a current file,
			// should write endRunningHash, close current file, and generate signature file
			closeCurrentAndSign();
			// start new file
			startNewFile(object);

			// if the file already exists, it will not be opened, so we don't write anything to it. begin() would
			// previously throw an NPE before this check was added
			if (stream != null) {
				// write the beginning of new file
				begin();
			}
		}

		// if stream is null, it means startWriteAtCompleteWindow is true and we are still in the first
		// incomplete
		// window, so we don't serialize this object;
		// so we only serialize the object when stream is not null
		if (stream != null) {
			consume(object);
		}
		// update runningHash
		this.runningHash = object.getRunningHash();
	}

	/**
	 * this method is called when the node falls behind,
	 * if current stream file is half written, deletes this file.
	 */
	@Override
	public void clear() {
		if (stream != null) {
			File currentFile = file;
			// close current file
			closeFile();
			try {
				// delete this file since it is half written
				Files.delete(currentFile.toPath());
				log.info(OBJECT_STREAM.getMarker(), "TimestampStreamFileWriter::clear deleted {}",
						() -> currentFile.getName());
			} catch (IOException ex) {
				log.error(EXCEPTION.getMarker(), "TimestampStreamFileWriter::clear got IOException " +
						"when deleting file {}", () -> currentFile.getName());
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		closeCurrentAndSign();
		log.info(LogMarker.FREEZE.getMarker(),
				"TimestampStreamFileWriter finished writing the last object, is stopped");
	}

	/**
	 * set startWriteAtCompleteWindow:
	 * it should be set to be true after reconnect, or at state recovering;
	 * it should be set to be false at restart
	 *
	 * @param startWriteAtCompleteWindow
	 */
	public void setStartWriteAtCompleteWindow(boolean startWriteAtCompleteWindow) {
		this.startWriteAtCompleteWindow = startWriteAtCompleteWindow;
		log.info(OBJECT_STREAM.getMarker(),
				"TimestampStreamFileWriter::setStartWriteAtCompleteWindow: {}", () -> startWriteAtCompleteWindow);
	}

	/**
	 * return startWriteAtCompleteWindow
	 *
	 * @return whether we should write object stream file until the first complete window
	 */
	public boolean getStartWriteAtCompleteWindow() {
		return startWriteAtCompleteWindow;
	}
}
