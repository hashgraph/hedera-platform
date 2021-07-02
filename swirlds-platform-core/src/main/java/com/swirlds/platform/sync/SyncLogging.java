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

package com.swirlds.platform.sync;

import com.swirlds.common.NodeId;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.events.Event;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.components.EventMapper;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.swirlds.logging.LogMarker.SYNC_SGM;

/**
 * Utility routines to generate formatted log string for sync-related variables.
 */
public final class SyncLogging {

	/**
	 * This type is not constructable
	 */
	private SyncLogging() {
	}


	/**
	 * Get the log string prefix to use for a given connection
	 *
	 * @param conn
	 * 		the {@link SyncConnection} used for a gossip session
	 * @param caller
	 * 		true iff this node is the caller
	 * @return the log string prefix
	 */
	public static String getSyncLogString(final SyncConnection conn, final boolean caller) {
		return getSyncLogString(conn.getSelfId(), conn.getOtherId(), caller);
	}

	public static final int BRIEF_HASH_LENGTH = 4;

	/**
	 * Get an abbreviated log string for a cryptographic hash
	 *
	 * @param hash
	 * 		the hash
	 * @return an abbreviated representation of the hash
	 */
	public static String briefHash(final Hash hash) {
		if (hash == null) {
			return "null";
		} else {
			return hash.toString().substring(0, BRIEF_HASH_LENGTH);
		}
	}

	/**
	 * Write a sequence of log strings, one for each marked shadow event
	 *
	 * @param shadowGraph
	 * 		the shadow graph with whose marked events are to be logged
	 * @param syncData
	 * 		the {@link SyncData} instance which holds the marks
	 * @param log
	 * 		the Log4j2 logging instance to use
	 * @param syncLogString
	 * 		the string prefix for the log entry
	 */
	public static void logMarkedEvents(
			final SyncShadowGraph shadowGraph,
			final SyncData syncData,
			final Logger log,
			final String syncLogString) {
		for (final SyncShadowEvent s : shadowGraph) {
			if (syncData.markedForSync(s)) {
				log.debug(SYNC_SGM.getMarker(),
						"{}  sync-marked event is {}",
						() -> syncLogString,
						() -> getSyncLogString(s));
			}

			if (syncData.markedForSearch(s)) {
				log.debug(SYNC_SGM.getMarker(),
						"{}  search-marked event is {} ",
						() -> syncLogString,
						() -> getSyncLogString(s));
			}
		}
	}

	/**
	 * Write the number of sync-marked and search-marked shadow events
	 *
	 * @param syncData
	 * 		the {@link SyncData} instance which holds the marks
	 * @param log
	 * 		the Log4j2 logging instance to use
	 * @param syncLogString
	 * 		the string prefix for the log entry
	 */
	public static void logMarkCounts(
			final SyncData syncData,
			final Logger log,
			final String syncLogString) {
		log.debug(SYNC_SGM.getMarker(),
				"{}{} sync-marked events, {} search-marked events",
				syncLogString,
				syncData.getNumMarkedForSync(),
				syncData.getNumMarkedForSearch());
	}

	/**
	 * Write a sequence log entries, one for each hashgraph tip event hash
	 * received from a gossip peer.
	 *
	 * @param syncData
	 * 		the {@link SyncData} instance which holds the tip hashes
	 * @param log
	 * 		the Log4j2 logging instance to use
	 * @param syncLogString
	 * 		the string prefix for the log entry
	 */
	public static void logReceivedTipHashes(
			final SyncData syncData,
			final Logger log,
			final String syncLogString) {
		log.debug(SYNC_SGM.getMarker(),
				"{}received {} tip hashes",
				syncLogString,
				syncData.getReceivedTipHashes().size());
		for (int i = 0; i < syncData.getReceivedTipHashes().size(); ++i) {
			final int ii = i;
			log.debug(SYNC_SGM.getMarker(),
					"{}  [{}] received tip hash {}",
					() -> syncLogString,
					() -> ii,
					() -> briefHash(syncData.getReceivedTipHashes().get(ii)));
		}
	}

	/**
	 * Write a sequence strings, "true" or "false", one for each hashgraph tip event
	 * received from a gossip peer.
	 *
	 * @param tipBooleans
	 * 		the tip booleans to log
	 * @param log
	 * 		the Log4j2 logging instance to use
	 * @param syncLogString
	 * 		the string prefix for the log entry
	 */
	public static void logReceivedTipBooleans(
			final List<Boolean> tipBooleans,
			final Logger log,
			final String syncLogString) {
		log.debug(SYNC_SGM.getMarker(),
				"{}received {} tip booleans",
				syncLogString,
				tipBooleans.size());

		for (int i = 0; i < tipBooleans.size(); ++i) {
			log.debug(SYNC_SGM.getMarker(),
					"{}  [{}] received tip boolean {}",
					syncLogString,
					i,
					tipBooleans.get(i));
		}
	}

