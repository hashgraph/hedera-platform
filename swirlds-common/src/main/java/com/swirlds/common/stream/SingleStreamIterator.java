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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Parse an inputStream, return an Iterator from which we can get all SelfSerializable objects in the inputStream
 */
public class SingleStreamIterator<T extends SelfSerializable> implements Iterator<T> {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	private static final Marker LOGM_OBJECT_STREAM = MarkerManager.getMarker("OBJECT_STREAM");
	private static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	private SerializableDataInputStream stream;

	/**
	 * stream is closed or stream is null
	 */
	private boolean streamClosed;

	public SingleStreamIterator(File file) {
		try {
			stream = new SerializableDataInputStream(
					new BufferedInputStream(new FileInputStream(file)));
			log.info(LOGM_OBJECT_STREAM, "SingleStreamIterator :: reading file: {}",
					() -> file.getName());
			// read file version
			int fileVersion = stream.readInt();
			log.info(LOGM_OBJECT_STREAM, "SingleStreamIterator :: read file version: {}",
					() -> fileVersion);
		} catch (IOException e) {
			log.error(LOGM_EXCEPTION, "SingleStreamIterator :: got IOException when parse File {}",
					file.getName(), e);
			closeStream();
		}
	}

	public SingleStreamIterator(InputStream inputStream) {
		stream = new SerializableDataInputStream(
				new BufferedInputStream(inputStream));
	}

	@Override
	public boolean hasNext() {
		try {
			if (streamClosed) {
				return false;
			}
			if (stream.available() == 0) {
				// close current stream
				closeStream();
				return false;
			}
			return true;
		} catch (IOException e) {
			log.error(LOGM_EXCEPTION, "parseStream :: got IOException when readSerializable. ", e);
			closeStream();
			return false;
		}
	}

	@Override
	public T next() {
		if(!hasNext()){
			throw new NoSuchElementException();
		}
		try {
			return stream.readSerializable();
		} catch (IOException e) {
			log.error(LOGM_EXCEPTION, "parseStream :: got IOException when readSerializable. ", e);
			closeStream();
			return null;
		}
	}

	/**
	 * close current stream
	 */
	void closeStream() {
		try {
			if (stream != null) {
				stream.close();
			}
		} catch (IOException e) {
			log.error(LOGM_EXCEPTION, "SingleStreamIterator :: got IOException when closing stream. ", e);
		}
		streamClosed = true;
	}
}
