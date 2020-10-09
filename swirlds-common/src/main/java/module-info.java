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
module com.swirlds.common {
	exports com.swirlds.common;
	exports com.swirlds.common.classscan;
	exports com.swirlds.common.constructable;
	exports com.swirlds.common.crypto;
	exports com.swirlds.common.futures;
	exports com.swirlds.common.io;
	exports com.swirlds.common.io.extendable;
	exports com.swirlds.common.notification;
	exports com.swirlds.common.notification.listeners;
	exports com.swirlds.common.threading;

	exports com.swirlds.common.merkle;
	exports com.swirlds.common.merkle.exceptions;
	exports com.swirlds.common.merkle.hash;
	exports com.swirlds.common.merkle.io;
	exports com.swirlds.common.merkle.iterators;
	exports com.swirlds.common.merkle.route;
	exports com.swirlds.common.merkle.synchronization;
	exports com.swirlds.common.merkle.utility;

	exports com.swirlds.common.events;
	exports com.swirlds.common.internal to com.swirlds.platform, com.swirlds.fcmap, com.swirlds.fcmap.test,
			com.swirlds.platform.test, com.swirlds.common.test;
	exports com.swirlds.common.list to com.swirlds.platform, com.swirlds.fcmap, com.swirlds.fcqueue;
	exports com.swirlds.common.crypto.internal to com.swirlds.platform, com.swirlds.common.test;
	exports com.swirlds.common.testutils to com.swirlds.platform, com.swirlds.common.test;
	exports com.swirlds.common.notification.internal to com.swirlds.common.test;
	exports com.swirlds.common.stream;

	opens com.swirlds.common.crypto to com.fasterxml.jackson.databind;
	opens com.swirlds.common.merkle.utility to com.fasterxml.jackson.databind;

	requires com.swirlds.logging;

	requires java.desktop;

	/* Cryptography Libraries */
	requires lazysodium.java;
	requires jocl;

	/* Logging Libraries */
	requires org.apache.logging.log4j;
	requires org.apache.logging.log4j.core;
	requires org.slf4j;

	/* Utilities */
	requires io.github.classgraph;
	requires org.apache.commons.lang3;

	/* Jackson JSON */
	requires com.fasterxml.jackson.core;
	requires com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.datatype.jsr310;
}
