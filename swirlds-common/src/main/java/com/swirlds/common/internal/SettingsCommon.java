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

package com.swirlds.common.internal;

public class SettingsCommon {

	// used by Transaction
	/**
	 * the maximum number of bytes that a single event may contain not including the event headers
	 * if a single transaction exceeds this limit then the event will contain the single transaction only
	 */
	public static int maxTransactionBytesPerEvent;

	/** the maximum number of transactions that a single event may contain */
	public static int maxTransactionCountPerEvent;

	// used by Transaction, Platform
	/** maximum number of bytes allowed in a transaction */
	public static int transactionMaxBytes;

	// used by CommonUtils
	public static boolean logStack;

	// used by AbstractStatistics
	public static double halfLife = 10;
	public static boolean showInternalStats;
	public static boolean verboseStatistics;

	// used by SignedStateManager
	public static boolean enableBetaMirror;
}
