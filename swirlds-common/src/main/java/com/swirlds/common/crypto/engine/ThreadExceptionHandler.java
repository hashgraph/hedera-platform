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

package com.swirlds.common.crypto.engine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.swirlds.common.crypto.engine.CryptoEngine.LOGM_EXCEPTION;

/**
 * Custom {@link Thread.UncaughtExceptionHandler} implementation that logs the caught exception as if the provided {@link
 * Class} had logged it directly.
 */
public class ThreadExceptionHandler implements Thread.UncaughtExceptionHandler {

	private final Logger log;

	/**
	 * Constructor that accepts the {@link Class} to be used for all logging output.
	 *
	 * @param sourceClass
	 * 		the class under which all logging should appear to originate
	 */
	public ThreadExceptionHandler(final Class<?> sourceClass) {
		this.log = LogManager.getLogger(sourceClass);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void uncaughtException(final Thread t, final Throwable ex) {
		log.error(LOGM_EXCEPTION, String.format("Intercepted Uncaught Exception [ threadName = '%s' ]", t.getName()),
				ex);
	}

}
