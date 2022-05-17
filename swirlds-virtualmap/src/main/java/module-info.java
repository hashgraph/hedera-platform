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
 * A map that implements the FastCopyable interface.
 */
open module com.swirlds.virtualmap {
	exports com.swirlds.virtualmap;
	exports com.swirlds.virtualmap.datasource;
	// Currently, exported only for tests.
	exports com.swirlds.virtualmap.internal.merkle;

	requires com.swirlds.common;
	requires com.swirlds.logging;

	requires org.apache.logging.log4j;
	requires org.apache.logging.log4j.core;

	requires java.sql;

	requires java.management; // Test dependency

	requires org.apache.commons.lang3;
}