	/**
	 * Write a sequence strings, "true" or "false", one for each hashgraph tip event
	 * sent to a gossip peer.
	 *
	 * @param tipBooleans
	 * 		the tip booleans to log
	 * @param log
	 * 		the Log4j2 logging instance to use
	 * @param syncLogString
	 * 		the string prefix for the log entry
	 */
	public static void logSentTipBooleans(
			final List<Boolean> tipBooleans,
			final Logger log,
			final String syncLogString) {
		log.debug(SYNC_SGM.getMarker(),
				"{}sent {} tip booleans",
				syncLogString,
				tipBooleans.size());

		for (int i = 0; i < tipBooleans.size(); ++i) {
			log.debug(SYNC_SGM.getMarker(),
					"{}  [{}] sent tip boolean {}",
					syncLogString,
					i,
					tipBooleans.get(i));
		}
	}

	/**
	 * Write a sequence of log entries, one for each hashgraph tip event
	 * from the set of tip events used during gossiping execution
	 *
	 * @param syncData
	 * 		the {@link SyncData} instance which holds the tip hashes
	 * @param log
	 * 		the Log4j2 logging instance to use
	 * @param syncLogString
	 * 		the string prefix for the log entry
	 */
	public static void logWorkingTipHashes(
			final SyncData syncData,
			final Logger log,
			final String syncLogString) {
		log.debug(SYNC_SGM.getMarker(),
				"{}{} working tip hashes",
				syncLogString,
				syncData.getWorkingTips().size());
		for (final SyncShadowEvent tip : syncData.getWorkingTips()) {
			log.debug(SYNC_SGM.getMarker(),
					"{}       working tip hash {}",
					() -> syncLogString,
					() -> briefHash(tip.getEventBaseHash()));
		}
	}

	/**
	 * Write a sequence of log entries, one for each of a set of tip event hashes
	 * to be sent to a gossip peer
	 *
	 * @param tipHashes
	 * 		a list tip event hashes to log
	 * @param log
	 * 		the Log4j2 logging instance to use
	 * @param syncLogString
	 * 		the string prefix for the log entry
	 */
	public static void logSentTipHashes(
			final List<Hash> tipHashes,
			final Logger log,
			final String syncLogString) {
		log.debug(SYNC_SGM.getMarker(),
				"{}{} tip hashes to send",
				syncLogString,
				tipHashes.size());
		for (int i = 0; i < tipHashes.size(); ++i) {
			final int ii = i;
			log.debug(
					SYNC_SGM.getMarker(),
					"{}   [{}] sent tip hash {}",
					() -> syncLogString,
					() -> ii,
					() -> briefHash(tipHashes.get(ii)));
		}
	}

	/**
	 * Write a sequence of log entries, one for each recent shadow graph event.
	 * Recency is determined by event generation number.
	 *
	 * @param sgm
	 * 		a shadow graph on this node
	 * @param minGeneration
	 * 		the minimum generation number to lof
	 * @param log
	 * 		the Log4j2 logging instance to use
	 * @param syncLogString
	 * 		the string prefix for the log entry
	 */
	public static synchronized void logShadowGraph(
			final SyncShadowGraphManager sgm,
			final long minGeneration,
			final Logger log,
			final String syncLogString) {
		log.debug(SYNC_SGM.getMarker(),
				"{}++ Shadow graph is ({} events total, showing only events with generation >= {})",
				syncLogString,
				sgm.getNumShadowEvents(),
				minGeneration);
		final Set<SyncShadowEvent> visited = new HashSet<>();
		final Set<SyncShadowEvent> tips = new HashSet<>(sgm.getTips());
		for (SyncShadowEvent t : tips) {
			visited.add(t);
			if (t.getEvent().getGeneration() >= minGeneration) {
				final SyncShadowEvent tt = t;
				log.debug(SYNC_SGM.getMarker(),
						"{}++ t {}",
						() -> syncLogString,
						() -> getSyncLogString(tt));
			}

			while (t != null) {
				if (visited.contains(t)) {
					t = t.getSelfParent();
					continue;
				}

				visited.add(t);

				if (t.getEvent().getGeneration() >= minGeneration) {
					final SyncShadowEvent tt = t;
					log.debug(SYNC_SGM.getMarker(),
							"{}++   {}",
							() -> syncLogString,
							() -> getSyncLogString(tt));
				}

				t = t.getSelfParent();
			}

		}
	}


