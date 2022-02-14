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

package com.swirlds.platform.reconnect;

import com.swirlds.common.merkle.synchronization.settings.ReconnectSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.function.Supplier;

import static com.swirlds.logging.LogMarker.RECONNECT;

/**
 * This object is responsible for restricting the frequency of reconnects (in the role of the sender).
 */
public class ReconnectThrottle {

	private static final Logger log = LogManager.getLogger();

	/**
	 * Reconnect settings for this node.
	 */
	private final ReconnectSettings settings;

	/**
	 * A map from node IDs to reconnect times. Nodes not in this map have either never reconnected or
	 * have reconnected only in the distant past.
	 */
	private final HashMap<Long, Instant> lastReconnectTime;

	/**
	 * The node that is currently reconnecting, or null of no node is currently reconnecting.
	 */
	private Long reconnectingNode;

	/**
	 * A method used to get the current time. Useful to have for debugging.
	 */
	private Supplier<Instant> currentTime;

	public ReconnectThrottle(final ReconnectSettings settings) {
		this.settings = settings;
		lastReconnectTime = new HashMap<>();
		reconnectingNode = null;
		currentTime = Instant::now;
	}

	/**
	 * Prune records of old reconnect attempts that no longer need to be tracked.
	 */
	private void forgetOldReconnects(final Instant now) {
		final Iterator<Instant> iterator = lastReconnectTime.values().iterator();

		while (iterator.hasNext()) {
			final Duration elapsed = Duration.between(iterator.next(), now);
			if (settings.getMinimumTimeBetweenReconnects().minus(elapsed).isNegative()) {
				iterator.remove();
			}
		}
	}

	/**
	 * Check if it is ok to reconnect (in the role of the sender) with a given node,
	 * and if so begin tracking that reconnect.
	 *
	 * @param nodeId
	 * 		the ID of the node that is behind and needs to reconnect
	 * @return true if the reconnect can proceed, false if reconnect is disallowed by policy
	 */
	public synchronized boolean initiateReconnect(final long nodeId) {
		if (reconnectingNode != null) {
			log.info(RECONNECT.getMarker(), "This node is actively helping node {} to reconnect, rejecting " +
					"concurrent reconnect request from node {}", reconnectingNode, nodeId);
			return false;
		}

		final Instant now = currentTime.get();

		forgetOldReconnects(now);
		if (lastReconnectTime.containsKey(nodeId)) {
			log.info(RECONNECT.getMarker(), "Rejecting reconnect request from node {} " +
					"due to a previous reconnect attempt at {}", nodeId, lastReconnectTime.get(nodeId));
			return false;
		}
		reconnectingNode = nodeId;
		lastReconnectTime.put(nodeId, now);
		return true;
	}

	/**
	 * Signal that the ongoing reconnect has finished.
	 */
	public synchronized void markReconnectFinished() {
		reconnectingNode = null;
	}

	/**
	 * Get the number of nodes that have recently reconnected.
	 */
	public int getNumberOfRecentReconnects() {
		return lastReconnectTime.size();
	}

	public void setCurrentTime(final Supplier<Instant> currentTime) {
		this.currentTime = currentTime;
	}
}
