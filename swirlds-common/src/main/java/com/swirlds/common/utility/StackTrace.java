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

package com.swirlds.common.utility;

/**
 * This class is a convenience wrapper around for the java stack trace.
 */
public record StackTrace(StackTraceElement[] frames) {

	/**
	 * Convenience method for getting a stack trace of the current thread.
	 *
	 * @return a stack grace
	 */
	public static StackTrace getStackTrace() {
		return new StackTrace(ignoreFrames(Thread.currentThread().getStackTrace(), 2));
	}

	/**
	 * Convenience method for getting the stack trace of a thread.
	 *
	 * @param thread
	 * 		the target thread
	 * @return a stack trace
	 */
	public static StackTrace getStackTrace(final Thread thread) {
		return new StackTrace(thread.getStackTrace());
	}

	/**
	 * Extract a stack trace from a throwable
	 *
	 * @param t
	 * 		a throwable
	 * @return a stack trace
	 */
	public static StackTrace getStackTrace(final Throwable t) {
		return new StackTrace(t.getStackTrace());
	}

	/**
	 * Return a list of stack trace elements, less a few ignored frames at the beginning.
	 *
	 * @param frames
	 * 		an array of frames
	 * @param ignoredFrames
	 * 		the number of frames to remove from the beginning
	 * @return a new array that does not contain the ignored frames
	 */
	private static StackTraceElement[] ignoreFrames(final StackTraceElement[] frames, final int ignoredFrames) {
		if (frames.length <= ignoredFrames) {
			return new StackTraceElement[0];
		}
		final StackTraceElement[] reducedFrames = new StackTraceElement[frames.length - ignoredFrames];
		System.arraycopy(frames, ignoredFrames, reducedFrames, 0, reducedFrames.length);
		return reducedFrames;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		for (final StackTraceElement element : frames) {
			if (element != frames[0]) {
				sb.append("\tat ");
			}
			sb.append(element.getClassName())
					.append(".").append(element.getMethodName())
					.append("(").append(element.getFileName()).append(":").append(element.getLineNumber()).append(")")
					.append("\n");
		}
		return sb.toString();
	}
}
