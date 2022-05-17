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

package com.swirlds.platform.network;

import com.swirlds.common.NodeId;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.stats.PlatformStatistics;

public interface NetworkStats extends PlatformStatistics {
	/**
	 * Notifies the stats that a new connection has been established
	 *
	 * @param connection
	 * 		a new connection
	 */
	void connectionEstablished(final SyncConnection connection);

	/**
	 * Record the ping time to this particular node
	 *
	 * @param node
	 * 		the node to which the latency is referring to
	 * @param pingNanos
	 * 		the ping time, in nanoseconds
	 */
	void recordPingTime(final NodeId node, final long pingNanos);
}
