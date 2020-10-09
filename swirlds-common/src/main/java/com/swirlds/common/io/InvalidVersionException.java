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

package com.swirlds.common.io;

/**
 * Exception that is caused when illegal version is read from the stream
 */
public class InvalidVersionException extends IllegalArgumentException {
	public InvalidVersionException(final int expectedVersion, final int version) {
		super(String.format("Illegal version %d was read from the stream. Expected %d", version, expectedVersion));
	}

	public InvalidVersionException(final int minimumVersion, final int maximumVersion, final int version) {
		super(String.format("Illegal version %d was read from the stream. Expected version in the range %d - %d",
				version, minimumVersion, maximumVersion));
	}

	public InvalidVersionException(final int version, SerializableDet object) {
		super(String.format(
				"Illegal version %d was read from the stream for %s (class ID %d(0x%08X)). Expected version in the " +
						"range %d - %d",
				version, object.getClass(), object.getClassId(), object.getClassId(),
				object.getMinimumSupportedVersion(), object.getVersion()));
	}
}
