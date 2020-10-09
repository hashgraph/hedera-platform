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
 * The base interface that must be implemented by all notifications sent to registered listeners.
 */
public interface Notification {

	/**
	 * Getter that returns a unique value representing the {@link Notification} specific sequence or system-wide
	 * notification order.
	 *
	 * @return a long value representing a unique sequence or notification order
	 */
	long getSequence();

	/**
	 * Setter for defining the unique value representing the {@link Notification} specific sequence or system-wide
	 * notification order.
	 *
	 * @param id
	 * 		a long value representing a unique sequence or notification order
	 */
	void setSequence(final long id);

}