	/**
	 * Write a log entry for this node at time of call, which includes
	 * <p>
	 * 1. generation numbers (max, min non-ancient, min non-expired)
	 * 2. number  of hashgraph events with {@code null} hashes.
	 * 3. number of shadow events
	 * 4. number of tip events
	 * </p>
	 *
	 * @param sgm
	 * 		the node's shadow graph
	 * @param consensus
	 * 		this node's consensus hashgraph
	 * @param eventMapper
	 * 		the event mapper for this node's consensus hashgraph
	 * @param log
	 * 		the Log4j2 logging instance to use
	 * @param syncLogString
	 * 		the string prefix for the log entry
	 */
	public static void log(
			final SyncShadowGraphManager sgm,
			final Consensus consensus,
			final EventMapper eventMapper,
			final Logger log,
			final String syncLogString) {
		final EventImpl[] allEvents = consensus.getAllEvents();
		int currentEventNullHashCount = 0;
		int nExpired = 0;
		int nAncient = 0;
		final long minGenerationNonAncient = consensus.getMinGenerationNonAncient();
		final long minGenerationNonExpired = consensus.getMinGenerationNonExpired();
		final long maxGeneration = eventMapper.getMaxGeneration();

		for (final EventImpl e : allEvents) {
			if (e.getBaseHash() == null && e.getGeneration() >= minGenerationNonAncient) {
				++currentEventNullHashCount;
			}

			if (e.getGeneration() < minGenerationNonExpired) {
				++nExpired;
			} else if (e.getGeneration() < minGenerationNonAncient) {
				++nAncient;
			}
		}

		log.debug(SYNC_SGM.getMarker(),
				"{}Hashgraph events: total: {}, ancient: {}, " +
						"expired: {}, min non-expired gen = {}, min non-ancient gen = {}, max gen = {}",
				syncLogString,
				allEvents.length,
				nAncient,
				nExpired,
				minGenerationNonExpired,
				minGenerationNonAncient,
				maxGeneration);

		if (currentEventNullHashCount > 0) {
			log.debug(SYNC_SGM.getMarker(),
					"{}{} Hashgraph current Events have null hashes",
					syncLogString,
					currentEventNullHashCount);
		}

		log.debug(SYNC_SGM.getMarker(),
				"{}Shadow graph events: total: {}, number of tips {}",
				syncLogString,
				sgm.getNumShadowEvents(),
				sgm.getNumTips());
	}

	/**
	 * Write a log entry for shadow graph verification result
	 *
	 * @param result
	 * 		the result of executing {@code SyncShadowGraphVerification.verify}
	 * @param log
	 * 		the Log4j2 logging instance to use
	 * @param syncLogString
	 * 		the string prefix for the log entry
	 */
	public static void log(
			final SyncShadowGraphVerificationStatus result,
			final Logger log,
			final String syncLogString) {
		switch (result) {
			case VERIFIED:
				break;

			case EVENT_NOT_IN_SHADOW_GRAPH:
			case MISSING_SELF_PARENT:
			case MISSING_OTHER_PARENT:
			case MISSING_TIP:
				log.debug(SYNC_SGM.getMarker(),
						"{}{}, Shadow graph is conditionally verified",
						syncLogString,
						result);
				break;
			default:
				log.debug(SYNC_SGM.getMarker(),
						"{}{}, Shadow graph update failed",
						syncLogString,
						result);
		}
	}

	/**
	 * Get a string representation of a hashgraph event.
	 *
	 * @param event
	 * 		the event
	 * @return a log string
	 */
	public static String getSyncLogString(final Event event) {
		if (event instanceof EventImpl) {
			return EventStrings.toMediumString((EventImpl) event);
		} else {
			return event.getClass().getName();
		}
	}

	/**
	 * Write a sequence of log entries, one for each hashgraph tip event
	 *
	 * @param sgm
	 * 		a shadow graph on this node
	 * @param log
	 * 		the Log4j2 logging instance to use
	 * @param syncLogString
	 * 		the string prefix for the log entry
	 */
	public static void logCurrentTipHashes(
			final SyncShadowGraphManager sgm,
			final Logger log,
			final String syncLogString) {
		log.debug(SYNC_SGM.getMarker(), "{}{} current tip hashes",
				syncLogString,
				sgm.getNumTips());
		for (final SyncShadowEvent t : sgm.getTips()) {
			log.debug(SYNC_SGM.getMarker(),
					"{}   t {}",
					() -> syncLogString,
					() -> briefBaseHash(t.getEvent()));
		}
	}

