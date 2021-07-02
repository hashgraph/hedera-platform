/*
 * (c) 2016-2021 Swirlds, Inc.
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

import java.io.File;

public interface StreamType {

	/**
	 * get the description of the streamType, used for logging
	 * @return the description of the streamType, used for logging
	 */
	String getDescription();

	/**
	 * get file name extension
	 * @return file name extension
	 */
	String getExtension();

	/**
	 * get file name extension of signature file
	 * @return file name extension of signature file
	 */
	String getSigExtension();

	/**
	 * get the header which is written in the beginning of a stream file,
	 * before writing the Object Stream Version
	 * @return stream file header
	 */
	int[] getFileHeader();

	/**
	 * get the header which is written in the beginning of a stream signature file,
	 * before writing the Object Stream Signature Version
	 * @return signature file header
	 */
	byte[] getSigFileHeader();

	/**
	 * check if the file with this name is a stream file of this type
	 * @param fileName a file's name
	 * @return whether the file with this name is a stream file of this type
	 */
	default boolean isStreamFile(final String fileName) {
		return fileName.endsWith(getExtension());
	}

	/**
	 * check if the given file is a stream file of this type
	 * @param file a file
	 * @return whether the file is a stream file of this type
	 */
	default boolean isStreamFile(final File file) {
		return file != null && isStreamFile(file.getName());
	}

	/**
	 * check if the given file is a signature file of this stream type
	 * @param fileName a file's name
	 * @return whether the file is a signature file of this stream type
	 */
	default boolean isStreamSigFile(final String fileName) {
		return fileName.endsWith(getSigExtension());
	}

	/**
	 * check if the given file is a signature file of this stream type
	 * @param file a file
	 * @return whether the file is a signature file of this stream type
	 */
	default boolean isStreamSigFile(final File file) {
		return file != null && isStreamSigFile(file.getName());
	}
}

