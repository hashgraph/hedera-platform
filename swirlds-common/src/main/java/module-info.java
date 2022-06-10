/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */
module com.swirlds.common {

	/* Exported packages. This list should remain alphabetized. */
	exports com.swirlds.common;
	exports com.swirlds.common.bloom;
	exports com.swirlds.common.bloom.hasher;
	exports com.swirlds.common.constructable;
	exports com.swirlds.common.crypto;
	exports com.swirlds.common.exceptions;
	exports com.swirlds.common.io;
	exports com.swirlds.common.io.exceptions;
	exports com.swirlds.common.io.extendable;
	exports com.swirlds.common.io.extendable.extensions;
	exports com.swirlds.common.io.streams;
	exports com.swirlds.common.merkle;
	exports com.swirlds.common.merkle.copy;
	exports com.swirlds.common.merkle.exceptions;
	exports com.swirlds.common.merkle.hash;
	exports com.swirlds.common.merkle.iterators;
	exports com.swirlds.common.merkle.route;
	exports com.swirlds.common.merkle.synchronization;
	exports com.swirlds.common.merkle.synchronization.internal;
	exports com.swirlds.common.merkle.synchronization.settings;
	exports com.swirlds.common.merkle.synchronization.streams;
	exports com.swirlds.common.merkle.synchronization.utility;
	exports com.swirlds.common.merkle.synchronization.views;
	exports com.swirlds.common.merkle.utility;
	exports com.swirlds.common.notification;
	exports com.swirlds.common.notification.listeners;
	exports com.swirlds.common.settings;
	exports com.swirlds.common.stream;
	exports com.swirlds.common.system;
	exports com.swirlds.common.system.events;
	exports com.swirlds.common.system.transaction;
	exports com.swirlds.common.threading;
	exports com.swirlds.common.threading.framework;
	exports com.swirlds.common.threading.framework.config;
	exports com.swirlds.common.threading.futures;
	exports com.swirlds.common.threading.interrupt;
	exports com.swirlds.common.threading.locks;
	exports com.swirlds.common.threading.pool;
	exports com.swirlds.common.utility;
	exports com.swirlds.common.utility.throttle;

	/* Targeted exports */
	exports com.swirlds.common.system.transaction.internal to com.swirlds.platform, com.swirlds.common.test,
			com.swirlds.platform.test;
	exports com.swirlds.common.internal to com.swirlds.platform, com.swirlds.platform.test,
			com.swirlds.common.test, com.swirlds.jrs, com.swirlds.demo.platform;
	exports com.swirlds.common.crypto.internal to com.swirlds.platform, com.swirlds.common.test;
	exports com.swirlds.common.notification.internal to com.swirlds.common.test;
	exports com.swirlds.common.signingtool to com.swirlds.common.test, com.swirlds.demo.platform,
			com.swirlds.jrs;
	exports com.swirlds.common.crypto.engine to com.swirlds.common.test;

	opens com.swirlds.common.crypto to com.fasterxml.jackson.databind;
	opens com.swirlds.common.merkle.utility to com.fasterxml.jackson.databind;
	opens com.swirlds.common.utility.throttle to com.fasterxml.jackson.databind;
	opens com.swirlds.common.stream to com.fasterxml.jackson.databind;
	exports com.swirlds.common.statistics;
	exports com.swirlds.common.statistics.internal to com.swirlds.common.test, com.swirlds.demo.platform,
			com.swirlds.platform, com.swirlds.platform.test, com.swirlds.jrs;
	opens com.swirlds.common.merkle.copy to com.fasterxml.jackson.databind;
	exports com.swirlds.common.io.streams.internal to com.swirlds.platform.test;
	exports com.swirlds.common.io.extendable.extensions.internal to com.swirlds.common.test;

	requires com.swirlds.logging;

	requires java.desktop;

	/* Cryptography Libraries */
	requires lazysodium.java;
	requires org.bouncycastle.provider;

	/* Logging Libraries */
	requires org.apache.logging.log4j;
	requires org.apache.logging.log4j.core;
	requires org.slf4j;

	/* Utilities */
	requires io.github.classgraph;
	requires org.apache.commons.lang3;
	requires org.apache.commons.codec;

	/* Jackson JSON */
	requires com.fasterxml.jackson.core;
	requires com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.datatype.jsr310;
}
