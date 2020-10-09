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
 * The base functional interface that must be implemented by all notification listeners. Uses the default {@link
 * DispatchModel} configuration.
 *
 * @param <N>
 * 		the type of the supported {@link Notification} which is passed to the {@link #notify(Notification)} method.
 */
@FunctionalInterface
@DispatchModel
public interface Listener<N extends Notification> {

	/**
	 * Called for each {@link Notification} that this listener should handle.
	 *
	 * @param data
	 * 		the notification to be handled
	 */
	void notify(final N data);
}
