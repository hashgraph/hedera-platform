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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static com.swirlds.common.Constants.MS_TO_NS;
import static com.swirlds.common.Constants.SEC_TO_NS;

public class TimestampStreamFileWriter<T extends Timestamped & SerializableHashable> implements ObjectStreamConsumer<T> {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/**
	 * logs related to Object Stream threads
	 */
	private static final Marker LOGM_OBJECT_STREAM = MarkerManager.getMarker("OBJECT_STREAM");
	/**
	 * logs related to content details of Object Stream
	 */
	private static final Marker LOGM_OBJECT_STREAM_DETAIL = MarkerManager.getMarker("OBJECT_STREAM_DETAIL");
	/**
	 * logs related to files created for Object Stream
	 */
	private static final Marker LOGM_OBJECT_STREAM_FILE = MarkerManager.getMarker("OBJECT_STREAM_FILE");
	private static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	private static final int FILE_VERSION = 1;

	private static final int SIG_FILE_VERSION = 1;

	public static final String OBJECT_STREAM_FILE_EXTENSION = ".soc";

	public static final String OBJECT_STREAM_SIG_EXTENSION = ".socs";

	/** file stream and output stream for dump event bytes to file */
	private FileOutputStream stream = null;
	private SerializableDataOutputStream dos = null;

	private String fileNameShort;
	private File file;
	/**
	 * the path to which we write object stream files and signature files
	 */
	private String dirPath;

	/**
	 * current runningHash
	 */
	private Hash runningHash;

	/**
	 * generate signature bytes for lastRunningHash in corresponding file
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
	 * after reconnect, so that reconnect node can generate the same stream files as other nodes after reconnect
	 * else, we start to write object stream file immediately. This is suitable fore streaming after restart, so that no
	 * object would be missing in the nodes' stream files
	 */
	private boolean startWriteAtCompleteWindow;

	private static final SignatureType signatureType = SignatureType.RSA;

	/**
	 * the thread which writes objects to files
	 */
	private Thread writeThread;

	/** queue for stream to observer */
	private BlockingQueue<WorkLoad> forStream;

	/** has writing thread been stopped or not */
	private volatile boolean stopped = false;

	/**
	 * whether writing thread should close file after finish writing the last WorkLoad in
	 * forStream queue, and then stop itself
	 */
	private volatile boolean closed = false;

	public TimestampStreamFileWriter(Hash initialHash,
			String dirPath,
			long logPeriodMs,
			Signer signer,
			boolean startWriteAtCompleteWindow,
			int eventStreamQueueCapacity) {
		runningHash = initialHash;
		this.dirPath = dirPath;
		this.logPeriodMs = logPeriodMs;
		this.signer = signer;
		this.startWriteAtCompleteWindow = startWriteAtCompleteWindow;
		forStream = new ArrayBlockingQueue<>(eventStreamQueueCapacity);
		writeThread = new Thread(this::work);
		//writeThread.setDaemon(true);
		writeThread.start();
	}

	@Override
	public void addToObjectStream(T object, Hash runningHash) {
		try {
			forStream.put(new WorkLoad(object, runningHash));
		} catch (InterruptedException ex) {
			// Restore interrupted state
			Thread.currentThread().interrupt();
		}
	}

