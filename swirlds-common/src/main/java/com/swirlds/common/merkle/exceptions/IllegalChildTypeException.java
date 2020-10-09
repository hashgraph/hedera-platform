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

package com.swirlds.common.merkle.exceptions;

/**
 * This exception is thrown when a child is added to a MerkleInternal node that is not of the expected type.
 */
public class IllegalChildTypeException extends IllegalArgumentException {
	public IllegalChildTypeException(int index, long classId, int version, final long parentClassId) {
		super(String.format("Invalid class ID %d(0x%08X) at index %d for version %d for parent with class id %d(0x%08X)",
				classId,
				classId,
				index,
				version,
				parentClassId,
				parentClassId));
	}
}
