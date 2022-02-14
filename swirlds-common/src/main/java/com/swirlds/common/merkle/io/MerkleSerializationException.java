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
package com.swirlds.common.merkle.io;

import com.swirlds.common.merkle.MerkleNode;

import java.io.IOException;

import static com.swirlds.common.constructable.ClassIdFormatter.versionedClassIdString;

/**
 * This exception can be thrown during serialization or deserialization of a merkle tree.
 */
public class MerkleSerializationException extends IOException {

	public MerkleSerializationException() {
	}

	public MerkleSerializationException(final String message) {
		super(message);
	}

	/**
	 * Throw an exception for a particular node.
	 *
	 * @param message
	 * 		the message to be included in the exception
	 * @param node
	 * 		the node that caused the exception
	 */
	public MerkleSerializationException(final String message, final MerkleNode node) {
		super(message + " [" + versionedClassIdString(node) + "]");
	}

	public MerkleSerializationException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public MerkleSerializationException(final Throwable cause) {
		super(cause);
	}
}
