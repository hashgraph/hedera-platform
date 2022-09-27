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

package com.swirlds.platform.stats.cycle;

import com.swirlds.common.metrics.FloatFormats;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.utility.CommonUtils;

/**
 * A metric that is used to output a percentage 0-100%
 */
public abstract class PercentageMetric extends Metric {
	private static final String APPENDIX = " (%)";
	private static final int PERCENT = 100;

	protected PercentageMetric(final String category, final String name, final String description) {
		super(category, name + APPENDIX, description, FloatFormats.FORMAT_3_1);
		CommonUtils.throwArgNull(name, "name");
	}

	public static double calculatePercentage(final int total, final int part){
		if (total == 0) {
			return 0;
		}
		return (((double) part) / total) * PERCENT;
	}
}
