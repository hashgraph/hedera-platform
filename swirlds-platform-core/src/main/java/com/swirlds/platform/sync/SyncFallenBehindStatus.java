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

package com.swirlds.platform.sync;

import com.swirlds.platform.consensus.GraphGenerations;

public enum SyncFallenBehindStatus {
	NONE_FALLEN_BEHIND,
	SELF_FALLEN_BEHIND,
	OTHER_FALLEN_BEHIND;

	public static SyncFallenBehindStatus getStatus(
			final GraphGenerations self,
			final GraphGenerations other) {
		if (other.getMaxRoundGeneration() < self.getMinRoundGeneration()) {
			return OTHER_FALLEN_BEHIND;
		}
		if (self.getMaxRoundGeneration() < other.getMinRoundGeneration()) {
			return SELF_FALLEN_BEHIND;
		}
		return NONE_FALLEN_BEHIND;
	}
}
