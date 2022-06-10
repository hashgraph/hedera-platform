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

package com.swirlds.common.io.streams;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * Utility methods for debugging streams.
 */
public final class StreamDebugUtils {

	private static final Logger LOG = LogManager.getLogger(StreamDebugUtils.class);

	private StreamDebugUtils() {

	}

	/**
	 * Describes a method that builds an input stream.
	 */
	public interface InputStreamBuilder {
		/**
		 * Build an input stream.
		 *
		 * @return an input stream
		 */
		InputStream buildStream() throws IOException;
	}

	/**
	 * Describes a method that deserializes an object.
	 *
	 * @param <T>
	 * 		the type of the object being deserialized
	 */
	@FunctionalInterface
	public interface Deserializer<T> {
		/**
		 * Deserialize an object.
		 *
		 * @param inputStream
		 * 		the stream to deserialize from
		 * @return the object that was deserialized
		 */
		T deserialize(final MerkleDataInputStream inputStream) throws IOException;
	}

	/**
	 * Attempt to perform deserialization. If the deserialization fails then retry with debug enabled, log the
	 * debug info, and call the failure callback. Rethrows any exceptions encountered internally.
	 *
	 * @param inputStreamBuilder
	 * 		builds the input stream. The stream is closed when finished.
	 * @param deserializer
	 * 		the method that performs the deserialization
	 * @param failureCallback
	 * 		a method that is invoked if serialization fails, ignored if null
	 * @param <T>
	 * 		the type of object being deserialized
	 * @return the deserialized objects (if no errors are encountered)
	 */
	public static <T> T deserializeAndDebugOnFailure(
			final InputStreamBuilder inputStreamBuilder,
			final Deserializer<T> deserializer,
			final Runnable failureCallback) throws IOException {
		return deserializeAndDebugOnFailure(inputStreamBuilder, null, deserializer, failureCallback);
	}

	/**
	 * Attempt to perform deserialization. If the deserialization fails then retry with debug enabled, log the
	 * debug info, and call the failure callback. Rethrows any exceptions encountered internally.
	 *
	 * @param inputStreamBuilder
	 * 		builds the input stream. The stream is closed when finished.
	 * @param externalDirectory
	 * 		the location of external data, ignored if null
	 * @param deserializer
	 * 		the method that performs the deserialization
	 * @param failureCallback
	 * 		a method that is invoked if serialization fails, ignored if null
	 * @param <T>
	 * 		the type of object being deserialized
	 * @return the deserialized objects (if no errors are encountered)
	 */
	public static <T> T deserializeAndDebugOnFailure(
			final InputStreamBuilder inputStreamBuilder,
			final File externalDirectory,
			final Deserializer<T> deserializer,
			final Runnable failureCallback) throws IOException {

		try (final InputStream baseStream = inputStreamBuilder.buildStream()) {
			final MerkleDataInputStream in = new MerkleDataInputStream(baseStream, externalDirectory);
			return deserializer.deserialize(in);
		} catch (final Throwable ex) {

			LOG.error(EXCEPTION.getMarker(), "Deserialization failure. " +
					"Will re-attempt deserialization with extra debug information.", ex);

			DebuggableMerkleDataInputStream in = null;
			try (final InputStream baseStream = inputStreamBuilder.buildStream()) {
				in = new DebuggableMerkleDataInputStream(baseStream, externalDirectory);
				deserializer.deserialize(in);
			} catch (final Throwable innerEx) {
				LOG.error(EXCEPTION.getMarker(),
						"Deserialization re-attempt also encountered a failure.", innerEx);
			} finally {
				if (in != null) {
					LOG.error(EXCEPTION.getMarker(), "Deserialization stack trace:\n{}",
							in.getFormattedStackTrace());
				}
				if (failureCallback != null) {
					failureCallback.run();
				}
			}

			throw ex;
		}
	}

}
