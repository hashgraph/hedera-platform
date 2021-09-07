/*
 * (c) 2016-2021 Swirlds, Inc.
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
 * A map that implements the FastCopyable interface.
 */
open module com.swirlds.fcmap {
	exports com.swirlds.fcmap;

	requires com.swirlds.common;
	requires com.swirlds.logging;
	requires com.swirlds.platform;
	requires com.swirlds.fcqueue;
	requires com.swirlds.fchashmap;

	requires org.apache.logging.log4j;
	requires org.apache.logging.log4j.core;
	requires org.apache.commons.lang3;

	requires java.sql;
	requires com.swirlds.merkletree;
}
