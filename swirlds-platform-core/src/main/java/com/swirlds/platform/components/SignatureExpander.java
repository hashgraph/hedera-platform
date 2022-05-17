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

package com.swirlds.platform.components;

import com.swirlds.common.Transaction;
import com.swirlds.platform.state.State;

/**
 * An object that is capable of expanding signatures on an array of transactions.
 */
@FunctionalInterface
public interface SignatureExpander {

	/**
	 * Expand the signatures on an array of transactions
	 *
	 * @param transactions
	 * 		an array of 0 or more transactions
	 * @param state
	 * 		the state of this application used to expand signatures
	 */
	void expandSignatures(final Transaction[] transactions, final State state);

}
