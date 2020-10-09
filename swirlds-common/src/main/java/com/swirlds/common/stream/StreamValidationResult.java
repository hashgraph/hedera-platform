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

/**
 * Result of validating stream files and stream signature files
 */
public enum StreamValidationResult {
	/**
	 * Fail to parse stream file
	 */
	PARSE_STREAM_FILE_FAIL,
	/**
	 * stream file doesn't contain any content
	 */
	STREAM_FILE_EMPTY,
	/**
	 * stream file doesn't contain initialRunningHash
	 */
	STREAM_FILE_MISS_INITIAL_HASH,
	/**
	 * stream file doesn't contain objects
	 */
	STREAM_FILE_MISS_OBJECTS,
	/**
	 * stream file doesn't contain lastRunningHash
	 */
	STREAM_FILE_MISS_LAST_HASH,
	/**
	 * lastRunningHash in the stream file doesn't match the calculated result from initialRunningHash and objects
	 */
	CALCULATED_LAST_HASH_NOT_MATCH,

	/**
	 * fail to parse signature file
	 */
	PARSE_SIG_FILE_FAIL,
	/**
	 * signature bytes in the signature file doesn't match lastRunningHash in the stream file
	 */
	SIG_NOT_MATCH_FILE,
	/**
	 * number of signature files is not equal to number of stream files
	 */
	SIG_FILE_COUNT_MISMATCH,
	/**
	 * initialRunningHash read from stream file doesn't match expected Hash
	 */
	INITIAL_HASH_NOT_MATCH,
	/**
	 * pass validation
	 */
	OK
}
