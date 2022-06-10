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

package com.swirlds.common.io.streams.internal;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Objects;

/**
 * Records information for recently deserialized objects. Used to print diagnostics when a serialization
 * problem is encountered.
 */
public class SerializationStack {

	final Deque<SerializationStackElement> stack = new LinkedList<>();

	public SerializationStack(final StackTraceElement callLocation) {
		stack.addLast(new SerializationStackElement(SerializationOperation.STREAM_OPENED, callLocation));
	}

	/**
	 * Mark the start of an operation.
	 *
	 * @param operation
	 * 		the operation that is starting
	 * @param callLocation
	 * 		describes where this serialization operation was initiated
	 */
	public void startOperation(final SerializationOperation operation, final StackTraceElement callLocation) {
		stack.addLast(new SerializationStackElement(operation, callLocation));
	}

	/**
	 * Mark the finish of an operation.
	 */
	public void finishOperation() {
		final SerializationStackElement finished = stack.removeLast();

		final SerializationStackElement parent = Objects.requireNonNull(stack.getLast(),
				"stack should never be empty");
		parent.addChild(finished);
	}

	/**
	 * Set the class ID of the current serializable object being deserialized, once it becomes known.
	 *
	 * @param classId
	 * 		the object's class ID
	 */
	public void setClassId(final long classId) {
		final SerializationStackElement element = Objects.requireNonNull(stack.getLast(),
				"stack should never be empty");
		element.setClassId(classId);
	}

	/**
	 * Set the class of the current serializable object being deserialized, once it becomes known.
	 *
	 * @param clazz
	 * 		the objects class
	 */
	public void setClass(final Class<?> clazz) {
		final SerializationStackElement element = Objects.requireNonNull(stack.getLast(),
				"stack should never be empty");
		element.setClass(clazz);
	}

	/**
	 * Set the string representation of the value. Should be very short.
	 *
	 * @param string
	 * 		a string representing the object read
	 */
	public void setStringRepresentation(final String string) {
		final SerializationStackElement element = Objects.requireNonNull(stack.getLast(),
				"stack should never be empty");
		element.setStringRepresentation(string);
	}

	/**
	 * Generate the stack trace for the serialization stack.
	 */
	public String generateSerializationStackTrace() {
		// Add all children to their parents.
		while (stack.size() > 1) {
			finishOperation();
		}

		// Write the stack trace.
		final StringBuilder sb = new StringBuilder();
		final SerializationStackElement root = Objects.requireNonNull(stack.getLast(),
				"stack should never be empty");
		root.writeStackTrace(sb, 0);

		return sb.toString();
	}

	/**
	 * Get the internal stack of stack trace elements.
	 */
	public Deque<SerializationStackElement> getInternalStack() {
		return stack;
	}
}