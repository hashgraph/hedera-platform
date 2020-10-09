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

package com.swirlds.logging.payloads;

/**
 * This payload is logged when the platform status changes.
 */
public class PlatformStatusPayload extends AbstractLogPayload {

	private String oldStatus;
	private String newStatus;

	public PlatformStatusPayload() {

	}

	/**
	 * @param oldStatus
	 * 		the original status of the platform
	 * @param newStatus
	 * 		the new status of the platform
	 */
	public PlatformStatusPayload(final String message, final String oldStatus, final String newStatus) {
		super(message);
		this.oldStatus = oldStatus;
		this.newStatus = newStatus;
	}

	public String getOldStatus() {
		return oldStatus;
	}

	public void setOldStatus(String oldStatus) {
		this.oldStatus = oldStatus;
	}

	public String getNewStatus() {
		return newStatus;
	}

	public void setNewStatus(String newStatus) {
		this.newStatus = newStatus;
	}
}
