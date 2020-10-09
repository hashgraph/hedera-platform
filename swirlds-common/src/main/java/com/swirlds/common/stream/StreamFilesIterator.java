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
import com.swirlds.common.io.SelfSerializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * an Iterator from which we can get all SelfSerializable objects contained in given files;
 * the files are parsed in increasing order by file names;
 * the next file's initialRunningHash is validated against the lastRunningHash of previous file;
 * the first item is the initialRunningHash in the first stream file,
 * then all stream objects contained in the directory,
 * the last item is the lastRunningHash in the last stream file
 */
class StreamFilesIterator<T extends SelfSerializable> implements Iterator<T> {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	private static final Marker LOGM_OBJECT_STREAM_FILE = MarkerManager.getMarker("OBJECT_STREAM_FILE");

	private static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");


	File[] files;
	/**
	 * index of current file
	 */
	int idx;
	/**
	 * initialRunningHash in current file
	 */
	Hash initialRunningHash;
	/**
	 * lastRunningHash in previous file
	 */
	Hash lastRunningHash;
	Iterator<T> currentFileIterator;

	T next;

	public StreamFilesIterator(File[] files) {
		this.files = Arrays.stream(files).filter(ObjectStreamUtilities::isStreamFile).sorted(
				Comparator.comparing(File::getName)).toArray(File[]::new);
		Arrays.sort(files, Comparator.comparing(File::getName));
		log.info(LOGM_OBJECT_STREAM_FILE, "StreamFilesIterator : files to be parsed: {}",
				() -> Arrays.toString(files));
		idx = 0;
		lastRunningHash = null;
		initialRunningHash = null;
		currentFileIterator = idx < files.length ? ObjectStreamUtilities.parseStreamFile(files[idx]) : null;
	}

	@Override
	public boolean hasNext() {
		if (currentFileIterator == null || !currentFileIterator.hasNext() && idx == files.length - 1) {
			return false;
		}
		if (currentFileIterator.hasNext()) {
			T object = currentFileIterator.next();

			if (object instanceof Hash) {
				if (initialRunningHash == null) {
					// this is the initialRunningHash of the first file, should output it
					next = object;
					// update initialRunningHash
					initialRunningHash = (Hash) object;
					log.info(LOGM_OBJECT_STREAM_FILE, "StreamFilesIterator : first initialRunningHash: {}",
							() -> initialRunningHash);
					return true;
				} else {
					// this is lastRunningHash of current file
					// update lastRunningHash
					lastRunningHash = (Hash) object;
					log.info(LOGM_OBJECT_STREAM_FILE, "StreamFilesIterator : update lastRunningHash: {}",
							() -> lastRunningHash);
					if (idx == files.length - 1) {
						// if current file is the last file, should output it
						next = object;
						log.info(LOGM_OBJECT_STREAM_FILE, "StreamFilesIterator : last lastRunningHash: {}",
								() -> object);
						return true;
					}

					// read next file
					return readNextFile();
				}
			} else {
				next = object;
				return true;
			}
		} else {
			return readNextFile();
		}
	}

	@Override
	public T next() {
		return next;
	}

	/**
	 * open the next file, read initialRunningHash and validate it against lastRunningHash;
	 * if not match, log an error and return false;
	 * else read and set next object
	 *
	 * @return
	 */
	boolean readNextFile() {
		idx++;
		currentFileIterator = ObjectStreamUtilities.parseStreamFile(files[idx]);
		if (!currentFileIterator.hasNext()) {
			log.info(LOGM_OBJECT_STREAM_FILE, "Fail to parse {}", files[idx]);
			return false;
		}
		T hash = currentFileIterator.next();
		if (!(hash instanceof Hash)) {
			log.info(LOGM_OBJECT_STREAM_FILE, "The first item {} in {} is not Hash", hash, files[idx]);
			return false;
		}
		// update initialRunningHash
		initialRunningHash = (Hash) hash;
		log.info(LOGM_OBJECT_STREAM_FILE, "StreamFilesIterator : update initialRunningHash: {}",
				() -> initialRunningHash);
		// validate initialRunningHash against lastRunningHash of previous file
		if (!initialRunningHash.equals(lastRunningHash)) {
			log.info(LOGM_OBJECT_STREAM_FILE,
					"initialRunningHash {} in {} doesn't match lastRunningHash {} in {}",
					initialRunningHash, files[idx], lastRunningHash, files[idx - 1]);
			return false;
		} else if (!currentFileIterator.hasNext()) {
			// log error if the file is ended
			log.error(LOGM_EXCEPTION, "file {} only contains initialRunningHash", () -> files[idx]);
			return false;
		} else {
			// output stream object
			next = currentFileIterator.next();
			return true;
		}
	}
}
