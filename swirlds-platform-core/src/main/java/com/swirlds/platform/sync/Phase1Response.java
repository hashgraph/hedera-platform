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

import com.swirlds.common.crypto.Hash;

import java.util.List;
import java.util.Objects;

public final class Phase1Response {
	private static final Phase1Response SYNC_REJECTED_RESPONSE = new Phase1Response(null, null);

	private final SyncGenerations generations;
	private final List<Hash> tips;

	private Phase1Response(final SyncGenerations generations, final List<Hash> tips) {
		this.generations = generations;
		this.tips = tips;
	}

	public static Phase1Response create(final SyncGenerations generations, final List<Hash> tips) {
		Objects.requireNonNull(generations, "generations cannot be null");
		Objects.requireNonNull(tips, "tips cannot be null");
		return new Phase1Response(generations, tips);
	}

	public static Phase1Response syncRejected() {
		return SYNC_REJECTED_RESPONSE;
	}

	public SyncGenerations getGenerations() {
		return generations;
	}

	public List<Hash> getTips() {
		return tips;
	}

	public boolean isSyncRejected() {
		return this == SYNC_REJECTED_RESPONSE;
	}
}
