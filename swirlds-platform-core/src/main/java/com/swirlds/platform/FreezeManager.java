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

package com.swirlds.platform;


import com.swirlds.common.EventCreationRule;
import com.swirlds.common.EventCreationRuleResponse;
import com.swirlds.common.NodeId;
import com.swirlds.common.Transaction;
import com.swirlds.platform.components.TransThrottleSyncRule;
import com.swirlds.platform.observers.EventAddedObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.swirlds.common.TransactionType.SYS_TRANS_STATE_SIG_FREEZE;
import static com.swirlds.logging.LogMarker.FREEZE;

/**
 * A class that handles all freeze related functionality
 * It only uses consensus time for determining if and when to initiate a freeze.
 */
public class FreezeManager implements EventAddedObserver, TransThrottleSyncRule, EventCreationRule {
	private static final Logger log = LogManager.getLogger();
	/** this boolean states whether events should be created or not */
	private volatile boolean freezeEventCreation = false;

	/** ID of the platform that owns this FreezeManager */
	private final NodeId selfId;

	/** A method to call when the freeze status changes */
	private final Runnable freezeChangeMethod;

	FreezeManager(final NodeId selfId,
			final Runnable freezeChangeMethod) {
		this.selfId = selfId;
		this.freezeChangeMethod = freezeChangeMethod;
	}

	/**
	 * Returns whether events should be created or not
	 *
	 * @return true if we should create events, false otherwise
	 */
	boolean isEventCreationFrozen() {
		return freezeEventCreation;
	}

	boolean shouldEnterMaintenance() {
		return freezeEventCreation;
	}

	/**
	 * Sets event creation to be frozen
	 */
	void setEventCreationFrozen() {
		freezeEventCreation = true;
		log.info(FREEZE.getMarker(), "Event creation frozen in platform {}", selfId);
		freezeChangeMethod.run();
	}

	@Override
	public void eventAdded(EventImpl event) {
		if (!event.isCreatedBy(selfId)) {
			// we only freeze event creation when we have created an event with a freeze transaction
			return;
		}
		final Transaction[] transactions = event.getTransactions();
		if (transactions == null) {
			return;
		}
		// Check all the system transactions that are put in an Event. If there is a
		// SYS_TRANS_STATE_SIG_FREEZE transaction then invoke callback.
		for (Transaction trans : transactions) {
			if (trans.getTransactionType() == SYS_TRANS_STATE_SIG_FREEZE) {
				setEventCreationFrozen();
				break;
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean shouldSync() {
		// the node should sync while event creation is frozen
		return isEventCreationFrozen();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EventCreationRuleResponse shouldCreateEvent() {
		// the node should not create event while event creation is frozen
		if (isEventCreationFrozen()) {
			return EventCreationRuleResponse.DONT_CREATE;
		} else {
			return EventCreationRuleResponse.PASS;
		}
	}
}
