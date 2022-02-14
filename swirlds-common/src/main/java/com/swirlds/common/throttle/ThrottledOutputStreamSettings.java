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

package com.swirlds.common.throttle;

/**
 * Configuration class for {@link ThrottledOutputStream}.
 * Currently, simple implementation with just one setting, but
 * allows future settings to be added, without changing the
 * constructor and affecting old unit tests.
 */
public final class ThrottledOutputStreamSettings {

	private long bytesPerSecond;

	public ThrottledOutputStreamSettings() {
		this.bytesPerSecond = 0;
	}

	public ThrottledOutputStreamSettings setBytesPerSecond(final long bytesPerSecond) {
		this.bytesPerSecond = bytesPerSecond;
		return this;
	}

	public long getBytesPerSecond() {
		return bytesPerSecond;
	}
}
