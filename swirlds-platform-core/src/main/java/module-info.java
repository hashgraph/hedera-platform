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
/**
 * The Swirlds public API module used by platform applications.
 */
module com.swirlds.platform {

	/* Public Package Exports */
	exports com.swirlds.blob;
	exports com.swirlds.platform;
	exports com.swirlds.throttle;
	exports com.swirlds.platform.state;

	/* Targeted Exports to External Libraries */
	exports com.swirlds.blob.internal to org.apache.logging.log4j, com.swirlds.demo.platform,
			com.swirlds.demo.fcm.stats, com.swirlds.fcmap;
	exports com.swirlds.blob.internal.db to org.apache.logging.log4j, com.swirlds.demo.platform, com.swirlds.fcmap,
			com.swirlds.platform.test, com.swirlds.fcmap.test, com.swirlds.regression;
	exports com.swirlds.blob.internal.db.migration to org.apache.logging.log4j, com.swirlds.demo.platform;
	exports com.swirlds.platform.event to com.swirlds.platform.test, com.fasterxml.jackson.core,
			com.fasterxml.jackson.databind;
	exports com.swirlds.platform.internal to com.swirlds.platform.test, com.fasterxml.jackson.core,
			com.fasterxml.jackson.databind;
	exports com.swirlds.platform.swirldapp to com.swirlds.platform.test;
	exports com.swirlds.platform.stats;

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
	requires org.flywaydb.core;
	requires com.zaxxer.hikari;
	requires org.postgresql.jdbc;

	/* Jackson JSON */
	requires com.fasterxml.jackson.core;
	requires com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.datatype.jsr310;


}
