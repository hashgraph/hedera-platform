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

package com.swirlds.platform.sync;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.BadIOException;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.event.ValidateEventTask;
import com.swirlds.platform.stats.SyncStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.swirlds.logging.LogMarker.SYNC_INFO;

public final class SyncComms {
	private static final Logger LOG = LogManager.getLogger();

	// Prevent instantiations of this static utility class
	private SyncComms() {
	}

	public static void writeFirstByte(final SyncConnection conn) throws IOException {
		if (conn.isOutbound()) { // caller WRITE sync request
			// try to initiate a sync
			conn.getDos().requestSync();
		} else { // listener WRITE sync request response
			conn.getDos().acceptSync();
		}
		// no need to flush, since we will write more data right after
	}


	public static void rejectSync(final SyncConnection conn, final int numberOfNodes) throws IOException {
		// respond with a nack
		conn.getDos().rejectSync();
		conn.getDos().flush();

		// read data and ignore since we rejected the sync
		conn.getDis().readGenerations();
		conn.getDis().readTipHashes(numberOfNodes);
	}

	public static Callable<Void> phase1Write(
			final SyncConnection conn,
			final SyncGenerations generations,
			final List<ShadowEvent> tips) {
		return () -> {
			final List<Hash> tipHashes = tips.stream().map(ShadowEvent::getEventBaseHash).collect(Collectors.toList());
			conn.getDos().writeGenerations(generations);
			conn.getDos().writeTipHashes(tipHashes);
			conn.getDos().flush();
			LOG.info(SYNC_INFO.getMarker(), "{} sent generations: {}",
					conn::getDescription, generations::toString);
			LOG.info(SYNC_INFO.getMarker(), "{} sent tips: {}",
					conn::getDescription, () -> SyncLogging.toShortShadows(tips));
			return null;
		};
	}

	public static Callable<Phase1Response> phase1Read(
			final SyncConnection conn,
			final int numberOfNodes) {
		return () -> {
			// Caller thread requested a sync, so now caller thread reads if its request was accepted.
			if (conn.isOutbound()) {
				try {
					if (!conn.getDis().readSyncRequestResponse()) {
						// sync rejected
						return Phase1Response.syncRejected();
					}
				} catch (SyncException | IOException e) {
					final Instant sentTime = conn.getDos().getRequestSentTime();
					final long inMs = sentTime == null ? -1 : sentTime.until(Instant.now(), ChronoUnit.MILLIS);
					throw new SyncException(conn,
							"Problem while reading sync request response. Request was sent "
									+ inMs + "ms ago", e);
				}
			}

			final SyncGenerations generations = conn.getDis().readGenerations();
			final List<Hash> tips = conn.getDis().readTipHashes(numberOfNodes);

			LOG.info(SYNC_INFO.getMarker(), "{} received generations: {}",
					conn::getDescription, generations::toString);
			LOG.info(SYNC_INFO.getMarker(), "{} received tips: {}",
					conn::getDescription, () -> SyncLogging.toShortHashes(tips));

			return Phase1Response.create(generations, tips);
		};
	}

	/**
	 * @return the Callable to run
	 */
	public static Callable<Void> phase2Write(
			final SyncConnection conn,
			final List<Boolean> booleans) {
		return () -> {
			conn.getDos().writeBooleanList(booleans);
			conn.getDos().flush();
			LOG.info(SYNC_INFO.getMarker(), "{} sent booleans: {}",
					conn::getDescription, () -> SyncLogging.toShortBooleans(booleans));
			return null;
		};
	}

	/**
	 * @return the Callable to run
	 */
	public static Callable<List<Boolean>> phase2Read(
			final SyncConnection conn,
			final int numberOfTips) {
		return () -> {
			final List<Boolean> booleans = conn.getDis().readBooleanList(numberOfTips);
			if (booleans == null) {
				throw new SyncException(conn, "peer sent null booleans");
			}
			LOG.info(SYNC_INFO.getMarker(),
					"{} received booleans: {}",
					conn::getDescription,
					() -> SyncLogging.toShortBooleans(booleans));
			return booleans;
		};
	}

	public static Callable<Void> phase3Write(
			final SyncConnection conn,
			final List<EventImpl> events) {
		return () -> {
			LOG.info(SYNC_INFO.getMarker(), "{} writing events start. send list size: {}",
					conn.getDescription(), events.size());
			for (final EventImpl event : events) {
				conn.getDos().writeByte(SyncConstants.COMM_EVENT_NEXT);
				conn.getDos().writeEventData(event);
			}
			conn.getDos().writeByte(SyncConstants.COMM_EVENT_DONE);
			conn.getDos().flush();

			LOG.info(SYNC_INFO.getMarker(), "{} writing events done, wrote {} events",
					conn.getDescription(), events.size());

			// (ignored)
			return null;
		};
	}

	public static Callable<Integer> phase3Read(
			final SyncConnection conn,
			final Consumer<ValidateEventTask> eventHandler,
			final SyncStats stats) {
		return () -> {
			LOG.info(SYNC_INFO.getMarker(), "{} reading events start", conn.getDescription());
			int eventsRead = 0;
			long startTime = Long.MIN_VALUE;
			while (true) {
				final byte next = conn.getDis().readByte();
				if (startTime == Long.MIN_VALUE) {
					startTime = System.nanoTime();
				}
				if (next == SyncConstants.COMM_EVENT_DONE) {
					break;
				} else if (next == SyncConstants.COMM_EVENT_NEXT) {
					final ValidateEventTask validateEventTask = conn.getDis().readEventData();
					eventHandler.accept(validateEventTask);
					eventsRead++;
				} else {
					throw new SyncException(conn,
							String.format("while reading events, received unexpected byte %02x", next));
				}
			}
			stats.eventsReceived(startTime, eventsRead);

			LOG.info(SYNC_INFO.getMarker(), "{} reading events done, read {} events",
					conn.getDescription(), eventsRead);
			return eventsRead;
		};
	}

	public static void syncDoneByte(final SyncConnection conn) throws IOException {
		// we have now finished reading and writing all the events of a sync. the remote node may not have
		// finished reading and processing all the events this node has sent. so we write a byte to tell the remote
		// node we have finished, and we wait for it to send us the same byte.
		conn.getDos().writeByte(SyncConstants.COMM_SYNC_DONE);
		conn.getDos().flush();

		LOG.debug(SYNC_INFO.getMarker(), "{} sent COMM_SYNC_DONE", conn.getDescription());

		final byte done = conn.getDis().readByte();

		if (done != SyncConstants.COMM_SYNC_DONE) {
			throw new BadIOException(
					"received " + done + " instead of COMM_SYNC_DONE");
		}

		LOG.debug(SYNC_INFO.getMarker(), "{} received COMM_SYNC_DONE", conn.getDescription());
	}

}