	private void work() {
		while (!stopped) {
			try {
				WorkLoad workLoad = forStream.take();
				T object = workLoad.object;
				Hash runningHash = workLoad.hash;

				if (checkIfShouldWriteNewFile(object)) {
					// if we have a current file,
					// should write lastRunningHash, close current file, and generate signature file
					closeCurrentAndSign();
					// start new file
					startNewFile(object);
					// write the beginning of new file
					begin();
				}

				// if stream is null, it means startWriteAtCompleteWindow is true and we are still in the first
				// incomplete
				// window, so we don't serialize this object;
				// so we only serialize the object when stream is not null
				if (stream != null) {
					consume(object);
				}
				// update runningHash
				this.runningHash = runningHash;

				// if close() has been called, and this is the last object in forStream queue
				// we should close current file, and stop this writing thread
				if (closed && forStream.isEmpty()) {
					closeCurrentAndSign();
					stopped = true;
					log.info(LOGM_OBJECT_STREAM,
							"TimestampStreamFileWriter finished writing the last object in forStream, will be stopped");
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
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
			log.info(LOGM_OBJECT_STREAM_DETAIL, "consume :: write object {}",
					() -> object);
		} catch (IOException e) {
			log.warn(LOGM_EXCEPTION, "IOException when serializing {}", object, e);
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
				log.info(LOGM_OBJECT_STREAM, "Stream file already exists {}",
						() -> fileNameShort);
			} else {
				stream = new FileOutputStream(file, false);
				dos = new SerializableDataOutputStream(new BufferedOutputStream(stream));
				log.info(LOGM_OBJECT_STREAM_FILE, "Stream file created {}", () -> fileNameShort);
			}
		} catch (FileNotFoundException e) {
			log.error(LOGM_EXCEPTION, "startNewFile :: FileNotFound: ", e);
		}
	}

	/**
	 * write the beginning part of the file:
	 * File Version ID, and initial runningHash
	 */
	private void begin() {
		try {
			// write file version
			dos.writeInt(FILE_VERSION);
			log.info(LOGM_OBJECT_STREAM_FILE, "begin :: write File_VERSION {}", () -> FILE_VERSION);
			// write initialRunningHash
			dos.writeSerializable(runningHash, true);
			log.info(LOGM_OBJECT_STREAM_FILE, "begin :: write initialRunningHash {}", () -> runningHash);
		} catch (IOException e) {
			log.warn(LOGM_EXCEPTION, "IOException when writing initialRunningHash to {}", fileNameShort, e);
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
			// write lastRunningHash
			try {
				dos.writeSerializable(runningHash, true);
				log.info(LOGM_OBJECT_STREAM_FILE, "closeCurrent :: write lastRunningHash {}",
						() -> runningHash);
			} catch (IOException e) {
				log.warn(LOGM_EXCEPTION, "IOException when writing lastRunningHash to {}", fileNameShort, e);
			}
			File currentFile = file;
			// close current file
			closeFile();
			// generate signature file
			byte[] signature = signer.sign(runningHash.getValue());
			try {
				writeSignatureFile(signature, generateSigFilePath(currentFile));
			} catch (IOException e) {
				log.error(LOGM_EXCEPTION,
						"writeSignatureFile :: Fail to generate signature file for {}", fileNameShort, e);
			}
		}
	}


	/**
	 * generate signature file for current object stream file
	 * the signature bytes should be generated by signing on the lastRunningHash
	 *
	 * @param signature
	 * 		signature bytes
	 * @param sigFilePath
	 * 		path of the signature file to be written
	 */
	public static void writeSignatureFile(byte[] signature, String sigFilePath) throws IOException {
		try (SerializableDataOutputStream output = new SerializableDataOutputStream(
				new BufferedOutputStream(new FileOutputStream(sigFilePath)))) {
			output.writeInt(SIG_FILE_VERSION);
			output.writeSerializable(new Signature(signatureType, signature), true);
			log.info(LOGM_OBJECT_STREAM_FILE, "signature file saved: {}", sigFilePath);
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

				file = null;
				stream = null;
				dos = null;

			} catch (IOException e) {
				log.warn(LOGM_EXCEPTION, "Exception in close file", e);
			}
			log.info(LOGM_OBJECT_STREAM_FILE, "File {} is closed at {}",
					() -> fileNameShort, () -> Instant.now());
		}
	}

	/**
	 * check whether need to start a new file
	 * return a boolean value which indicates whether we should start a new File
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
			result = getPeriod(lastConsensusTimestamp) != getPeriod(currentConsensusTimestamp);
		}
		// update lastConsensusTimestamp
		lastConsensusTimestamp = currentConsensusTimestamp;
		return result;
	}

	public long getPeriod(Instant consensusTimestamp) {
		final long nanos = consensusTimestamp.getEpochSecond() * SEC_TO_NS + consensusTimestamp.getNano();
		return nanos / MS_TO_NS / logPeriodMs;
	}

	/**
	 * generate full fileName from given SelfSerializable object
	 */
	String generateStreamFilePath(T object) {
		return dirPath + File.separator + generateStreamFileNameFromInstant(object.getTimestamp());
	}

	/**
	 * generate fileName from given Instant
	 */
	public static String generateStreamFileNameFromInstant(Instant timestamp) {
		return timestamp.toString().replace(
				":", "_") + OBJECT_STREAM_FILE_EXTENSION;
	}

	/**
	 * generate signature file name for current stream file
	 */
	String generateSigFilePath(File file) {
		return file.getAbsolutePath() + "s";
	}

	public void waitUntilDone() throws InterruptedException {
		writeThread.join();
	}

	class WorkLoad {
		T object;
		Hash hash;

		WorkLoad(T object, Hash hash) {
			this.object = object;
			this.hash = hash;
		}
	}

	/**
	 * this method is called when the node falls behind,
	 * clears the queue, and stops the thread.
	 * if current stream file is half written, deletes this file.
	 */
	public void stopAndClear() {
		stopped = true;
		forStream.clear();
		if (stream != null) {
			File currentFile = file;
			// close current file
			closeFile();
			try {
				// delete this file since it is half written
				Files.delete(currentFile.toPath());
				log.info(LOGM_OBJECT_STREAM, "TimestampStreamFileWriter::stopAndClear deleted {}",
						() -> currentFile.getName());
			} catch (IOException ex) {
				log.error(LOGM_EXCEPTION, "TimestampStreamFileWriter::stopAndClear got IOException " +
						"when deleting file {}", () -> currentFile.getName());
			}
		}
		log.info(LOGM_OBJECT_STREAM, "TimestampStreamFileWriter stopped");
	}

	@Override
	public void close() {
		closed = true;
	}
}