	/**
	 * Write a sequence of log entries, one for each of a set of tip events
	 *
	 * @param sgm
	 * 		a shadow graph on this node
	 * @param log
	 * 		the Log4j2 logging instance to use
	 * @param syncLogString
	 * 		the string prefix for the log entry
	 */
	public static void logCurrentTips(
			final SyncShadowGraphManager sgm,
			final Logger log,
			final String syncLogString) {
		log.debug(SYNC_SGM.getMarker(),
				"{}{} current tips",
				syncLogString,
				sgm.getNumTips());
		for (final SyncShadowEvent t : sgm.getTips()) {
			log.debug(SYNC_SGM.getMarker(),
					"{}   t {}",
					() -> syncLogString,
					() -> getSyncLogString(t));
		}
	}

	/**
	 * Get a string representation of a shadow event
	 *
	 * @param s
	 * 		the shadow
	 * @return a log string
	 */
	private static String getSyncLogString(final SyncShadowEvent s) {
		return getSyncLogString(s.getEvent());
	}

	/**
	 * Get an abbreviated log string for the cryptographic base hash of an event
	 *
	 * @param e
	 * 		the event
	 * @return an abbreviated representation of the base hash of the event
	 */
	private static String briefBaseHash(final Event e) {
		if (e == null) {
			return "null";
		} else {
			return briefHash(e.getBaseHash());
		}
	}

	/**
	 * Get the log string prefix to use for a given caller node and listener node
	 *
	 * @param self
	 * 		this node's ID
	 * @param other
	 * 		the remote node's ID
	 * @param caller
	 * 		true iff this node is the caller
	 * @return the prefix log string
	 */
	private static String getSyncLogString(final NodeId self, final NodeId other, final boolean caller) {
		final String syncLogString;
		if (caller) {
			syncLogString = String.format("%s -> %s", self, other);
		} else {
			syncLogString = String.format("%s <- %s", self, other);
		}

		return syncLogString;
	}


	/**
	 * The value report to the kernel whenever this process is halted by a call to {@link System#exit(int)} in the
	 * routine {@code logAndExit}
	 */
	private static final int EXIT_VALUE = 10;

	/**
	 * This routine does two things:
	 * <p>
	 * 1. Write every shadow event to a log file as a sequence of log statement, one shadow per statement.
	 * <p>
	 * 2. Call {@link System#exit(int)}
	 * <p>
	 * This routine <i>must never</i> be used in production code. It is exclusively for dumping a shadow graph
	 * to a log file whenever a terminal condition has been defined and satisfied. It is set to private
	 * to enforce this.
	 *
	 * @param sgm
	 * 		the {@link SyncShadowGraphManager} instance to use
	 * @param syncData
	 * 		the {@link SyncData} instance to use
	 * @param log
	 * 		the Log4j2 logging instance to use
	 * @param syncLogString
	 * 		the string prefix for the log entry
	 */
	private static void logAndExit(
			final SyncShadowGraphManager sgm,
			final SyncData syncData,
			final Logger log,
			final String syncLogString) {

		final List<EventImpl> sendList = syncData.getSendList();

		log.debug(SYNC_SGM.getMarker(),
				"{}!! Exiting with sendList.size() = {}",
				syncLogString,
				sendList.size());
		log.debug(SYNC_SGM.getMarker(),
				"{}!! sendList is ",
				syncLogString);

		final List<EventImpl> reversedSendList = new ArrayList<>(sendList);
		Collections.reverse(reversedSendList);
		for (final EventImpl e : reversedSendList) {
			log.debug(SYNC_SGM.getMarker(),
					"{}!! s {}",
					() -> syncLogString,
					() -> getSyncLogString(sgm.shadow(e)));
		}

		log.debug(SYNC_SGM.getMarker(),
				"{}!! Exiting with tips.size() = {}",
				syncLogString,
				sgm.getNumTips());
		log.debug(SYNC_SGM.getMarker(),
				"{}!! Tips are",
				syncLogString);
		for (final SyncShadowEvent t : sgm.getTips()) {
			log.debug(SYNC_SGM.getMarker(),
					"{}!! t {}",
					() -> syncLogString,
					() -> getSyncLogString(t));
		}


		log.debug(SYNC_SGM.getMarker(),
				"{}!! shadowGraph.size() = {}",
				syncLogString,
				sgm.getNumShadowEvents());

		log.debug(SYNC_SGM.getMarker(),
				"{} !! Shadow graph is",
				syncLogString);
		for (SyncShadowEvent t : sgm.getTips()) {
			while (t != null) {
				final SyncShadowEvent tt = t;
				log.debug(SYNC_SGM.getMarker(),
						"{}!!   {}",
						() -> syncLogString,
						() -> getSyncLogString(tt));
				t = t.getSelfParent();
			}
		}

		System.exit(EXIT_VALUE);
	}

}
