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

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows per {@link Listener} configuration of {@link DispatchMode} and {@link DispatchOrder}. Default configuration is
 * {@link DispatchMode#SYNC} and {@link DispatchOrder#UNORDERED}.
 */
@Inherited
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface DispatchModel {
	/**
	 * Specifies the {@link DispatchMode} to be used for all {@link Notification} implementations supported by this
	 * listener.
	 *
	 * The default is {@link DispatchMode#SYNC}.
	 *
	 * @return the configured {@link DispatchMode}
	 */
	DispatchMode mode() default DispatchMode.SYNC;

	/**
	 * Specifies the {@link DispatchOrder} to be used for all {@link Notification} implementations supported by this
	 * listener.
	 *
	 * The default is {@link DispatchOrder#UNORDERED}.
	 *
	 * @return the configured {@link DispatchOrder}
	 */
	DispatchOrder order() default DispatchOrder.UNORDERED;
}
