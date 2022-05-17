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
/**
 * The Swirlds public API module used by platform applications.
 */
module com.swirlds.platform {

	/* Public Package Exports */
	exports com.swirlds.platform;
	exports com.swirlds.platform.state;

	/* Targeted Exports to External Libraries */
	exports com.swirlds.platform.event to com.swirlds.platform.test, com.swirlds.common, com.fasterxml.jackson.core,
			com.fasterxml.jackson.databind;
	exports com.swirlds.platform.internal to com.swirlds.platform.test, com.fasterxml.jackson.core,
			com.fasterxml.jackson.databind;
	exports com.swirlds.platform.swirldapp to com.swirlds.platform.test;
	exports com.swirlds.platform.stats;
	exports com.swirlds.platform.components to com.swirlds.platform.test;
	exports com.swirlds.platform.observers to com.swirlds.platform.test;
	exports com.swirlds.platform.eventhandling;
	exports com.swirlds.platform.sync;
	exports com.swirlds.platform.consensus to com.swirlds.platform.test;
	exports com.swirlds.platform.system;
	exports com.swirlds.platform.crypto to com.swirlds.platform.test;
	exports com.swirlds.platform.network;
	exports com.swirlds.platform.network.unidirectional;
	exports com.swirlds.platform.network.connectivity;
	exports com.swirlds.platform.network.connection;
	exports com.swirlds.platform.network.topology;
	exports com.swirlds.platform.chatter.protocol.messages;
	exports com.swirlds.platform.chatter.protocol.input;
	exports com.swirlds.platform.chatter.protocol.output;
	exports com.swirlds.platform.chatter.protocol.peer;
	exports com.swirlds.platform.chatter.protocol;
	exports com.swirlds.platform.chatter;
	exports com.swirlds.platform.chatter.protocol.purgable.twomaps;
	exports com.swirlds.platform.chatter.protocol.purgable;

	/* Swirlds Libraries */
	requires transitive com.swirlds.common;
	requires com.swirlds.logging;

	/* JDK Libraries */
	requires java.desktop;
	requires java.management;
	requires java.scripting;
	requires java.sql;

	requires jdk.management;
	requires jdk.net;

	/* JavaFX Libraries */
	requires javafx.base;

	/* Apache Commons */
	requires org.apache.commons.lang3;

	/* Networking Libraries */
	requires portmapper;

	/* Logging Libraries */
	requires org.apache.logging.log4j;
	requires org.apache.logging.log4j.core;
	requires org.slf4j;

	/* Cryptographic Libraries */
	requires org.bouncycastle.pkix;
	requires org.bouncycastle.provider;

	/* Database Libraries */
	requires com.swirlds.fchashmap;
	requires com.swirlds.jasperdb;
	requires com.swirlds.virtualmap;
}
