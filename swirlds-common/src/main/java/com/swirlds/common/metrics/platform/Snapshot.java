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

package com.swirlds.common.metrics.platform;

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.utility.CommonUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.List;

/**
 * An instance of {@code Snapshot} contains the data of a single snapshot of a {@link Metric}.
 */
public final class Snapshot {

	public static Snapshot of(final PlatformMetric metric) {
		return new Snapshot(metric);
	}

	private final Metric metric;
	private final List<SnapshotValue> snapshots;

	private Snapshot(final PlatformMetric metric) {
		this.metric = CommonUtils.throwArgNull(metric, "metric");
		this.snapshots = metric.takeSnapshot();
	}

	Metric getMetric() {
		return metric;
	}

	List<SnapshotValue> getEntries() {
		return snapshots;
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.append("metric", metric)
				.append("snapshots", snapshots)
				.toString();
	}

	public record SnapshotValue(Metric.ValueType valueType, Object value) {
	}
}
