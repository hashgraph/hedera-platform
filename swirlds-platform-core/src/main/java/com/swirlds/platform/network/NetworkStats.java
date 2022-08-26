/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.network;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.Connection;
import com.swirlds.platform.stats.PlatformStatistics;

public interface NetworkStats extends PlatformStatistics {
	/**
	 * Notifies the stats that a new connection has been established
	 *
	 * @param connection
	 * 		a new connection
	 */
	void connectionEstablished(final Connection connection);

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
