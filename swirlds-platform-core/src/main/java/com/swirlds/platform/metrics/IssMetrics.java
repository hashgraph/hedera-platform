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

package com.swirlds.platform.metrics;

import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.utility.CommonUtils;

import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;

/**
 * Metrics for ISS events.
 */
public class IssMetrics {

	private final IntegerGauge issCount;

	/**
	 * Constructor of {@code IssMetrics}
	 *
	 * @param metrics
	 * 		a reference to the metrics-system
	 * @throws
	 * 		IllegalArgumentException if {@code metrics} is {@code null}
	 */
	public IssMetrics(final Metrics metrics) {
		CommonUtils.throwArgNull(metrics, "metrics");
		issCount = metrics.getOrCreate(
				new IntegerGauge.Config(INTERNAL_CATEGORY, "issCount")
						.withDescription("the number nodes that currently disagree with the hash of this node's state")
		);
	}

	/**
	 * Set the number of nodes that currently disagree with the state hash computed by this node.
	 *
	 * @param issCount
	 * 		the number of nodes in an ISS state
	 */
	public void setIssCount(final int issCount) {
		this.issCount.set(issCount);
	}

}
