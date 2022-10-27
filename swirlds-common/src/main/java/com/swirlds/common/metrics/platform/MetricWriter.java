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

import java.io.IOException;
import java.util.List;

/**
 * Interface that needs to be implemented by all writers that write metrics data to some kind of output.
 */
interface MetricWriter {

	/**
	 * This method allows writers to prepare the file, e.g. writing some header. It is called exactly once
	 * before anything else.
	 *
	 * @param metrics
	 * 		the list of {@link Metric}-instances
	 */
	void prepareFile(List<Metric> metrics) throws IOException;

	/**
	 * This method is called periodically. Writers should go through the list of {@link Snapshot}-instances
	 * and write the cached values.
	 *
	 * @param snapshots
	 * 		the list of {@code Snapshots}
	 */
	void writeMetrics(List<Snapshot> snapshots) throws IOException;

}
