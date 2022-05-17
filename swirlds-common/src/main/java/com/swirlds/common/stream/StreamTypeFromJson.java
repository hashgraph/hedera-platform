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

package com.swirlds.common.stream;

/**
 * Contains properties related to certain stream file type.
 * The object can be built from a json file
 */
public final class StreamTypeFromJson implements StreamType {
	/**
	 * description of the streamType, used for logging
	 */
	private String description;
	/**
	 * file name extension
	 */
	private String extension;
	/**
	 * file name extension of signature file
	 */
	private String sigExtension;
	/**
	 * Header which is written in the beginning of a stream file, before writing the Object Stream Version.
	 */
	private int[] fileHeader;
	/**
	 * Header which is written in the beginning of a stream signature file, before writing the Object Stream Signature
	 * Version.
	 */
	private byte[] sigFileHeader;

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getExtension() {
		return extension;
	}

	@Override
	public String getSigExtension() {
		return sigExtension;
	}

	@Override
	public int[] getFileHeader() {
		return fileHeader;
	}

	@Override
	public byte[] getSigFileHeader() {
		return sigFileHeader;
	}
}
