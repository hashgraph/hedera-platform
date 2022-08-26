/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.io.streams;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

		try (final InputStream baseStream = inputStreamBuilder.buildStream()) {
			final MerkleDataInputStream in = new MerkleDataInputStream(baseStream);
			return deserializer.deserialize(in);
		} catch (final Throwable ex) {

			LOG.error(EXCEPTION.getMarker(), "Deserialization failure. " +
					"Will re-attempt deserialization with extra debug information.", ex);

			DebuggableMerkleDataInputStream in = null;
			try (final InputStream baseStream = inputStreamBuilder.buildStream()) {
				in = new DebuggableMerkleDataInputStream(baseStream);
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
