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

package com.swirlds.common.internal;

import com.swirlds.common.PlatformException;
import com.swirlds.logging.LogMarker;

public class ConfigurationException extends PlatformException {

	public ConfigurationException() {
		super(LogMarker.EXCEPTION);
	}

	public ConfigurationException(final String message) {
		super(message, LogMarker.EXCEPTION);
	}

	public ConfigurationException(final String message, final Throwable cause) {
		super(message, cause, LogMarker.EXCEPTION);
	}

	public ConfigurationException(final Throwable cause) {
		super(cause, LogMarker.EXCEPTION);
	}

}
