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

package com.swirlds.platform.sync;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.Connection;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.network.ByteConstants;
import com.swirlds.platform.metrics.SyncMetrics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.swirlds.logging.LogMarker.SYNC_INFO;

public final class SyncComms {
	private static final Logger LOG = LogManager.getLogger();
	/**
	 * send a {@link ByteConstants#COMM_SYNC_ONGOING} every this many milliseconds after we are done writing events,
	 * until we are done reading events
	 */
	private static final int SYNC_ONGOING_SEND_EVERY_MS = 500;
	/**
	 * The maximum time we will allow for phase 3. If phase 3 is not done within this time limit, we will abort the sync
	 */
	private static final Duration PHASE3_MAX_DURATION = Duration.ofMinutes(1);
	/** The value of PHASE3_MAX_DURATION in nanoseconds */
	private static final long PHASE3_MAX_NANOS = PHASE3_MAX_DURATION.toNanos();

	// Prevent instantiations of this static utility class
	private SyncComms() {
	}

	public static void writeFirstByte(final Connection conn) throws IOException {
		if (conn.isOutbound()) { // caller WRITE sync request
			// try to initiate a sync
			conn.getDos().requestSync();
		} else { // listener WRITE sync request response
			conn.getDos().acceptSync();
		}
		// no need to flush, since we will write more data right after
	}


	public static void rejectSync(final Connection conn, final int numberOfNodes) throws IOException {
		// respond with a nack
		conn.getDos().rejectSync();
		conn.getDos().flush();

		// read data and ignore since we rejected the sync
		conn.getDis().readGenerations();
		conn.getDis().readTipHashes(numberOfNodes);
	}

	public static Callable<Void> phase1Write(
			final Connection conn,
			final Generations generations,
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
			final Connection conn,
			final int numberOfNodes,
			final boolean readInitByte) {
		return () -> {
			// Caller thread requested a sync, so now caller thread reads if its request was accepted.
			if (conn.isOutbound() && readInitByte) {
				try {
					if (!conn.getDis().readSyncRequestResponse()) {
						// sync rejected
						return Phase1Response.syncRejected();
					}
				} catch (final SyncException | IOException e) {
					final Instant sentTime = conn.getDos().getRequestSentTime();
					final long inMs = sentTime == null ? -1 : sentTime.until(Instant.now(), ChronoUnit.MILLIS);
					throw new SyncException(conn,
							"Problem while reading sync request response. Request was sent "
									+ inMs + "ms ago", e);
				}
			}

			final Generations generations = conn.getDis().readGenerations();
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
			final Connection conn,
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
			final Connection conn,
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
			final Connection conn,
			final List<EventImpl> events,
			final CountDownLatch eventReadingDone) {
		return () -> {
			LOG.info(SYNC_INFO.getMarker(), "{} writing events start. send list size: {}",
					conn.getDescription(), events.size());
			for (final EventImpl event : events) {
				conn.getDos().writeByte(ByteConstants.COMM_EVENT_NEXT);
				conn.getDos().writeEventData(event);
			}
			conn.getDos().writeByte(ByteConstants.COMM_EVENT_DONE);
			conn.getDos().flush();

			LOG.info(SYNC_INFO.getMarker(), "{} writing events done, wrote {} events",
					conn.getDescription(), events.size());

			// if we are still reading events, send keepalive messages
			while (!eventReadingDone.await(SYNC_ONGOING_SEND_EVERY_MS, TimeUnit.MILLISECONDS)) {
				conn.getDos().writeByte(ByteConstants.COMM_SYNC_ONGOING);
				conn.getDos().flush();
			}

			// we have now finished reading and writing all the events of a sync. the remote node may not have
			// finished reading and processing all the events this node has sent. so we write a byte to tell the remote
			// node we have finished, and the reader will wait for it to send us the same byte.
			conn.getDos().writeByte(ByteConstants.COMM_SYNC_DONE);
			conn.getDos().flush();

			LOG.debug(SYNC_INFO.getMarker(), "{} sent COMM_SYNC_DONE", conn.getDescription());

			// (ignored)
			return null;
		};
	}

	public static Callable<Integer> phase3Read(
			final Connection conn,
			final Consumer<GossipEvent> eventHandler,
			final SyncMetrics syncMetrics,
			final CountDownLatch eventReadingDone) {
		return () -> {
			LOG.info(SYNC_INFO.getMarker(), "{} reading events start", conn.getDescription());
			int eventsRead = 0;
			try {
				final long startTime = System.nanoTime();
				while (true) {
					// readByte() will throw a timeout exception if the socket timeout is exceeded
					final byte next = conn.getDis().readByte();
					// if the peer continuously sends COMM_SYNC_ONGOING, or sends the data really slowly,
					// this timeout will be triggered
					checkPhase3Time(startTime);
					switch (next) {
						case ByteConstants.COMM_EVENT_NEXT -> {
							final GossipEvent gossipEvent = conn.getDis().readEventData();
							eventHandler.accept(gossipEvent);
							eventsRead++;
						}
						case ByteConstants.COMM_EVENT_DONE -> {
							syncMetrics.eventsReceived(startTime, eventsRead);
							LOG.info(SYNC_INFO.getMarker(), "{} reading events done, read {} events",
									conn.getDescription(), eventsRead);
							// we are done reading event, tell the writer thread to send a COMM_SYNC_DONE
							eventReadingDone.countDown();
						}
						// while we are waiting for the peer to tell us they are done, they might send COMM_SYNC_ONGOING
						// if they are still busy reading events
						case ByteConstants.COMM_SYNC_ONGOING ->
								// peer is still reading events, waiting for them to finish
								LOG.debug(SYNC_INFO.getMarker(), "{} received COMM_SYNC_ONGOING",
										conn.getDescription());
						case ByteConstants.COMM_SYNC_DONE -> {
							LOG.debug(SYNC_INFO.getMarker(), "{} received COMM_SYNC_DONE", conn.getDescription());
							return eventsRead;
						}
						default -> throw new SyncException(conn,
								String.format("while reading events, received unexpected byte %02x", next));
					}
				}
			} finally {
				// in case an exception gets thrown, unblock the writer thread
				eventReadingDone.countDown();
			}
		};
	}

	/**
	 * Checks if the phase 3 maximum time has been exceeded. If it has, it throws an exception.
	 *
	 * @param startTime
	 * 		the time at which phase 3 started
	 * @throws SyncTimeoutException
	 * 		thrown if the time is exceeded
	 */
	private static void checkPhase3Time(final long startTime) throws SyncTimeoutException {
		final long phase3Nanos = System.nanoTime() - startTime;
		if (phase3Nanos > PHASE3_MAX_NANOS) {
			throw new SyncTimeoutException(Duration.ofNanos(phase3Nanos), PHASE3_MAX_DURATION);
		}
	}
}
