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

package com.swirlds.platform.system;

public enum SystemExitReason {
	BROWSER_WINDOW_CLOSED(0),
	STATE_RECOVER_FINISHED(0),
	SAVED_STATE_NOT_LOADED(200),
	SWIRLD_MAIN_THREW_EXCEPTION(201),
	/** This node has fallen behind but can not reconnect due to policy. */
	BEHIND_RECONNECT_DISABLED(202),
	/** This node exceeded the maximum consecutive failed reconnect attempts. */
	RECONNECT_FAILURE(203),
	/** An issue occurred while loading keys from .pfx files */
	KEY_LOADING_FAILED(204);

	private final int exitCode;

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
