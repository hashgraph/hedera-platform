/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.platform;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.OperatingSystemMXBean;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.statistics.internal.AbstractStatistics;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.PlatformStatNames;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.utility.ThresholdLimitingHandler;
import com.swirlds.common.utility.Units;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.EventCounter;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.SwirldStateSingleTransactionPool;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.network.NetworkStats;
import com.swirlds.platform.observers.EventAddedObserver;
import com.swirlds.common.utility.RuntimeObjectRegistry;
import com.swirlds.platform.state.SwirldStateManagerDouble;
import com.swirlds.platform.state.SwirldStateManagerSingle;
import com.swirlds.platform.stats.AverageAndMax;
import com.swirlds.platform.stats.AverageStat;
import com.swirlds.platform.stats.ConsensusHandlingStats;
import com.swirlds.platform.stats.ConsensusStats;
import com.swirlds.platform.stats.CycleTimingStat;
import com.swirlds.platform.stats.EventIntakeStats;
import com.swirlds.platform.stats.IssStats;
import com.swirlds.platform.stats.MaxStat;
import com.swirlds.platform.stats.PerSecondStat;
import com.swirlds.platform.stats.PlatformStatistics;
import com.swirlds.platform.stats.SignedStateStats;
import com.swirlds.platform.stats.SwirldStateStats;
import com.swirlds.platform.stats.SyncStats;
import com.swirlds.platform.stats.TimeStat;
import com.swirlds.platform.stats.TransactionStatistics;
import com.swirlds.platform.sync.SyncManager;
import com.swirlds.platform.sync.SyncResult;
import com.swirlds.platform.sync.SyncTiming;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_0;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_1;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_3;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_6;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_11_3;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_13_0;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_13_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_14_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_14_7;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_15_3;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_16_0;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_16_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_17_1;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_1_3;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_1_4;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_2_0;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_3_0;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_4_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_5_3;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_6_0;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_7_0;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_8_0;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_8_1;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_9_6;
import static com.swirlds.common.system.PlatformStatNames.SIGNED_STATE_HASHING_TIME;
import static com.swirlds.common.utility.Units.NANOSECONDS_TO_SECONDS;
import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * This class collects and reports various statistics about network operation. A statistic such as
 * Transactions Per Second can be retrieved by using its name "trans/sec". The current list of statistic
 * names can be found by calling {@link #getAvailableStats}, and includes the following:
 *
 * <ul>
 * <li><b>badEv/sec</b> - number of corrupted events received per second *
 * <li><b>bytes/sec_sync</b> - average number of bytes per second transferred during a sync *
 * <li><b>bytes/sec_trans</b> - number of bytes in the transactions received per second (from unique events created
 * by self and others) *
 * <li><b>bytes/trans</b> - number of bytes in each transactions *
 * <li><b>cEvents/sec</b> - number of events per second created by this node *
 * <li><b>conns</b> - number of times a TLS connections was created *
 * <li><b>cpuLoadSys</b> - the CPU load of the whole system *
 * <li><b>directMemInMB</b> - total megabytes of off-heap (direct) memory in the JVM *
 * <li><b>directMemPercent</b> - off-heap (direct) memory used as a percentage of -XX:MaxDirectMemorySize param *
 * <li><b>dupEv%</b> - percentage of events received that are already known *
 * <li><b>ev/syncS</b> - number of events sent per successful sync *
 * <li><b>ev/syncR</b> - number of events received per successful sync *
 * <li><b>events/sec</b> - number of unique events received per second (created by self and others) *
 * <li><b>eventStreamQueueSize</b> - size of the queue from which we take events and write to EventStream file *
 * <li><b>icSync/sec</b> - (interrupted call syncs) syncs interrupted per second initiated by this member *
 * <li><b>irSync/sec</b> - (interrupted receive syncs) syncs interrupted per second initiated by other
 * member *
 * <li><b>lastSeq</b> - last event number generated by me *
 * <li><b>local</b> - number of members running on this local machine *
 * <li><b>memberID</b> - ID number of this member *
 * <li><b>members</b> - total number of members participating *
 * <li><b>memFree</b> - bytes of free memory (which can increase after a garbage collection) *
 * <li><b>memMax</b> - maximum bytes that the JVM might use *
 * <li><b>memTot</b> - total bytes in the Java Virtual Machine *
 * <li><b>name</b> - name of this member *
 * <li><b>ping</b> - average time for a round trip message between 2 computers (in milliseconds) *
 * <li><b>proc</b> - number of processors (cores) available to the JVM *
 * <li><b>roundSup</b> - latest round with state signed by a supermajority *
 * <li><b>rounds/sec</b> - average number of rounds per second *
 * <li><b>sec/sync</b> - duration of average successful sync (in seconds) *
 * <li><b>secC2C</b> - time from creating an event to knowing its consensus (in seconds) *
 * <li><b>SecC2H</b> - time from knowing consensus for a transaction to handling it (in seconds) *
 * <li><b>secC2R</b> - time from another member creating an event to receiving it and veryfing the signature
 * (in seconds) *
 * <li><b>secC2RC</b> - time from another member creating an event to it being received and and knowing
 * consensus for it (in seconds) *
 * <li><b>secR2C</b> - time from receiving an event to knowing its consensus (in seconds) *
 * <li><b>secR2F</b> - time from a round's first received event to all the famous witnesses being known (in
 * seconds) *
 * <li><b>secR2nR</b> - time from fist event received in one round, to first event received in the next
 * round (in seconds) *
 * <li><b>simListenSyncs</b> - avg number of simultaneous listening syncs happening at any given time *
 * <li><b>simSyncs</b> - avg number of simultaneous syncs happening at any given time *
 * <li><b>sync/secC</b> - (call syncs) syncs completed per second initiated by this member *
 * <li><b>sync/secR</b> - (receive syncs) syncs completed per second initiated by other member *
 * <li><b>threads</b> - the current number of live threads *
 * <li><b>time</b> - the current time *
 * <li><b>TLS</b> - 1 if using TLS, 0 if not *
 * <li><b>transCons</b> - transCons queue size *
 * <li><b>transEvent</b> - transEvent queue size *
 * <li><b>trans/event</b> - number of app transactions in each event *
 * <li><b>trans/sec</b> - number of app transactions received per second (from unique events created by self
 * and others) *
 * <li><b>write</b> - the app claimed to log statistics every this many milliseconds *
 * </ul>
 */
