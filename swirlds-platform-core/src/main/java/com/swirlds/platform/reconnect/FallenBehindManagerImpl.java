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
package com.swirlds.platform.reconnect;

import com.swirlds.common.merkle.synchronization.settings.ReconnectSettings;
import com.swirlds.common.system.EventCreationRule;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.network.RandomGraph;
import com.swirlds.platform.sync.FallenBehindManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** A thread-safe implementation of {@link FallenBehindManager} */
public class FallenBehindManagerImpl implements FallenBehindManager, EventCreationRule {
    /** a set of all neighbors of this node */
    private final HashSet<Long> allNeighbors;
    /** the number of neighbors we have */
    private final int numNeighbors;
    /** set of neighbors who report that this node has fallen behind */
    private final HashSet<Long> reportFallenBehind;
    /**
     * set of neighbors that have not yet reported that we have fallen behind, only exists if
     * someone reports we have fallen behind. This Set is made from a ConcurrentHashMap, so it needs
     * no synchronization
     */
    private final Set<Long> notYetReportFallenBehind;
    /** Called on any fallen behind status change */
    private final Runnable notifyPlatform;
    /** Called when the status becomes fallen behind */
    private final Runnable fallenBehindCallback;

    private final ReconnectSettings settings;
    /** number of neighbors who think this node has fallen behind */
    volatile int numReportFallenBehind;

    public FallenBehindManagerImpl(
            final NodeId selfId,
            final RandomGraph connectionGraph,
            final Runnable notifyPlatform,
            final Runnable fallenBehindCallback,
            final ReconnectSettings settings) {
        notYetReportFallenBehind = ConcurrentHashMap.newKeySet();
        reportFallenBehind = new HashSet<>();
        allNeighbors = new HashSet<>();
        /* an array with all the neighbor ids */
        final int[] neighbors = connectionGraph.getNeighbors(selfId.getIdAsInt());
        numNeighbors = neighbors.length;
        for (final int neighbor : neighbors) {
            allNeighbors.add((long) neighbor);
        }
        this.notifyPlatform = notifyPlatform;
        this.fallenBehindCallback = fallenBehindCallback;
        this.settings = settings;
    }

    @Override
    public synchronized void reportFallenBehind(final NodeId id) {
        final boolean previouslyFallenBehind = hasFallenBehind();
        if (reportFallenBehind.add(id.getId())) {
            if (numReportFallenBehind == 0) {
                // we have received the first indication that we have fallen behind, so we need to
                // check with other
                // nodes to confirm
                notYetReportFallenBehind.addAll(allNeighbors);
            }
            // we don't need to check with this node
            notYetReportFallenBehind.remove(id.getId());
            numReportFallenBehind++;
            if (!previouslyFallenBehind && hasFallenBehind()) {
                notifyPlatform.run();
                fallenBehindCallback.run();
            }
        }
    }

    @Override
    public List<Long> getNeededForFallenBehind() {
        if (notYetReportFallenBehind.isEmpty()) {
            return null;
        }
        final List<Long> ret = new ArrayList<>(notYetReportFallenBehind);
        Collections.shuffle(ret);
        return ret;
    }

    @Override
    public boolean hasFallenBehind() {
        return numNeighbors * settings.getFallenBehindThreshold() < numReportFallenBehind;
    }

    @Override
    public synchronized List<Long> getNeighborsForReconnect() {
        final List<Long> ret = new ArrayList<>(reportFallenBehind);
        Collections.shuffle(ret);
        return ret;
    }

    @Override
    public synchronized void resetFallenBehind() {
        numReportFallenBehind = 0;
        reportFallenBehind.clear();
        notYetReportFallenBehind.clear();
        notifyPlatform.run();
    }

    @Override
    public int numReportedFallenBehind() {
        return numReportFallenBehind;
    }

    @Override
    public EventCreationRuleResponse shouldCreateEvent() {
        if (hasFallenBehind()) {
            return EventCreationRuleResponse.DONT_CREATE;
        }
        return EventCreationRuleResponse.PASS;
    }
}
