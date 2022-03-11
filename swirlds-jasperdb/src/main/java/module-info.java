/*
 * (c) 2016-2022 Swirlds, Inc.
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
 * A disk-based VirtualDataSource implementation; complete module documentation
 * to be assembled over time as the full implementation is transplanted here.
 */
open module com.swirlds.jasperdb {
	exports com.swirlds.jasperdb.collections;
	exports com.swirlds.jasperdb.utilities;
	exports com.swirlds.jasperdb.files;
	exports com.swirlds.jasperdb.files.hashmap;
	exports com.swirlds.jasperdb;
	exports com.swirlds.jasperdb.settings;

	requires com.swirlds.common;
	requires com.swirlds.logging;
	requires com.swirlds.virtualmap;

	requires org.apache.commons.lang3;

	requires org.eclipse.collections.impl;
	requires org.eclipse.collections.api;

	requires org.apache.logging.log4j;
	requires org.apache.logging.log4j.core;

	requires java.management;
	requires jdk.management;
	requires jdk.unsupported;
}
