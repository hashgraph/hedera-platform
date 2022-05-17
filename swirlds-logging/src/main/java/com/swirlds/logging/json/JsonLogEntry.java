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

package com.swirlds.logging.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.swirlds.logging.payloads.LogPayload;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.time.Instant;

import static com.swirlds.logging.payloads.AbstractLogPayload.extractPayloadType;
import static com.swirlds.logging.payloads.AbstractLogPayload.parsePayload;

/**
 * A single entry in a json log file.
 */
public class JsonLogEntry {

	private final Instant timestamp;
	private final String thread;
	private final String level;
	private final String loggerName;
	private final String marker;
	private final String exceptionType;
	private final String exceptionMessage;
	private final String payload;

	public JsonLogEntry(JsonNode node) {
		long seconds = Long.parseLong(node.get("instant").get("epochSecond").asText());
		long nanoseconds = Long.parseLong(node.get("instant").get("nanoOfSecond").asText());
		timestamp = Instant.ofEpochSecond(seconds, nanoseconds);

		thread = node.get("thread").asText();
		level = node.get("level").asText();
		loggerName = node.get("loggerName").asText();
		marker = node.get("marker").get("name").asText();

		if (node.has("thrown")) {
			exceptionType = node.get("thrown").get("name").asText();

			JsonNode exceptionMessageField = node.get("thrown").get("message");
			if (exceptionMessageField != null && !exceptionMessageField.isNull()) {
				exceptionMessage = exceptionMessageField.asText();
			} else {
				exceptionMessage = "";
			}

		} else {
			exceptionType = null;
			exceptionMessage = null;
		}

		payload = node.get("message").asText();

	}

	public JsonLogEntry(Instant timestamp, String thread, String level, String loggerName, String marker,
			String exceptionType, String exceptionMessage, String payload) {
		this.timestamp = timestamp;
		this.thread = thread;
		this.level = level;
		this.loggerName = loggerName;
		this.marker = marker;
		this.exceptionType = exceptionType;
		this.exceptionMessage = exceptionMessage;
		this.payload = payload;
	}

	/**
	 * Get the timestamp of when the log message was written.
	 *
	 * @return the timestamp of when the log message was written
	 */
	public Instant getTimestamp() {
		return timestamp;
	}

	/**
	 * Get the name of the thread that wrote the message.
	 *
	 * @return the name of the thread that wrote the message
	 */
	public String getThread() {
		return thread;
	}

	/**
	 * Get the log level, e.g. "INFO", "ERROR", etc.
	 *
	 * @return the log level
	 */
	public String getLevel() {
		return level;
	}

	/**
	 * Get the name of the logger that wrote the message. Usually corresponds the class that is writing the log.
	 *
	 * @return the name of the logger that wrote the message
	 */
	public String getLoggerName() {
		return loggerName;
	}

	/**
	 * Get the name of the log marker used for this message.
	 *
	 * @return the name of the log marker used for this message
	 */
	public String getMarker() {
		return marker;
	}

	/**
	 * Check if there is an exception attached to this entry.
	 *
	 * @return whether there is an exception attached to this entry
	 */
	public boolean hasException() {
		return exceptionType != null;
	}

	/**
	 * Get the type of the exception, e.g. "java.lang.RuntimeException".
	 *
	 * @return the type of the exception
	 */
	public String getExceptionType() {
		return exceptionType;
	}

	/**
	 * Get the message contained by the exception.
	 *
	 * @return the message contained by the exception
	 */
	public String getExceptionMessage() {
		return exceptionMessage;
	}

	/**
	 * Get the payload of the log entry in its serialized form.
	 *
	 * @return the payload of the log entry in its serialized form
	 */
	public String getRawPayload() {
		return payload;
	}

	/**
	 * Get an instantiated payload object.
	 *
	 * @param type
	 * 		the expected type of the payload
	 * @param <T>
	 * 		type of the payload
	 * @return the instantiated payload object
	 */
	public <T extends LogPayload> T getPayload(Class<T> type) {
		return parsePayload(type, payload);
	}


	/**
	 * Get the payload type.
	 *
	 * @return "" if the payload is unformatted, otherwise the fully qualified class name.
	 */
	public String getPayloadType() {
		return extractPayloadType(payload);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		ToStringBuilder tsb = new ToStringBuilder(this)
				.append("timestamp", timestamp.toString())
				.append("thread", thread)
				.append("level", level)
				.append("logger", loggerName)
				.append("marker", marker)
				.append("payload", payload);

		if (hasException()) {
			tsb.append("exception type", exceptionType)
					.append("exception message", exceptionMessage);
		}

		return tsb.build();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof JsonLogEntry)) {
			return false;
		}

		if (this == o) {
			return true;
		}

		JsonLogEntry other = (JsonLogEntry) o;

		return new EqualsBuilder()
				.append(getTimestamp(), other.getTimestamp())
				.append(getThread(), other.getThread())
				.append(getLevel(), other.getLevel())
				.append(getLoggerName(), other.getLoggerName())
				.append(getMarker(), other.getMarker())
				.append(getRawPayload(), other.getRawPayload())
				.append(getExceptionType(), other.getExceptionType())
				.append(getExceptionMessage(), other.getExceptionMessage())
				.isEquals();
	}
}