public class Statistics extends AbstractStatistics implements
		ConsensusStats,
		EventAddedObserver,
		EventIntakeStats,
		IssStats,
		PlatformStatistics,
		SignedStateStats,
		SyncStats,
		SwirldStateStats,
		ConsensusHandlingStats,
		TransactionStatistics,
		NetworkStats {

	private static final Logger LOG = LogManager.getLogger(Statistics.class);

	/** The maximum number of times an exception should be logged before being suppressed. */
	private static final long EXCEPTION_RATE_THRESHOLD = 10;

	/** The number of time intervals measured on the thread-cons cycle */
	private static final int CONS_ROUND_INTERVALS = 6;

	/** The number of time intervals measured on the new signed state creation cycle */
	private static final int NEW_SIGNED_STATE_INTERVALS = 5;

	/** which Platform to watch */
	protected AbstractPlatform platform;
	/** an object used to get OS stats */
	private final OperatingSystemMXBean osBean;
	private final List<PerSecondStat> perSecondStats;

	private final ThresholdLimitingHandler<Throwable> exceptionRateLimiter = new ThresholdLimitingHandler<>(
			EXCEPTION_RATE_THRESHOLD);

	private final ReconnectStatistics reconnectStatistics;


	/** an object to get thread stats */
	ThreadMXBean thbean;

	/** number of app transactions (from self and others) per second */
	private final SpeedometerMetric transactionsPerSecond = new SpeedometerMetric(
			CATEGORY,
			"trans/sec",
			"number of app transactions received per second (from unique events created by self and others)",
			FORMAT_13_2
	);

	/** number of events (from self and others) per second */
	private final SpeedometerMetric eventsPerSecond = new SpeedometerMetric(
			CATEGORY,
			"events/sec",
			"number of unique events received per second (created by self and others)",
			FORMAT_16_2
	);

	/** number of rounds reaching consensus per second */
	private final SpeedometerMetric roundsPerSecond = new SpeedometerMetric(
			CATEGORY,
			"rounds/sec",
			"average number of rounds per second",
			FORMAT_11_3
	);

	/** number of events discarded because already known */
	private final SpeedometerMetric duplicateEventsPerSecond = new SpeedometerMetric(
			INTERNAL_CATEGORY,
			"dupEv/sec",
			"number of events received per second that are already known",
			FORMAT_14_2
	);

	/** number of syncs per second that complete, where self called someone else */
	private final SpeedometerMetric callSyncsPerSecond = new SpeedometerMetric(
			CATEGORY,
			"sync/secC",
			"(call syncs) syncs completed per second initiated by this member",
			FORMAT_14_7
	);

	/** number of syncs per second that complete, where someone else called self */
	private final SpeedometerMetric recSyncsPerSecond = new SpeedometerMetric(
			CATEGORY,
			"sync/secR",
			"(receive syncs) syncs completed per second initiated by other member",
			FORMAT_14_7
	);

	/** number of syncs initiated by member interrupted in the middle, per second */
	final SpeedometerMetric interruptedCallSyncsPerSecond = new SpeedometerMetric(
			CATEGORY,
			"icSync/sec",
			"(interrupted call syncs) syncs interrupted per second initiated by this member",
			FORMAT_14_7
	);

	/** number of syncs initiated by others interrupted in the middle, per second */
	final SpeedometerMetric interruptedRecSyncsPerSecond = new SpeedometerMetric(
			CATEGORY,
			"irSync/sec",
			"(interrupted receive syncs) syncs interrupted per second initiated by other member",
			FORMAT_14_7
	);

	/** number of events created by this node per second */
	final SpeedometerMetric eventsCreatedPerSecond = new SpeedometerMetric(
			CATEGORY,
			"cEvents/sec",
			"number of events per second created by this node",
			FORMAT_16_2
	);

	/** all connections of this platform */
	final Queue<Connection> connections = new ConcurrentLinkedQueue<>();
	/** total number of connections created so far (both caller and listener) */
	final AtomicInteger connsCreated = new AtomicInteger(0);

	/**
	 * number of bytes in the transactions received per second (from unique events created by self and
	 * others)
	 */
	private final SpeedometerMetric bytesPerSecondTrans = new SpeedometerMetric(
			CATEGORY,
			"bytes/sec_trans",
			"number of bytes in the transactions received per second (from unique events created " +
					"by self and others)",
			FORMAT_16_2
	);

	/** number of bytes sent per second over the network (total for this member) */
	private final SpeedometerMetric bytesPerSecondSent = new SpeedometerMetric(
			INTERNAL_CATEGORY,
			"bytes/sec_sent",
			"number of bytes sent per second over the network (total for this member)",
			FORMAT_16_2
	);

	/** number of extra bytes sent per second to help other members who fall behind to catch up */
	private final SpeedometerMetric bytesPerSecondCatchupSent = new SpeedometerMetric(
			INTERNAL_CATEGORY,
			"bytes/sec_catchup",
			"number of bytes sent per second to help others catch up",
			FORMAT_16_2
	);

	/** time for event, from when the event is received, to when all the famous witnesses are known */
	private final RunningAverageMetric avgReceivedFamousTime = new RunningAverageMetric(
			CATEGORY,
			"secR2F",
			"time from a round's first received event to all the famous witnesses being known (in seconds)",
			FORMAT_10_3
	);

	/** time for member, from creating to knowing consensus */
	private final RunningAverageMetric avgCreatedConsensusTime = new RunningAverageMetric(
			CATEGORY,
			"secC2C",
			"time from creating an event to knowing its consensus (in seconds)",
			FORMAT_10_3
	);

	/** time for a member, from receiving to knowing consensus */
	private final RunningAverageMetric avgReceivedConsensusTime = new RunningAverageMetric(
			CATEGORY,
			"secR2C",
			"time from receiving an event to knowing its consensus (in seconds)",
			FORMAT_10_3
	);

	/** time for a member, from knowing consensus to handling that consensus transaction */
	private final RunningAverageMetric avgConsHandleTime = new RunningAverageMetric(
			CATEGORY,
			"SecC2H",
			"time from knowing consensus for a transaction to handling it (in seconds)",
			FORMAT_10_3
	);

	private final AverageStat knownSetSize = new AverageStat(
			CATEGORY,
			"knownSetSize",
			"the average size of the known set during a sync",
			FORMAT_10_3,
			AverageStat.WEIGHT_VOLATILE
	);
	/** average wall clock time from start of a successful sync until it's done */
	private final TimeStat avgSyncDuration = new TimeStat(
			ChronoUnit.SECONDS,
			INTERNAL_CATEGORY,
			"sec/sync",
			"duration of average successful sync (in seconds)"
	);

	/** average wall clock time for step 1 of a successful sync */
	private final TimeStat avgSyncDuration1 = new TimeStat(
			ChronoUnit.SECONDS,
			INTERNAL_CATEGORY,
			"sec/sync1",
			"duration of step 1 of average successful sync (in seconds)"
	);
	/** average wall clock time for step 2 of a successful sync */
	private final TimeStat avgSyncDuration2 = new TimeStat(
			ChronoUnit.SECONDS,
			INTERNAL_CATEGORY,
			"sec/sync2",
			"duration of step 2 of average successful sync (in seconds)"
	);
	/** average wall clock time for step 3 of a successful sync */
	private final TimeStat avgSyncDuration3 = new TimeStat(
			ChronoUnit.SECONDS,
			INTERNAL_CATEGORY,
			"sec/sync3",
			"duration of step 3 of average successful sync (in seconds)"
	);
	/** average wall clock time for step 4 of a successful sync */
	private final TimeStat avgSyncDuration4 = new TimeStat(
			ChronoUnit.SECONDS,
			INTERNAL_CATEGORY,
			"sec/sync4",
			"duration of step 4 of average successful sync (in seconds)"
	);
	/** average wall clock time for step 5 of a successful sync */
	private final TimeStat avgSyncDuration5 = new TimeStat(
			ChronoUnit.SECONDS,
			INTERNAL_CATEGORY,
			"sec/sync5",
			"duration of step 5 of average successful sync (in seconds)"
	);
	private final AverageStat syncGenerationDiff = new AverageStat(
			INTERNAL_CATEGORY,
			"syncGenDiff",
			"number of generation ahead (positive) or behind (negative) when syncing",
			FORMAT_8_1,
			AverageStat.WEIGHT_VOLATILE
	);
	private final AverageStat eventRecRate = new AverageStat(
			INTERNAL_CATEGORY,
			"eventRecRate",
			"the rate at which we receive and enqueue events in ev/sec",
			FORMAT_8_1,
			AverageStat.WEIGHT_VOLATILE
	);

	private final CycleTimingStat consensusCycleTiming = new CycleTimingStat(
			ChronoUnit.MILLIS,
			INTERNAL_CATEGORY,
			"consRound",
			CONS_ROUND_INTERVALS,
			List.of("dataPropMillis/round",
					"handleMillis/round",
					"storeMillis/round",
					"hashMillis/round",
					"buildStateMillis",
					"forSigCleanMillis"
			),
			List.of("average time to propagate consensus data to transactions",
					"average time to handle a consensus round",
					"average time to add consensus round events to signed state storage",
					"average time spent hashing the consensus round events",
					"average time spent building a signed state",
					"average time spent expiring signed state storage events"
			)
	);

	private final CycleTimingStat newSignedStateCycleTiming = new CycleTimingStat(
			ChronoUnit.MICROS,
			INTERNAL_CATEGORY,
			"newSS",
			NEW_SIGNED_STATE_INTERVALS,
			List.of(
					"getStateMicros",
					"getStateDataMicros",
					"runningHashMicros",
					"newSSInstanceMicros",
					"queueAdmitMicros"
			),
			List.of("average time to get the state to sign",
					"average time to get events and min gen info",
					"average time spent waiting on the running hash future",
					"average time spent creating the new signed state instance",
					"average time spent admitting the signed state to the signing queue"
			)
	);

	/** average time (in seconds) to send a byte and get a reply, for each member (holds 0 for self) */
	public final RunningAverageMetric[] avgPingMilliseconds;
	/** average bytes per second received during a sync with each member (holds 0 for self) */

	public final SpeedometerMetric[] avgBytePerSecSent;

	/** time for event, from being created to being received by another member */
	private final RunningAverageMetric avgCreatedReceivedTime = new RunningAverageMetric(
			CATEGORY,
			"secC2R",
			"time from another member creating an event to receiving it and veryfing the " +
					"signature (in seconds)",
			FORMAT_10_3
	);

	/** time for event, from being created by one, to knowing consensus by another */
	private final RunningAverageMetric avgCreatedReceivedConsensusTime = new RunningAverageMetric(
			CATEGORY,
			"secC2RC",
			"time from another member creating an event to it being received and and knowing  " +
					"consensus for it (in seconds)",
			FORMAT_10_3
	);

	/** time for event, from receiving the first event in a round to the first event in the next round */
	private final RunningAverageMetric avgFirstEventInRoundReceivedTime = new RunningAverageMetric(
			CATEGORY,
			"secR2nR",
			"time from first event received in one round, to first event received in the " +
					"next round (in seconds)",
			FORMAT_10_3
	);

	/** average number of app transactions per event, counting both system and non-system transactions */
	private final RunningAverageMetric avgTransactionsPerEvent = new RunningAverageMetric(
			CATEGORY,
			"trans/event",
			"number of app transactions in each event",
			FORMAT_17_1
	);

	/** average number of bytes per app transaction, counting both system and non-system transactions */
	final RunningAverageMetric avgBytesPerTransaction = new RunningAverageMetric(
			CATEGORY,
			"bytes/trans",
			"number of bytes in each transactions",
			FORMAT_16_0
	);

	/** average percentage of received events that are already known */
	private final RunningAverageMetric avgDuplicatePercent = new RunningAverageMetric(
			CATEGORY,
			"dupEv%",
			"percentage of events received that are already known",
			FORMAT_10_2
	);

	/** self event consensus timestamp minus time created */
	private final RunningAverageMetric avgSelfCreatedTimestamp = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"secSC2T",
			"self event consensus timestamp minus time created (in seconds)",
			FORMAT_10_3
	);

	/** other event consensus timestamp minus time received */
	private final RunningAverageMetric avgOtherReceivedTimestamp = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"secOR2T",
			"other event consensus timestamp minus time received (in seconds)",
			FORMAT_10_3
	);

	/** INTERNAL: number of app transactions (from self and others) per second */
	private final SpeedometerMetric transactionsPerSecondSys = new SpeedometerMetric(
			INTERNAL_CATEGORY,
			"trans/sec_sys",
			"number of system transactions received per second (from unique events created by self and others)",
			FORMAT_13_2
	);

	/**
	 * INTERNAL: number of bytes in system transactions received per second, both created by self and
	 * others, only counting unique events
	 */
	private final SpeedometerMetric bytesPerSecondSys = new SpeedometerMetric(
			INTERNAL_CATEGORY,
			"bytes/sec_sys",
			"number of bytes in the system transactions received per second (from unique events " +
					"created by self and others)",
			FORMAT_16_2
	);

	/**
	 * INTERNAL: average number of system transactions per event, counting both system and non-system
	 * transactions
	 */
	private final RunningAverageMetric avgTransactionsPerEventSys = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"trans/event_sys",
			"number of system transactions in each event",
			FORMAT_17_1
	);

	/**
	 * INTERNAL: average number of bytes per system transaction, counting both system and non-system
	 * transactions
	 */
	private final RunningAverageMetric avgBytesPerTransactionSys = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"bytes/trans_sys",
			"number of bytes in each system transaction",
			FORMAT_16_0
	);

	/** sleeps per second because caller thread had too many failed connects */
	final SpeedometerMetric sleep1perSecond = new SpeedometerMetric(
			INTERNAL_CATEGORY,
			"sleep1/sec",
			"sleeps per second because caller thread had too many failed connects",
			FORMAT_9_6
	);

	/** fraction of syncs that are slowed to let others catch up */
	private final RunningAverageMetric fracSyncSlowed = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"fracSyncSlowed",
			"fraction of syncs that are slowed to let others catch up",
			FORMAT_9_6
	);

	/** fraction of each second spent in dot products */
	private final SpeedometerMetric timeFracDot = new SpeedometerMetric(
			INTERNAL_CATEGORY,
			"timeFracDot",
			"fraction of each second spent on dot products",
			FORMAT_9_6
	);

	/** fraction of each second spent adding an event to the hashgraph, and calculating consensus */
	private final SpeedometerMetric timeFracAdd = new SpeedometerMetric(
			INTERNAL_CATEGORY,
			"timeFracAdd",
			"fraction of each second spent adding an event to the hashgraph and finding consensus",
			FORMAT_9_6
	);

	/** average number of bytes per second transfered during a sync */
	private final RunningAverageMetric avgBytesPerSecSync = new RunningAverageMetric(
			CATEGORY,
			"bytes/sec_sync",
			"average number of bytes per second transfered during a sync",
			FORMAT_16_2
	);

	/** number of consensus transactions per second handled by SwirldState.handleTransaction() */
	private final SpeedometerMetric transHandledPerSecond = new SpeedometerMetric(
			INTERNAL_CATEGORY,
			PlatformStatNames.TRANSACTIONS_HANDLED_PER_SECOND,
			"number of consensus transactions per second handled by SwirldState.handleTransaction()",
			FORMAT_9_6
	);

	/** avg time to handle a consensus transaction in SwirldState.handleTransaction (in seconds) */
	private final RunningAverageMetric avgSecTransHandled = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"secTransH",
			"avg time to handle a consensus transaction in SwirldState.handleTransaction (in seconds)",
			FORMAT_10_6
	);

	/** average time it takes the copy() method in SwirldState to finish (in microseconds) */
	private final RunningAverageMetric avgStateCopyMicros = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"stateCopyMicros",
			"average time it takes the SwirldState.copy() method in SwirldState to finish (in microseconds)",
			FORMAT_16_2
	);

	/** average time it takes to create a new SignedState (in seconds) */
	private final RunningAverageMetric avgSecNewSignedState = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			SIGNED_STATE_HASHING_TIME,
			"average time it takes to create a new SignedState (in seconds)",
			FORMAT_10_3
	);

	/** boolean result of function {@link SyncManager#shouldCreateEvent(SyncResult)} */
	private final RunningAverageMetric shouldCreateEvent = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"shouldCreateEvent",
			"shouldCreateEvent",
			FORMAT_10_1
	);

	private final IntegerGauge issCount = new IntegerGauge(
			INTERNAL_CATEGORY,
			"issCount",
			"the number nodes that currently disagree with the hash of this node's state"
	);

	/** number of coin rounds that have occurred so far */
	private final RunningAverageMetric numCoinRounds = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"coinR",
			"number of coin rounds that have occurred so far",
			FORMAT_10_0
	);


	//////////////////// these are updated in Statistics.updateOthers() /////////////

	/** number of app transactions received so far */
	private final Counter numTrans = new Counter(
			INTERNAL_CATEGORY,
			"trans",
			"number of transactions received so far"
	);

	/** average time for a round trip message between 2 computers (in milliseconds) */
	private final RunningAverageMetric avgPing = new RunningAverageMetric(
			CATEGORY,
			"ping",
			"average time for a round trip message between 2 computers (in milliseconds)",
			FORMAT_7_0
	);

	/** number of connections created (both calling and listening) */
	final RunningAverageMetric avgConnsCreated = new RunningAverageMetric(
			CATEGORY,
			"conns",
			"number of times a TLS connections was created",
			FORMAT_10_0,
			0
	);

	/** bytes of free memory (which can increase after a garbage collection) */
	private final RunningAverageMetric memFree = new RunningAverageMetric(
			CATEGORY,
			"memFree",
			"bytes of free memory (which can increase after a garbage collection)",
			FORMAT_16_0,
			0
	);

	/** total bytes in the Java Virtual Machine */
	private final RunningAverageMetric memTot = new RunningAverageMetric(
			CATEGORY,
			"memTot",
			"total bytes in the Java Virtual Machine",
			FORMAT_16_0,
			0
	);

	/** maximum bytes that the JVM might use */
	private final RunningAverageMetric memMax = new RunningAverageMetric(
			CATEGORY,
			"memMax",
			"maximum bytes that the JVM might use",
			FORMAT_16_0,
			0
	);

	/** maximum amount of off-heap (direct) memory being used by the JVM, in megabytes */
	private final RunningAverageMetric directMemInMB = new RunningAverageMetric(
			CATEGORY,
			"directMemInMB",
			"megabytes of off-heap (direct) memory being used by the JVM",
			FORMAT_16_2,
			0
	);

	/** off-heap (direct) memory being used by the JVM, as a percentage of -XX:MaxDirectMemorySize param */
	private final RunningAverageMetric directMemPercent = new RunningAverageMetric(
			CATEGORY,
			"directMemPercent",
			"off-heap (direct) memory used, as a percent of MaxDirectMemorySize",
			FORMAT_16_2,
			0
	);

	/**
	 * helper variables (set once, when the directMemory StatsRunningAverage variables are created) used to
	 * refresh those values whenever the stats are created
	 */
	private BufferPoolMXBean directMemMXBean;
	private double maximumDirectMemSizeInMB = -1;

	/** number of processors (cores) available to the JVM */
	private final RunningAverageMetric avgNumProc = new RunningAverageMetric(
			CATEGORY,
			"proc",
			"number of processors (cores) available to the JVM",
			FORMAT_8_0
	);

	/** the CPU load of the whole system */
	private final RunningAverageMetric cpuLoadSys = new RunningAverageMetric(
			CATEGORY,
			"cpuLoadSys",
			"the CPU load of the whole system",
			FORMAT_1_4
	);

	/** Total number of thread running */
	private final RunningAverageMetric threads = new RunningAverageMetric(
			CATEGORY,
			"threads",
			"the current number of live threads",
			FORMAT_6_0
	);

	/** ID number of this member */
	private final RunningAverageMetric avgSelfId = new RunningAverageMetric(
			INFO_CATEGORY,
			"memberID",
			"ID number of this member",
			FORMAT_3_0
	);

	/** total number of members participating */
	private final RunningAverageMetric avgNumMembers = new RunningAverageMetric(
			CATEGORY,
			"members",
			"total number of members participating",
			FORMAT_10_0
	);

	/** statistics are logged every this many milliseconds */
	private final RunningAverageMetric avgWrite = new RunningAverageMetric(
			CATEGORY,
			"write",
			"the app claimed to log statistics every this many milliseconds",
			FORMAT_8_0
	);

	/** max number of syncs this can initiate simultaneously */
	private final RunningAverageMetric avgSimCallSyncsMax = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"simCallSyncsMax",
			"max number of syncs this can initiate simultaneously",
			FORMAT_2_0
	);

	/** avg number of syncs this member is doing simultaneously */
	private final RunningAverageMetric avgSimSyncs = new RunningAverageMetric(
			CATEGORY,
			"simSyncs",
			"avg number of simultaneous syncs happening at any given time",
			FORMAT_9_6
	);

	/** avg number of listening syncs this member is doing simultaneously */
	private final RunningAverageMetric avgSimListenSyncs = new RunningAverageMetric(
			CATEGORY,
			"simListenSyncs",
			"avg number of simultaneous listening syncs happening at any given time",
			FORMAT_9_6
	);

	/**
	 * The number of pre-consensus events waiting to be handled by
	 * {@link com.swirlds.platform.state.SwirldStateManager}
	 */
	private final AverageAndMax avgQ1PreConsEvents = new AverageAndMax(
			INTERNAL_CATEGORY,
			PlatformStatNames.PRE_CONSENSUS_QUEUE_SIZE,
			"average number of events in the pre-consensus queue (q1) waiting to be handled",
			FORMAT_10_3,
			AverageStat.WEIGHT_VOLATILE
	);

	/**
	 * The number of events in the consensus rounds waiting to be handled by
	 * {@link ConsensusRoundHandler}
	 */
	private final AverageAndMax avgQ2ConsEvents = new AverageAndMax(
			INTERNAL_CATEGORY,
			PlatformStatNames.CONSENSUS_QUEUE_SIZE,
			"average number of events in the consensus queue (q2) waiting to be handled",
			FORMAT_10_3,
			AverageStat.WEIGHT_VOLATILE
	);

	/** The average number of events per round. */
	private final AverageAndMax avgEventsPerRound = new AverageAndMax(
			INTERNAL_CATEGORY,
			"events/round",
			"average number of events in a consensus round",
			FORMAT_8_1,
			AverageStat.WEIGHT_VOLATILE
	);

	/** number of handled consensus events that will be part of the next signed state */
	private final RunningAverageMetric avgQSignedStateEvents = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"queueSignedStateEvents",
			"number of handled consensus events that will be part of the next signed state",
			FORMAT_10_1
	);

	/** number of events received waiting to be processed, or events waiting to be created [forSigs.size()] */
	private final RunningAverageMetric avgQ4forHash = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"q4",
			"number events in receiving queue waiting to be processed or created",
			FORMAT_10_1
	);

	/** number of SignedStates waiting to be hashed and signed in the queue [stateToHashSign.size()] */
	private final RunningAverageMetric avgStateToHashSignDepth = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"stateToHashSignDepth",
			"average depth of the stateToHashSign queue (number of SignedStates)",
			FORMAT_16_2
	);

	/** size of the queue from which we take events and write to EventStream file */
	private final RunningAverageMetric eventStreamQueueSize = new RunningAverageMetric(
			INFO_CATEGORY,
			"eventStreamQueueSize",
			"size of the queue from which we take events and write to EventStream file",
			FORMAT_13_0
	);

	/** size of the queue from which we take events, calculate Hash and RunningHash */
	private final RunningAverageMetric hashQueueSize = new RunningAverageMetric(
			INFO_CATEGORY,
			"hashQueueSize",
			"size of the queue from which we take events, calculate Hash and RunningHash",
			FORMAT_13_0
	);

	/** latest round with signed state by a supermajority */
	private final RunningAverageMetric avgRoundSupermajority = new RunningAverageMetric(
			CATEGORY,
			"roundSup",
			"latest round with state signed by a supermajority",
			FORMAT_10_0
	);

	/** number of events sent per successful sync */
	private final AverageAndMax avgEventsPerSyncSent = new AverageAndMax(
			CATEGORY,
			"ev/syncS",
			"number of events sent per successful sync",
			FORMAT_8_1
	);
	/** number of events received per successful sync */
	private final AverageAndMax avgEventsPerSyncRec = new AverageAndMax(
			CATEGORY,
			"ev/syncR",
			"number of events received per successful sync",
			FORMAT_8_1
	);
	private final AverageStat averageOtherParentAgeDiff = new AverageStat(
			CATEGORY,
			"opAgeDiff",
			"average age difference (in generations) between an event created by this node and its other parent",
			FORMAT_5_3,
			AverageStat.WEIGHT_VOLATILE
	);

	/** INTERNAL: total number of events in memory, for all members on the local machine together */
	private final RunningAverageMetric avgEventsInMem = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"eventsInMem",
			"total number of events in memory, for all members on the local machine together",
			FORMAT_16_2
	);

	/** running average of the number of signatures collected on each signed state */
	final RunningAverageMetric avgStateSigs = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"stateSigs",
			"number of signatures collected on each signed state",
			FORMAT_10_2
	);

	/** number of stale events per second */
	private final SpeedometerMetric staleEventsPerSecond = new SpeedometerMetric(
			INTERNAL_CATEGORY,
			"staleEv/sec",
			"number of stale events per second",
			FORMAT_16_2
	);

	/** number of stale events ever */
	private final Counter staleEventsTotal = new Counter(
			INTERNAL_CATEGORY,
			"staleEvTot",
			"total number of stale events ever"
	);

	/** number of events generated per second to rescue childless events so they don't become stale */
	private final SpeedometerMetric rescuedEventsPerSecond = new SpeedometerMetric(
			INTERNAL_CATEGORY,
			"rescuedEv/sec",
			"number of events per second generated to prevent stale events",
			FORMAT_16_2
	);

	/** The number of creators that have more than one tip at the start of each sync. */
	private final MaxStat multiTipsPerSync = new MaxStat(
			CATEGORY,
			PlatformStatNames.MULTI_TIPS_PER_SYNC,
			"the number of creators that have more than one tip at the start of each sync",
			INTEGER_FORMAT_5
	);
	/** The average number of tips per sync at the start of each sync. */
	private final RunningAverageMetric tipsPerSync = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			PlatformStatNames.TIPS_PER_SYNC,
			"the average number of tips per sync at the start of each sync",
			FORMAT_15_3
	);

	/** The average number of generations that should be expired but cannot because of reservations. */
	private final AverageStat gensWaitingForExpiry = new AverageStat(
			CATEGORY,
			PlatformStatNames.GENS_WAITING_FOR_EXPIRY,
			"the average number of generations waiting to be expired",
			FORMAT_5_3,
			AverageStat.WEIGHT_VOLATILE
	);

	/**
	 * The ratio of rejected syncs to accepted syncs over time.
	 */
	private final AverageStat rejectedSyncRatio = new AverageStat(
			INTERNAL_CATEGORY,
			PlatformStatNames.REJECTED_SYNC_RATIO,
			"the averaged ratio of rejected syncs to accepted syncs over time",
			FORMAT_1_3,
			AverageStat.WEIGHT_VOLATILE
	);
	/**
	 * The ratio of rejected syncs to accepted syncs over time.
	 */
	private final AverageStat avgTransSubmitMicros = new AverageStat(
			INTERNAL_CATEGORY,
			PlatformStatNames.TRANS_SUBMIT_MICROS,
			"average time spent submitting a user transaction (in microseconds)",
			FORMAT_1_3,
			AverageStat.WEIGHT_VOLATILE
	);

	/**
	 * average time spent in
	 * {@code SwirldStateManager#preConsensusEvent} by the {@code thread-curr} thread (in microseconds)
	 */
	private final TimeStat preConsHandleTime = new TimeStat(
			ChronoUnit.MICROS,
			INTERNAL_CATEGORY,
			"preConsHandleMicros",
			"average time it takes to handle a pre-consensus event from q4 (in microseconds)"
	);

	/**
	 * average time spent in
	 * {@code SwirldStateManager#prehandle} by the {@code intake} thread (in microseconds)
	 */
	private final TimeStat preHandleTime = new TimeStat(
			ChronoUnit.MICROS,
			INTERNAL_CATEGORY,
			"preHandleMicros",
			"average time it takes to perform preHandle (in microseconds)"
	);

	/**
	 * average time spent in {@link SwirldStateManagerSingle} when performing a shuffle (in microseconds)
	 */
	private final RunningAverageMetric avgShuffleMicros = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"shuffleMicros",
			"average time spent in SwirldStateManagerSingle.Shuffler#shuffle() method (in microseconds)",
			FORMAT_16_2
	);

	/**
	 * avg length of the state archival queue
	 */
	private final RunningAverageMetric stateArchivalQueueAvg = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"stateArchivalQueueAvg",
			"avg length of the state archival queue",
			FORMAT_15_3
	);

	/**
	 * avg time taken to archive a signed state (in microseconds)
	 */
	private final RunningAverageMetric stateArchivalTimeAvg = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"stateArchivalTimeAvg",
			"avg time to archive a signed state (in microseconds)",
			FORMAT_15_3
	);

	/**
	 * avg length of the state deletion queue
	 */
	private final RunningAverageMetric stateDeletionQueueAvg = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"stateDeletionQueueAvg",
			"avg length of the state deletion queue",
			FORMAT_15_3
	);

	/**
	 * avg time taken to delete a signed state (in microseconds)
	 */
	private final RunningAverageMetric stateDeletionTimeAvg = new RunningAverageMetric(
			INTERNAL_CATEGORY,
			"stateDeletionTimeAvg",
			"avg time it takes to delete a signed state (in microseconds)",
			FORMAT_15_3
	);

	private final File rootDirectory = new File("/");

	private static final String INTEGER_FORMAT_5 = "%5d";
	private static final double WHOLE_PERCENT = 100.0;    // all of something is to be reported as 100.0%

	/** once a second, update all the statistics that aren't updated by any other class */
	@Override
	public void updateOthers() {
		try {
			// don't update anything until the platform creates the hashgraph
			if (platform.getEventTaskCreator() != null) {
				for (final PerSecondStat perSecondStat : perSecondStats) {
					perSecondStat.update();
				}
				// calculate the value for otherStatPing (the average of all, not including self)
				double sum = 0;
				final double[] times = getPingMilliseconds(); // times are in seconds
				for (final double time : times) {
					sum += time;
				}
				// don't average in the times[selfId]==0, so subtract 1 from the length
				final double pingValue = sum / (times.length - 1);  // pingValue is in milliseconds

				avgPing.recordValue(pingValue);
				interruptedCallSyncsPerSecond.update(0);
				interruptedRecSyncsPerSecond.update(0);
				platform.getStats().sleep1perSecond.update(0);
				memFree.recordValue(Runtime.getRuntime().freeMemory());
				memTot.recordValue(Runtime.getRuntime().totalMemory());
				memMax.recordValue(Runtime.getRuntime().maxMemory());
				updateDirectMemoryStatistics();
				avgNumProc.recordValue(
						Runtime.getRuntime().availableProcessors());
				cpuLoadSys.recordValue(osBean.getCpuLoad());
				threads.recordValue(thbean.getThreadCount());
				avgSelfId.recordValue(platform.getSelfId().getId());
				avgNumMembers.recordValue(
						platform.getAddressBook().getSize());
				avgWrite.recordValue(statsWritePeriod);
				avgSimCallSyncsMax.recordValue(Settings.maxOutgoingSyncs);
				avgQ1PreConsEvents.update(platform.getPreConsensusHandler().getQueueSize());
				avgQ2ConsEvents.update(platform.getConsensusHandler().getNumEventsInQueue());
				avgQSignedStateEvents.recordValue(platform.getConsensusHandler().getSignedStateEventsSize());
				avgQ4forHash.recordValue(platform.getIntakeQueue().size());
				avgSimSyncs.recordValue(platform.getSimultaneousSyncThrottle().getNumSyncs());
				avgSimListenSyncs.recordValue(platform.getSimultaneousSyncThrottle().getNumListenerSyncs());
				eventStreamQueueSize.recordValue(platform.getEventStreamManager() != null ?
						platform.getEventStreamManager().getEventStreamingQueueSize() : 0);
				hashQueueSize.recordValue(platform.getEventStreamManager() != null ?
						platform.getEventStreamManager().getHashQueueSize() : 0);
				avgStateToHashSignDepth.recordValue(platform.getConsensusHandler().getStateToHashSignSize());
				avgRoundSupermajority.recordValue(
						platform.getSignedStateManager().getLastCompleteRound());
				avgEventsInMem.recordValue(EventCounter.getNumEventsInMemory());
				updateConnectionStats();
			}
		} catch (final Exception e) {
			exceptionRateLimiter.handle(e,
					error -> LOG.error(EXCEPTION.getMarker(), "Exception while updating statistics.", error));
		}
	}

	private void updateConnectionStats() {
		long totalBytesSent = 0;
		for (final Iterator<Connection> iterator = connections.iterator(); iterator.hasNext(); ) {
			final Connection conn = iterator.next();
			if (conn != null) {
				final long bytesSent = conn.getDos().getConnectionByteCounter().getAndResetCount();
				totalBytesSent += bytesSent;
				final int otherId = conn.getOtherId().getIdAsInt();
				if (otherId < avgBytePerSecSent.length && avgBytePerSecSent[otherId] != null) {
					avgBytePerSecSent[otherId].update(bytesSent);
				}
				if (!conn.connected()) {
					iterator.remove();
				}
			}
		}
		bytesPerSecondSent.update(totalBytesSent);
		avgConnsCreated.recordValue(connsCreated.get());
	}

	/**
	 * Update additional statistics that aren't updated by any other class. Split out of {link #updateOthers()}
	 * to stop SonarCloud from complaining that it's Cognitive Complexity was 1 too high.
	 */
	private void updateDirectMemoryStatistics() {
		if (directMemMXBean == null) {
			return;
		}

		final long bytesUsed = directMemMXBean.getMemoryUsed();
		// recording the value of -1 as (-1) / (1024 * 1024) makes it too close to 0; treat it as -1 megabytes
		// for visibility
		if (bytesUsed == -1) {
			directMemInMB.recordValue(bytesUsed);
			if (maximumDirectMemSizeInMB > 0) {
				directMemPercent.recordValue(bytesUsed);
			}
			return;
		}
		final double megabytesUsed = bytesUsed * Units.BYTES_TO_MEBIBYTES;
		directMemInMB.recordValue(megabytesUsed);
		if (maximumDirectMemSizeInMB > 0) {
			directMemPercent.recordValue(megabytesUsed * WHOLE_PERCENT / maximumDirectMemSizeInMB);
		}
	}

	/**
	 * Do not allow instantiation without knowing which Platform to monitor. Do not call this.
	 */
	@SuppressWarnings("unused")
	Statistics() {
		super();
		throw new UnsupportedOperationException(
				"called constructor new Statistics() instead of new Statistics(platform)");
	}

	/**
	 * Same as {@link #Statistics(AbstractPlatform, List)} with additionalEntries set to be empty
	 */
	public Statistics(final AbstractPlatform platform) {
		this(platform, Collections.emptyList());
	}

	/**
	 * Same as {@link #Statistics(AbstractPlatform, List, List)} with perSecondStats set to be empty
	 */
	public Statistics(final AbstractPlatform platform, final List<Metric> additionalEntries) {
		this(platform, additionalEntries, Collections.emptyList());
	}

	/**
	 * Constructor for a Statistics object that will monitor the statistics for the network for the given
	 * Platform.
	 *
	 * @param platform
	 * 		the Platform whose statistics should be monitored
	 * @param additionalEntries
	 * 		additional stat entries to be added
	 * @param perSecondStats
	 * 		stats that need to be updated once per second
	 */
	public Statistics(
			final AbstractPlatform platform,
			final List<Metric> additionalEntries,
			final List<PerSecondStat> perSecondStats) {
		super();
		this.platform = platform;
		this.perSecondStats = perSecondStats;
		this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		this.thbean = ManagementFactory.getThreadMXBean();

		this.reconnectStatistics = new ReconnectStatistics();
		final int abSize = platform.getAddressBook() == null ? 0 : platform.getAddressBook().getSize(); //0 during unit
		// tests

		avgPingMilliseconds = new RunningAverageMetric[abSize];
		avgBytePerSecSent = new SpeedometerMetric[abSize];
		for (int i = 0; i < avgPingMilliseconds.length; i++) {
			avgPingMilliseconds[i] = new RunningAverageMetric(
					PING_CATEGORY,
					String.format("ping_ms_%02d", i),
					String.format("milliseconds to send node %02d a byte and receive a reply", i),
					FORMAT_4_2,
					Settings.halfLife
			);
		}
		for (int i = 0; i < avgBytePerSecSent.length; i++) {
			avgBytePerSecSent[i] = new SpeedometerMetric(
					BPSS_CATEGORY,
					String.format("bytes/sec_sent_%02d", i),
					String.format("bytes per second sent to node %02d", i),
					FORMAT_16_2,
					Settings.halfLife);
		}
		for (final PerSecondStat perSecondStat : perSecondStats) {
			additionalEntries.addAll(perSecondStat.getStats());
		}
		createStatEntriesArray(platform, additionalEntries);
		setUpStatEntries();
	}

	/**
	 * reset all the Speedometer and RunningAverage objects with a half life of Platform.halfLife
	 */
	@Override
	public void resetAllSpeedometers() {
		super.resetAllSpeedometers();

		for (final RunningAverageMetric avgPingSecond : avgPingMilliseconds) {
			avgPingSecond.reset();
		}
		for (final SpeedometerMetric abpss : avgBytePerSecSent) {
			abpss.reset();
		}
	}

	/**
	 * Returns the time for a round-trip message to each member (in milliseconds).
	 * <p>
	 * This is an exponentially-weighted average of recent ping times.
	 *
	 * @return the average times, for each member, in milliseconds
	 */
	public double[] getPingMilliseconds() {
		final double[] times = new double[avgPingMilliseconds.length];
		for (int i = 0; i < times.length; i++) {
			times[i] = avgPingMilliseconds[i].get();
		}
		times[platform.getSelfId().getIdAsInt()] = 0;
		return times;
	}

	/**
	 * return an array of info about all the statistics
	 *
	 * @return the array of StatEntry elements for every statistic managed by this class
	 */
	@Override
	public Metric[] getStatEntriesArray() {
		return statEntries;
	}

	/**
	 * Create all the data for the statEntry array. This must be called before getStatEntriesArray
	 *
	 * @param platform
	 * 		link to the platform instance
	 * @param additionalEntries
	 * 		additional stat entries to be added
	 */
	private void createStatEntriesArray(final AbstractPlatform platform, final List<Metric> additionalEntries) {
		statEntries = new Metric[] {
				new FunctionGauge<>(
						INFO_CATEGORY,
						"time",
						"the current time",
						"%25s",
						() -> DateTimeFormatter
								.ofPattern("yyyy-MM-dd HH:mm:ss z")
								.format(Instant.now()
										.atZone(ZoneId.of("UTC")))),
				numTrans,
				avgReceivedConsensusTime,
				avgCreatedConsensusTime,
				avgConsHandleTime,
				roundsPerSecond,
				avgPing,
				bytesPerSecondTrans,
				bytesPerSecondSent,
				bytesPerSecondCatchupSent,
				bytesPerSecondSys,
				transactionsPerSecond,
				eventsPerSecond,
				duplicateEventsPerSecond,
				avgDuplicatePercent,
				callSyncsPerSecond,
				recSyncsPerSecond,
				interruptedCallSyncsPerSecond,
				interruptedRecSyncsPerSecond,
				memFree,
				memTot,
				memMax,
				directMemInMB,
				directMemPercent,
				avgNumProc,
				cpuLoadSys,
				threads,
				new FunctionGauge<>(
						INFO_CATEGORY,
						"name",
						"name of this member",
						"%8s",
						() -> {
							if (platform.isMirrorNode()) {
								return "Mirror-" + platform.getSelfId().getId();
							}
							return platform.getAddressBook()
									.getAddress(platform.getSelfId().getId())
									.getSelfName();
						}),
				avgSelfId,
				avgNumMembers,
				avgWrite,
				avgBytesPerTransaction,
				avgTransactionsPerEvent,
				avgSimCallSyncsMax,
				avgSimSyncs,
				avgSimListenSyncs,
				eventsCreatedPerSecond,
				avgCreatedReceivedTime,
				avgCreatedReceivedConsensusTime,
				avgFirstEventInRoundReceivedTime,
				avgReceivedFamousTime,
				avgQSignedStateEvents,
				avgQ4forHash,
				avgRoundSupermajority,
				avgSelfCreatedTimestamp,
				avgOtherReceivedTimestamp,
				avgEventsInMem,
				transactionsPerSecondSys,
				avgBytesPerTransactionSys,
				avgTransactionsPerEventSys,
				avgConnsCreated,
				new FunctionGauge<>(
						INFO_CATEGORY,
						"TLS",
						"1 if using TLS, 0 if not",
						"%6d",
						() -> Settings.useTLS ? 1 : 0),
				fracSyncSlowed,
				timeFracDot,
				timeFracAdd,
				sleep1perSecond,
				transHandledPerSecond,
				avgSecTransHandled,
				avgStateCopyMicros,
				avgSecNewSignedState,
				avgBytesPerSecSync,
				avgStateSigs,
				numCoinRounds,

				new FunctionGauge<>(
						INFO_CATEGORY,
						"lastGen",
						"last event generation number by me",
						"%d",
						() -> {
							if (platform.isMirrorNode()) {
								return -1;
							}
							return platform.getLastGen(platform.getSelfId().getId());
						}),

				new FunctionGauge<>(
						INFO_CATEGORY,
						"transEvent",
						"transEvent queue size",
						"%d",
						this::getTransEventSize),

				new FunctionGauge<>(
						INFO_CATEGORY,
						"transCons",
						"transCons queue size",
						"%d",
						this::getTransConsSize),

				// Statistics for monitoring transaction and event creation logic

				new FunctionGauge<>(
						INTERNAL_CATEGORY,
						"isEvFrozen",
						"isEventCreationFrozen",
						"%b",
						() -> platform.getFreezeManager().isEventCreationFrozen()
								|| platform.getStartUpEventFrozenManager().isEventCreationPausedAfterStartUp()),

				new FunctionGauge<>(
						INTERNAL_CATEGORY,
						"isStrongMinorityInMaxRound",
						"isStrongMinorityInMaxRound",
						"%b",
						() -> {
							if (platform.isMirrorNode()) {
								return false;
							}
							return platform.getCriticalQuorum().isInCriticalQuorum(platform.getSelfId().getId());
						}),

				new FunctionGauge<>(
						INTERNAL_CATEGORY,
						"transThrottleCallAndCreate",
						"transThrottleCallAndCreate",
						"%b",
						() -> platform.getSyncManager() == null ? 0 :
								platform.getSyncManager().transThrottleCallAndCreate()),

				new FunctionGauge<>(
						INTERNAL_CATEGORY,
						"getNumUserTransEvents",
						"getNumUserTransEvents",
						"%d",
						() -> platform.getTransactionTracker().getNumUserTransEvents()),

				new FunctionGauge<>(
						INTERNAL_CATEGORY,
						"hasFallenBehind",
						"hasFallenBehind",
						"%b",
						() -> platform.getSyncManager() == null ? 0 :
								platform.getSyncManager().hasFallenBehind()),

				shouldCreateEvent,

				new FunctionGauge<>(
						INTERNAL_CATEGORY,
						"numReportFallenBehind",
						"numReportFallenBehind",
						"%d",
						() -> platform.getSyncManager() == null ? 0 :
								platform.getSyncManager().numReportedFallenBehind()),

				staleEventsPerSecond,
				staleEventsTotal,

				rescuedEventsPerSecond,

				new FunctionGauge<>(
						INTERNAL_CATEGORY,
						"DiskspaceFree",
						"disk space being used right now",
						"%d",
						rootDirectory::getFreeSpace),
				new FunctionGauge<>(
						INTERNAL_CATEGORY,
						"DiskspaceWhole",
						"total disk space available on node",
						"%d",
						rootDirectory::getTotalSpace),
				new FunctionGauge<>(
						INTERNAL_CATEGORY,
						"DiskspaceUsed",
						"disk space free for use by the node",
						"%d",
						() -> rootDirectory.getTotalSpace() - rootDirectory.getFreeSpace()),
				eventStreamQueueSize,
				hashQueueSize,
				avgShuffleMicros,
				avgStateToHashSignDepth,
				avgStateToHashSignDepth,
				stateArchivalQueueAvg,
				stateArchivalTimeAvg,
				stateDeletionQueueAvg,
				stateDeletionTimeAvg,
				tipsPerSync,
				issCount
		};
		final List<Metric> entryList = new ArrayList<>(Arrays.asList(statEntries));

		// runtime objects count and age tracked in RuntimeObjectRegistry
		final List<FunctionGauge<Number>> runtimeObjectEntries = RuntimeObjectRegistry.getTrackedClasses()
				.stream()
				.flatMap(cls -> {
					final String className = cls.getSimpleName();
					FunctionGauge<Number> count = new FunctionGauge<>(
							INTERNAL_CATEGORY,
							"countInMemory" + className,
							"the number of " + className + " objects in memory",
							"%d",
							() -> RuntimeObjectRegistry.getActiveObjectsCount(cls));
					FunctionGauge<Number> maxAge = new FunctionGauge<>(
							INTERNAL_CATEGORY,
							"oldest" + className + "Seconds",
							"the age of the oldest " + className + " object in memory",
							"%d",
							() -> RuntimeObjectRegistry.getOldestActiveObjectAge(cls, Instant.now()).toSeconds());
					return Stream.of(count, maxAge);
				}).toList();
		entryList.addAll(runtimeObjectEntries);

		// additional entries
		entryList.addAll(additionalEntries);

		// atomic stats
		entryList.addAll(avgEventsPerSyncSent.getAllEntries());
		entryList.addAll(avgEventsPerSyncRec.getAllEntries());
		entryList.addAll(avgSyncDuration.getAllEntries());
		entryList.addAll(consensusCycleTiming.getAverageEntries());
		entryList.addAll(newSignedStateCycleTiming.getAverageEntries());
		entryList.add(avgSyncDuration1.getAverageStat());
		entryList.add(avgSyncDuration2.getAverageStat());
		entryList.add(avgSyncDuration3.getAverageStat());
		entryList.add(avgSyncDuration4.getAverageStat());
		entryList.add(avgSyncDuration5.getAverageStat());
		entryList.add(syncGenerationDiff.getStatEntry());
		entryList.add(eventRecRate.getStatEntry());
		entryList.add(rejectedSyncRatio.getStatEntry());
		entryList.add(avgTransSubmitMicros.getStatEntry());
		entryList.add(averageOtherParentAgeDiff.getStatEntry());
		entryList.add(gensWaitingForExpiry.getStatEntry());
		entryList.add(knownSetSize.getStatEntry());
		entryList.add(multiTipsPerSync.getStatEntry());
		entryList.add(preConsHandleTime.getAverageStat());
		entryList.add(preHandleTime.getAverageStat());
		entryList.add(avgQ1PreConsEvents.getAverageStat());
		entryList.add(avgQ1PreConsEvents.getMaxStat());
		entryList.add(avgQ2ConsEvents.getAverageStat());
		entryList.add(avgQ2ConsEvents.getMaxStat());
		entryList.add(avgEventsPerRound.getAverageStat());
		entryList.add(avgEventsPerRound.getMaxStat());

		entryList.addAll(List.of(avgPingMilliseconds));
		entryList.addAll(List.of(avgBytePerSecSent));

		reconnectStatistics.registerStats(entryList);
		setDirectMemMXBean();
		setMaximumDirectMemSizeInMB();

		statEntries = entryList.toArray(statEntries);
	}

	private int getTransEventSize() {
		if (platform.getSwirldStateManager() == null ||
				platform.getSwirldStateManager().getTransactionPool() == null) {
			return 0;
		}
		return platform.getSwirldStateManager().getTransactionPool().getEventSize();
	}

	private long getTransConsSize() {
		if (platform.getSwirldStateManager() == null
				|| platform.getSwirldStateManager() instanceof SwirldStateManagerDouble) {
			return 0;
		}
		return ((SwirldStateSingleTransactionPool) platform.getSwirldStateManager().getTransactionPool()).getConsSize();
	}

	//
	// direct memory stats methods below
	//

	private void setDirectMemMXBean() {
		// scan through PlatformMXBeans to find the one responsible for direct memory used
		final List<BufferPoolMXBean> pools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
		for (final BufferPoolMXBean pool : pools) {
			if (pool.getName().equals("direct")) {
				directMemMXBean = pool;
				return;
			}
		}
	}

	private void setMaximumDirectMemSizeInMB() {
		final HotSpotDiagnosticMXBean hsdiag = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
		long maxDirectMemoryInBytes = Runtime.getRuntime().maxMemory();
		if (hsdiag != null) {
			try {
				final long value = Long.parseLong(hsdiag.getVMOption("MaxDirectMemorySize").getValue());
				if (value > 0) {
					maxDirectMemoryInBytes = value;
				}
			} catch (final NumberFormatException ex) {
				// just use the present value, namely Runtime.getRuntime().maxMemory().
			}
		}
		maximumDirectMemSizeInMB = maxDirectMemoryInBytes * Units.BYTES_TO_MEBIBYTES;
	}

	//
	// ConsensusStats below
	//

	/**
	 * Time when this platform received the first event created by someone else in the most recent round.
	 * This is used to calculate Statistics.avgFirstEventInRoundReceivedTime which is "time for event, from
	 * receiving the first event in a round to the first event in the next round".
	 */
	private volatile Instant firstEventInLastRoundTime = null;
	/** the max round number for which at least one event is known that was created by someone else */
	private volatile long lastRoundNumber = -1;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addedEvent(final EventImpl event) {
		// this method is only ever called by 1 thread, so no need for locks
		if (!platform.getSelfId().equalsMain(event.getCreatorId())
				&& event.getRoundCreated() > lastRoundNumber) {// if first event in a round
			final Instant now = Instant.now();
			if (firstEventInLastRoundTime != null) {
				avgFirstEventInRoundReceivedTime.recordValue(
						firstEventInLastRoundTime.until(now,
								ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
			}
			firstEventInLastRoundTime = now;
			lastRoundNumber = event.getRoundCreated();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void coinRounds(final long numCoinRounds) {
		this.numCoinRounds.recordValue(numCoinRounds);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void lastFamousInRound(final EventImpl event) {
		if (!platform.getSelfId().equalsMain(event.getCreatorId())) {// record this for events received
			avgReceivedFamousTime.recordValue(
					event.getTimeReceived().until(Instant.now(),
							ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void consensusReached(final EventImpl event) {
		// Keep a running average of how many seconds from when I first know of an event
		// until it achieves consensus. Actually, keep two such averages: one for events I
		// create, and one for events I receive.
		// Because of transThrottle, these statistics can end up being misleading, so we are only tracking events that
		// have user transactions in them.
		if (event.hasUserTransactions()) {
			if (platform.getSelfId().equalsMain(event.getCreatorId())) { // set either created or received time to now
				avgCreatedConsensusTime
						.recordValue(event.getTimeReceived().until(Instant.now(),
								ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
			} else {
				avgReceivedConsensusTime
						.recordValue(event.getTimeReceived().until(Instant.now(),
								ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
				avgCreatedReceivedConsensusTime
						.recordValue(event.getTimeCreated().until(Instant.now(),
								ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
			}
		}

		// Because of transThrottle, these statistics can end up being misleading, so we are only tracking events that
		// have user transactions in them.
		if (event.hasUserTransactions()) {
			if (platform.getSelfId().equalsMain(event.getCreatorId())) {
				avgSelfCreatedTimestamp.recordValue(
						event.getTimeCreated().until(event.getConsensusTimestamp(),
								ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
			} else {
				avgOtherReceivedTimestamp.recordValue(
						event.getTimeReceived().until(event.getConsensusTimestamp(),
								ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void consensusReachedOnRound() {
		roundsPerSecond.cycle();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dotProductTime(final long nanoTime) {
		timeFracDot.update(nanoTime * NANOSECONDS_TO_SECONDS);
	}

	//
	// HashgraphStats below
	//

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void staleEvent(final EventImpl event) {
		staleEventsTotal.increment();
		staleEventsPerSecond.cycle();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void rescuedEvent() {
		rescuedEventsPerSecond.cycle();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void duplicateEvent() {
		duplicateEventsPerSecond.cycle();
		avgDuplicatePercent.recordValue(100); // move toward 100%
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void nonDuplicateEvent() {
		avgDuplicatePercent.recordValue(0); // move toward 0%
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processedEventTask(final long startTime) {
		// nanoseconds spent adding to hashgraph
		timeFracAdd.update(((double) time() - startTime) * NANOSECONDS_TO_SECONDS);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void eventAdded(final EventImpl event) {
		if (event.isCreatedBy(platform.getSelfId())) {
			eventsCreatedPerSecond.cycle();
			if (event.getBaseEventHashedData().hasOtherParent()) {
				averageOtherParentAgeDiff.update(event.getGeneration() - event.getOtherParentGen());
			}
		} else {
			avgCreatedReceivedTime.recordValue(
					event.getTimeCreated().until(event.getTimeReceived(),
							ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
		}

		// count the unique events in the hashgraph
		eventsPerSecond.cycle();

		// record stats for all transactions in this event
		final ConsensusTransaction[] trans = event.getTransactions();
		final int numTransactions = (trans == null ? 0 : trans.length);

		// we have already ensured this isn't a duplicate event, so record all the stats on it:

		// count the bytes in the transactions, and bytes per second, and transactions per event
		// for both app transactions and system transactions.
		// Handle system transactions
		int appSize = 0;
		int sysSize = 0;
		int numAppTrans = 0;
		int numSysTrans = 0;
		for (int i = 0; i < numTransactions; i++) {
			if (trans[i].isSystem()) {
				numSysTrans++;
				sysSize += trans[i].getSerializedLength();
				avgBytesPerTransactionSys.recordValue(trans[i].getSerializedLength());
			} else {
				numAppTrans++;
				appSize += trans[i].getSerializedLength();
				avgBytesPerTransaction.recordValue(trans[i].getSerializedLength());
			}
		}
		avgTransactionsPerEvent.recordValue(numAppTrans);
		avgTransactionsPerEventSys.recordValue(numSysTrans);
		bytesPerSecondTrans.update(appSize);
		bytesPerSecondSys.update(sysSize);
		// count each transaction within that event (this is like calling cycle() numTrans times)
		transactionsPerSecond.update(numAppTrans);
		transactionsPerSecondSys.update(numSysTrans);

		// count all transactions ever in the hashgraph
		if (!event.isEmpty()) {
			numTrans.add(event.getTransactions().length);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateArchivalQueue(final int len) {
		stateArchivalQueueAvg.recordValue(len);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateArchivalTime(final double time) {
		stateArchivalTimeAvg.recordValue(time);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateDeletionQueue(final int len) {
		stateDeletionQueueAvg.recordValue(len);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateDeletionTime(final double time) {
		stateDeletionTimeAvg.recordValue(time);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateMultiTipsPerSync(final int multiTipCount) {
		multiTipsPerSync.update(multiTipCount);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateTipsPerSync(final int tipCount) {
		tipsPerSync.recordValue(tipCount);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateGensWaitingForExpiry(final long numGenerations) {
		gensWaitingForExpiry.update(numGenerations);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateRejectedSyncRatio(final boolean syncRejected) {
		rejectedSyncRatio.update(syncRejected);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateTransSubmitMicros(final long microseconds) {
		avgTransSubmitMicros.update(microseconds);
	}

	public ReconnectStatistics getReconnectStats() {
		return reconnectStatistics;
	}

	@Override
	public void generations(final GraphGenerations self, final GraphGenerations other) {
		syncGenerationDiff.update(self.getMaxRoundGeneration() - other.getMaxRoundGeneration());
	}

	@Override
	public void eventsReceived(final long nanosStart, final int numberReceived) {
		if (numberReceived == 0) {
			return;
		}
		final double nanos = ((double) System.nanoTime()) - nanosStart;
		final double seconds = nanos / ChronoUnit.SECONDS.getDuration().toNanos();
		eventRecRate.update(Math.round(numberReceived / seconds));
	}

	@Override
	public void syncThrottleBytesWritten(final int bytesWritten) {
		bytesPerSecondCatchupSent.update(bytesWritten);
		fracSyncSlowed.recordValue(bytesWritten > 0 ? 1 : 0);
	}

	@Override
	public void recordSyncTiming(final SyncTiming timing, final Connection conn) {
		avgSyncDuration1.update(timing.getTimePoint(0), timing.getTimePoint(1));
		avgSyncDuration2.update(timing.getTimePoint(1), timing.getTimePoint(2));
		avgSyncDuration3.update(timing.getTimePoint(2), timing.getTimePoint(3));
		avgSyncDuration4.update(timing.getTimePoint(3), timing.getTimePoint(4));
		avgSyncDuration5.update(timing.getTimePoint(4), timing.getTimePoint(5));

		avgSyncDuration.update(timing.getTimePoint(0), timing.getTimePoint(5));
		final double syncDurationSec = timing.getPointDiff(5, 0) * Units.NANOSECONDS_TO_SECONDS;
		final double speed =
				Math.max(conn.getDis().getSyncByteCounter().getCount(), conn.getDos().getSyncByteCounter().getCount())
						/ syncDurationSec;

		// set the bytes/sec speed of the sync currently measured
		avgBytesPerSecSync.recordValue(speed);
	}

	@Override
	public CycleTimingStat getConsCycleStat() {
		return consensusCycleTiming;
	}

	@Override
	public CycleTimingStat getNewSignedStateCycleStat() {
		return newSignedStateCycleTiming;
	}

	/**
	 * Records the number of events in a round.
	 *
	 * @param numEvents
	 * 		the number of events in the round
	 */
	@Override
	public void recordEventsPerRound(final int numEvents) {
		avgEventsPerRound.update(numEvents);
	}

	@Override
	public void knownSetSize(final int knownSetSize) {
		this.knownSetSize.update(knownSetSize);
	}

	@Override
	public void syncDone(final SyncResult info) {
		if (info.isCaller()) {
			callSyncsPerSecond.cycle();
		} else {
			recSyncsPerSecond.cycle();
		}
		avgEventsPerSyncSent.update(info.getEventsWritten());
		avgEventsPerSyncRec.update(info.getEventsRead());
	}

	@Override
	public void eventCreation(final boolean shouldCreateEvent) {
		this.shouldCreateEvent.recordValue(shouldCreateEvent ? 1 : 0);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void recordNewSignedStateTime(final double seconds) {
		avgSecNewSignedState.recordValue(seconds);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void consensusTransHandleTime(final double seconds) {
		avgSecTransHandled.recordValue(seconds);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void consensusToHandleTime(final double seconds) {
		avgConsHandleTime.recordValue(seconds);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void consensusTransHandled(final int numTrans) {
		transHandledPerSecond.update(numTrans);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shuffleMicros(final double micros) {
		avgShuffleMicros.recordValue(micros);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void stateCopyMicros(final double micros) {
		avgStateCopyMicros.recordValue(micros);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getAvgSelfCreatedTimestamp() {
		return avgSelfCreatedTimestamp.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getAvgOtherReceivedTimestamp() {
		return avgOtherReceivedTimestamp.get();
	}

	@Override
	public void setIssCount(final int issCount) {
		this.issCount.set(issCount);
	}

	@Override
	public void preConsensusHandleTime(final long start, final long end) {
		this.preConsHandleTime.update(start, end);
	}

	@Override
	public void preHandleTime(final long start, final long end) {
		this.preHandleTime.update(start, end);
	}

	@Override
	public void connectionEstablished(final Connection connection) {
		if (connection == null) {
			return;
		}
		connections.add(connection);
		connsCreated.incrementAndGet(); // count new connections
	}

	@Override
	public void recordPingTime(final NodeId node, final long pingNanos) {
		avgPingMilliseconds[node.getIdAsInt()].recordValue((pingNanos) / 1_000_000.0);
	}
}
