/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.common.internal;

/**
 * @deprecated this is not a good access pattern, don't add to this mess by increasing the places
 *     where its used
 */
@Deprecated
public class SettingsCommon {

    // used by Transaction
    /**
     * the maximum number of bytes that a single event may contain not including the event headers
     * if a single transaction exceeds this limit then the event will contain the single transaction
     * only
     */
    public static int maxTransactionBytesPerEvent = Integer.MAX_VALUE;

    /** the maximum number of transactions that a single event may contain */
    public static int maxTransactionCountPerEvent = Integer.MAX_VALUE;

    // used by Transaction, Platform
    /** maximum number of bytes allowed in a transaction */
    public static int transactionMaxBytes = Integer.MAX_VALUE;
    /**
     * the maximum number of address allowed in a address book, the same as the maximum allowed
     * network size
     */
    public static int maxAddressSizeAllowed = Integer.MAX_VALUE;

    // used by CommonUtils
    public static boolean logStack;

    // used by AbstractStatistics
    public static double halfLife = 10;
    public static boolean showInternalStats;
    public static boolean verboseStatistics;

    // used by SignedStateManager
    public static boolean enableBetaMirror;
    /**
     * should a transaction be sent after each state signature transaction, giving all
     * avgPingMilliseconds[] stats?
     */
    public static boolean enablePingTrans = true;
    /**
     * should a transaction be sent after each state signature transaction, giving all
     * avgBytePerSecSent[] stats?
     */
    public static boolean enableBpsTrans = true;

    /** update some metrics every this many milliseconds (-1 for never) */
    public static int metricsUpdatePeriodMillis = 1000;

    // used by MetricsWriterService
    public static boolean disableMetricsOutput = false;
    public static int threadPriorityNonSync = Thread.NORM_PRIORITY;
    public static String csvFileName = "";
    public static String csvOutputFolder = "";
    public static boolean csvAppend = false;
    public static long csvWriteFrequency = 3000L;

    /** Indicates if a prometheus endpoint should be offered * */
    public static boolean prometheusEndpointEnabled = false;
    /** Port of the Prometheus endpoint * */
    public static int prometheusEndpointPortNumber = 9999;
    /**
     * Backlog of the Prometheus endpoint (= number of incoming TCP connections the system will
     * queue) *
     */
    public static int prometheusEndpointMaxBacklogAllowed = 1;
}
