/*
 * (c) 2016-2022 Swirlds, Inc.
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

/**
 * Contains properties related to EventStream file type;
 * Its constructor is private. Users need to use the singleton to denote this type
 */
public final class EventStreamType implements StreamType {
	/**
	 * description of the streamType, used for logging
	 */
	public static final String EVENT_DESCRIPTION = "events";
	/**
	 * file name extension
	 */
	public static final String EVENT_EXTENSION = "evts";
	/**
	 * file name extension of signature file
	 */
	public static final String EVENT_SIG_EXTENSION = "evts_sig";
	/**
	 * Header which is written in the beginning of a stream file, before writing the Object Stream Version.
	 * the int in fileHeader denotes version 5
	 */
	private static final int[] EVENT_FILE_HEADER = new int[] { 5 };
	/**
	 * Header which is written in the beginning of a stream signature file, before writing the Object Stream Signature
	 * Version.
	 * the byte in sigFileHeader denotes version 5
	 */
	private static final byte[] EVENT_SIG_FILE_HEADER = new byte[] { 5 };

	/**
	 * a singleton denotes EventStreamType
	 */
	public static final EventStreamType EVENT = new EventStreamType();

	private EventStreamType() {}

	@Override
	public String getDescription() {
		return EVENT_DESCRIPTION;
	}

	@Override
	public String getExtension() {
		return EVENT_EXTENSION;
	}

	@Override
	public String getSigExtension() {
		return EVENT_SIG_EXTENSION;
	}

	@Override
	public int[] getFileHeader() {
		return EVENT_FILE_HEADER;
	}

	@Override
	public byte[] getSigFileHeader() {
		return EVENT_SIG_FILE_HEADER;
	}
}
