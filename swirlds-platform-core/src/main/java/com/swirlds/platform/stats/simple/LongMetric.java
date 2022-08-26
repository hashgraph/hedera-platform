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

package com.swirlds.platform.stats.simple;

import com.swirlds.common.metrics.Metric;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * A simple metric that just returns a long value
 */
public class LongMetric extends Metric {
	private static final String FORMAT = "%d";
	private final AtomicReference<LongSupplier> supplier = new AtomicReference<>();

	public LongMetric(final String category, final String name, final String description) {
		super(category, name, description, FORMAT);
	}

	@Override
	public Object getValue() {
		final LongSupplier longSupplier = supplier.get();
		if (longSupplier == null) {
			return 0;
		}
		return longSupplier.getAsLong();
	}

	public void setSupplier(final LongSupplier supplier) {
		this.supplier.set(supplier);
	}
}
