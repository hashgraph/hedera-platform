/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.platform;

import static com.swirlds.logging.LogMarker.FREEZE;

import com.swirlds.common.system.EventCreationRule;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.platform.components.TransThrottleSyncRule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** The source freeze related information. */
public class FreezeManager implements TransThrottleSyncRule, EventCreationRule {

    private static final Logger log = LogManager.getLogger(FreezeManager.class);

    /** this boolean states whether events should be created or not */
    private volatile boolean freezeEventCreation = false;

    /** the current state of the system regarding freeze */
    private volatile FreezeStatus freezeStatus = FreezeStatus.NOT_IN_FREEZE;

    /** A method to call when the freeze status changes */
    private final Runnable freezeChangeMethod;

    private enum FreezeStatus {
        NOT_IN_FREEZE,
        IN_FREEZE,
        FREEZE_COMPLETE
    }

    public FreezeManager(final Runnable freezeChangeMethod) {
        this.freezeChangeMethod = freezeChangeMethod;
    }

    /**
     * Returns whether events should be created or not
     *
     * @return true if we should create events, false otherwise
     */
    public boolean isEventCreationFrozen() {
        return freezeEventCreation;
    }

    /** Sets event creation to be frozen */
    public void freezeEventCreation() {
        freezeEventCreation = true;
        log.info(FREEZE.getMarker(), "Event creation frozen");
    }

    /** Sets the system in a state a freeze. */
    public synchronized void freezeStarted() {
        if (freezeStatus != FreezeStatus.NOT_IN_FREEZE) {
            throw new IllegalStateException(
                    "Attempt to enter a freeze period from state {}" + freezeStatus);
        }
        freezeStatus = FreezeStatus.IN_FREEZE;
        freezeChangeMethod.run();
    }

    /** Returns true if the system is currently in a freeze */
    public boolean isFreezeStarted() {
        return freezeStatus == FreezeStatus.IN_FREEZE;
    }

    /**
     * Sets the system in a freeze state in which no more consensus transactions will be handled.
     * Only valid if {@link #freezeStarted()} was previously called.
     */
    public synchronized void freezeComplete() {
        if (freezeStatus != FreezeStatus.IN_FREEZE) {
            throw new IllegalStateException("Attempt to complete freeze before freeze started.");
        }
        freezeStatus = FreezeStatus.FREEZE_COMPLETE;
        freezeChangeMethod.run();
    }

    /** Returns true if the system is no longer handling consensus transactions */
    public boolean isFreezeComplete() {
        return freezeStatus == FreezeStatus.FREEZE_COMPLETE;
    }

    /** {@inheritDoc} */
    @Override
    public boolean shouldSync() {
        // the node should sync while event creation is frozen
        return isEventCreationFrozen();
    }

    /** {@inheritDoc} */
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
