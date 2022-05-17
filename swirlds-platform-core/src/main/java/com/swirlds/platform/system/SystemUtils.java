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

package com.swirlds.platform.system;

import com.swirlds.logging.payloads.SystemExitPayload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.swirlds.logging.LogMarker.EXCEPTION;

public final class SystemUtils {
	private static final Logger LOG = LogManager.getLogger();

	private SystemUtils() {
	}

	/**
	 * Exits the system
	 *
	 * @param reason
	 * 		the reason for the exit
	 * @param haltRuntime
	 * 		whether to halt the java runtime or not
	 */
	public static void exitSystem(final SystemExitReason reason, final boolean haltRuntime) {
		if (reason.isError()) {
			LOG.error(EXCEPTION.getMarker(), new SystemExitPayload(reason.name(), reason.getExitCode()));
			final String exitMsg = "Exiting system, reason: " + reason;
			System.out.println(exitMsg);
		}
		System.exit(reason.getExitCode());
		if (haltRuntime) {
			Runtime.getRuntime().halt(reason.getExitCode());
		}
	}

	/**
	 * Same as {@link #exitSystem(SystemExitReason, boolean)}, but with haltRuntime set to false
	 *
	 * @see #exitSystem(SystemExitReason, boolean)
	 */
	public static void exitSystem(final SystemExitReason reason) {
		exitSystem(reason, false);
	}
}
