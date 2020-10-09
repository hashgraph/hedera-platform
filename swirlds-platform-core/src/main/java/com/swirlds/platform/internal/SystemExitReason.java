/*
 * (c) 2016-2020 Swirlds, Inc.
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

package com.swirlds.platform.internal;

public enum SystemExitReason {
	BROWSER_WINDOW_CLOSED(0),
	SAVED_STATE_NOT_LOADED(200),
	STATE_RECOVER_FINISHED(0),
	SWIRLD_MAIN_THREW_EXCEPTION(201);

	private int exitCode;

	SystemExitReason(int exitCode) {
		this.exitCode = exitCode;
	}

	public int getExitCode() {
		return exitCode;
	}

	public boolean isError() {
		return exitCode != 0;
	}
}
