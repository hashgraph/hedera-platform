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

package com.swirlds.common.notification;

/**
 * Abstract base class provided for convenience of implementing {@link Notification} classes. Provides the basic sequence
 * support as required by the {@link Notification} interface.
 */
public abstract class AbstractNotification implements Notification {

	private long sequence;

	public AbstractNotification() {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getSequence() {
		return sequence;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setSequence(final long id) {
		this.sequence = id;
	}

}
