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

package com.swirlds.virtualmap;

/**
 * Static holder of resolved {@code virtualMap.*} settings, via an instance of {@link VirtualMapSettings}.
 */
public final class VirtualMapSettingsFactory {
	private static VirtualMapSettings virtualMapSettings;

	/**
	 * Hook that {@code Browser#populateSettingsCommon()} will use to attach the instance of
	 * {@link VirtualMapSettings} obtained by parsing <i>settings.txt</i>.
	 */
	public static void configure(final VirtualMapSettings virtualMapSettings) {
		VirtualMapSettingsFactory.virtualMapSettings = virtualMapSettings;
	}

	/**
	 * Get the configured settings for VirtualMap.
	 */
	public static VirtualMapSettings get() {
		if (virtualMapSettings == null) {
			virtualMapSettings = new DefaultVirtualMapSettings();
		}
		return virtualMapSettings;
	}

	private VirtualMapSettingsFactory() {
		/* Utility class */
	}
}
