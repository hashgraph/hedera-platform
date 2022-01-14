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

package com.swirlds.common.merkle.exceptions;

import com.swirlds.common.crypto.Hash;

import java.util.List;
import java.util.Set;

/**
 * This exception is thrown if there is an error due to the archival state of a merkle node.
 */
public class ArchivedException extends RuntimeException {

	public ArchivedException() {
	}

	public ArchivedException(final String message) {
		super(message);
	}

	public ArchivedException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public ArchivedException(final Throwable cause) {
		super(cause);
	}

	public ArchivedException(final String message, final Throwable cause, final boolean enableSuppression,
			final boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
