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

package com.swirlds.platform.state;

import com.swirlds.common.system.AddressBook;

/**
 * Contains information about a signed state. A SignedStateInfo object is still ok to read after the
 * parent SignedState object has been deleted.
 */
public interface SignedStateInfo {

	/**
	 * The greatest round number such that all famous witnesses are known for it and all earlier rounds.
	 *
	 * @return the last round number
	 */
	long getLastRoundReceived();

	/**
	 * Return the set of signatures collected so far for the hash of this SignedState. This includes the
	 * signature by self.
	 *
	 * @return the set of signatures
	 */
	SigSet getSigSet();

	AddressBook getAddressBook();

}
