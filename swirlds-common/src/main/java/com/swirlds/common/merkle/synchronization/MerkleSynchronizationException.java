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

package com.swirlds.common.merkle.synchronization;

/**
 * This exception may be thrown if there is a problem during synchronization of merkle trees.
 */
public class MerkleSynchronizationException extends RuntimeException {

	public MerkleSynchronizationException(String message) {
		super(message);
	}

	public MerkleSynchronizationException(Exception ex) {
		super(ex);
	}

	public MerkleSynchronizationException(Throwable cause) {
		super(cause);
	}

	public MerkleSynchronizationException(String message, Throwable cause) {
		super(message, cause);
	}
}
