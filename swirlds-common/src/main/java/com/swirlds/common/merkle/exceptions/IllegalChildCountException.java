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

public class IllegalChildCountException extends IllegalArgumentException {
	public IllegalChildCountException(long classId, int version, int minimumChildCount, int maximumChildCount,
			int givenChildCount) {
		super(String.format("Node with class ID %d(0x%08X) at version %d requires at least %d children and no " +
						"more than %d children, but %d children were provided.",
				classId, classId, version, minimumChildCount, maximumChildCount, givenChildCount));
	}
}
