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

package com.swirlds.platform;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.events.BaseEventHashedData;
import com.swirlds.common.events.BaseEventUnhashedData;
import com.swirlds.common.io.BadIOException;
import com.swirlds.platform.event.ValidateEventTask;
import com.swirlds.platform.sync.SyncData;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncLogging;
import com.swirlds.platform.sync.SyncOutputStream;
import com.swirlds.platform.sync.SyncShadowGraphManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.SYNC;
import static com.swirlds.logging.LogMarker.SYNC_SGM;
import static com.swirlds.logging.LogMarker.SYNC_STEP_4;
import static com.swirlds.logging.LogMarker.TIME_MEASURE;

/**
 * A low-level stream interface type, providing to support to the gossip protocol
 * implemented in {@link NodeSynchronizerImpl}.
 */
abstract class AbstractNodeSynchronizer implements NodeSynchronizer {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private final Logger log;

	/** Did self (not other) initiate this sync (so caller, not listener)? */
	private final boolean caller;

	/** Number of member nodes in the network for this sync */
	private final long numberOfNodes;

	/** The connection instance that contains the I|O stream instances and node IDs */
	protected final SyncConnection conn;

	/** A prefix string for statements written to a log file */
	private final String logString;

	/** The shadow graph manager to use for this sync */
	private final SyncShadowGraphManager syncShadowGraphManager;

	/** keeps track of the number of events read */
	private final AtomicInteger eventsRead = new AtomicInteger(0);

	/** keeps track of the number of events written */
	private final AtomicInteger eventsWritten = new AtomicInteger(0);

	/** The data used by the communication protocol to select events for transmission */
	private final SyncData syncData = new SyncData();

	/** Defines and applies the number of extra bytes to write to control sync speed. */
	private final SyncThrottle throttle;

	/** Update timing stats. */
	private final Statistics stats;

	/** Consumer for generating events from gossip-neighbors. */
	private final Consumer<ValidateEventTask> addEvent;

	/** tracks how many bytes have been written */
	private final AtomicLong bytesWritten = new AtomicLong(0);

	/**
	 * Constructor
	 *
	 * @param conn
	 * 		The {@link SyncConnection} instance which this node synchronizer will use.
	 * @param caller
	 * 		true iff {@code this} is invoked from a caller thread
	 * 		false  iff {@code this} is invoked from a listener thread
	 * @param syncShadowGraphManager
	 * 		The {@link SyncShadowGraphManager} instance to use for this connection
	 * @param throttle
	 * 		The {@link SyncThrottle} instance to use for this connection
	 * @param stats
	 * 		The statistics accumulator which {@code this} will update
	 * @param addEvent
	 * 		A functor to use for enqueueing received event data, for ingestion by consensus hashgraph
	 * @param numberOfNodes
	 * 		The number of nodes in the network.
	 * @param log
	 * 		The Log4j logging object to use to record log entries emitted by this instance while gossiping.
	 * @param logString
	 * 		The logString which will identify log entries emitted by this instance while gossiping.
	 */
	protected AbstractNodeSynchronizer(
			final SyncConnection conn,
			final boolean caller,
			final SyncShadowGraphManager syncShadowGraphManager,
			final SyncThrottle throttle,
			final Statistics stats,
			final Consumer<ValidateEventTask> addEvent,
			final long numberOfNodes,
			final Logger log,
			final String logString) {
		this.numberOfNodes = numberOfNodes;
		this.caller = caller;
		this.conn = conn;
		this.logString = logString;
		this.syncShadowGraphManager = syncShadowGraphManager;
		this.throttle = throttle;
		this.stats = stats;
		this.addEvent = addEvent;
		this.log = log;
	}

	/**
	 * Is this synchronizer operating on a caller thread?
	 *
	 * @return true iff on a caller thread
	 */
	protected boolean isCaller() {
		return caller;
	}

	/**
	 * Is this synchronizer operating on a listener thread?
	 *
	 * @return true iff on a listener thread
	 */
	protected boolean isListener() {
		return !caller;
	}

	/**
	 * Get the number of nodes in the network at time of construction
	 *
	 * @return the number of nodes in the network at time of construction
	 */
	protected long getNumberOfNodes() {
		return numberOfNodes;
	}

	/**
	 * Get a reference to the shadow graph manager used by this gossip session
	 *
	 * @return a reference to the shadow graph manager used by this gossip session
	 */
	protected SyncShadowGraphManager getSyncShadowGraphManager() {
		return syncShadowGraphManager;
	}

	/**
	 * Get the string used to identify log entries emitted by {@code this}
	 *
	 * @return the string used to identify log entries emitted by {@code this}
	 */
	protected String getLogString() {
		return logString;
	}

	/**
	 * Get a reference to the {@link SyncData} instance used during the current gossip session
	 *
	 * @return a reference to the {@link SyncData} instance used during the current gossip session
	 */
	protected SyncData getSyncData() {
		return syncData;
	}

	/**
	 * Get the statistics accumulator used by {@code this}
	 *
	 * @return the statistics accumulator used by {@code this}
	 */
	protected Statistics getStats() {
		return stats;
	}

	/**
	 * Get the number of events read from the remote peer in the current gossip session at time of call
	 *
	 * @return the number of events read from the remote peer in the current gossip session at time of call
	 */
	protected AtomicInteger getEventsRead() {
		return eventsRead;
	}

	/**
	 * Get the number of events written to the remote peer in the current gossip session at time of call
	 *
	 * @return the number of events written to the remote peer in the current gossip session at time of call
	 */
	protected AtomicInteger getEventsWritten() {
		return eventsWritten;
	}

	/**
	 * Get the {@link SyncOutputStream} instance used by this synchronizer, assigned from the
	 * {@link SyncConnection} object passed to the constructor
	 *
	 * @return the {@link SyncOutputStream} instance used by this synchronizer, assigned from the
	 *        {@link SyncConnection} object passed to the constructor
	 */
	protected SyncOutputStream getOutputStream() {
		return conn.getDos();
	}

	/**
	 * Get the {@link SyncInputStream} instance used by this synchronizer, assigned from the
	 * {@link SyncConnection} object passed to the constructor
	 *
	 * @return the {@link SyncInputStream} instance used by this synchronizer, assigned from the
	 *        {@link SyncConnection} object passed to the constructor
	 */
	protected SyncInputStream getInputStream() {
		return conn.getDis();
	}

	/**
	 * Write to a data stream the events that self has that the other member does not.
	 * <p>
	 * If other needs an event that self has discarded, then increment SyncServer.discardedEventsRequested
	 * <p>
	 * If {@code slowDown} is true, then send extra bytes at the end to intentionally slow down, to allow those who
	 * have fallen behind to catch up. The number of bytes to write is the total written up to that point,
	 * times {@code Settings.throttle7extra}.
	 *
	 * @throws IOException
	 * 		iff the output stream instance throws
	 */
	protected void writeEvents() throws IOException {

		final String syncLogString = logString + " `writeEvents`: ";

		// (throttle may be null for testing)
		if (throttle != null) {
			throttle.initialize(conn);
		}

		// If peer node has not fallen behind, send events to peer.
		final List<EventImpl> diffEvents = syncShadowGraphManager.finishSendEventList(syncData, syncLogString);

		if (syncData.isTipAbsorptionActive()) {
			conn.getPlatform().getStats().updateTipAbsorptionOpsPerSec();
		}

		log.debug(SYNC_SGM.getMarker(), "{}{} events to send", syncLogString, diffEvents.size());

		for (final EventImpl event : diffEvents) {
			writeByte(SyncConstants.COMM_EVENT_NEXT);
			writeEventData(event);
		}

		log.debug(SYNC_SGM.getMarker(), "{}sent {} events", syncLogString, diffEvents.size());

		writeByte(SyncConstants.COMM_EVENT_DONE);

		writeThrottleBytes();

		writeFlush();

		// record history of events written per sync
		// (stats may be null for testing)
		if (stats != null) {
			stats.avgEventsPerSyncSent.recordValue(eventsWritten.get());
		}
	}

	/**
	 * @throws IOException
	 * 		iff the {@link SyncOutputStream} instance throws
	 */
	protected void writeFallenBehind() throws IOException {
		final String syncLogString = getLogString() + ": `writeFallenBehind `: ";

		conn.getSyncServer().discardedEventsRequested.incrementAndGet();
		writeByte(SyncConstants.COMM_EVENT_DISCARDED);
		writeFlush();
		log.debug(RECONNECT.getMarker(),
				"{}sent COMM_EVENT_DISCARDED to {}",
				() -> syncLogString,
				conn::getOtherId);
	}

	/**
	 * Flush the output stream
	 *
	 * @throws IOException
	 * 		iff the {@link SyncOutputStream} instance throws
	 */
	protected void writeFlush() throws IOException {
		getOutputStream().flush();
	}

	/**
	 * Write bytes to regulate connection frequency
	 *
	 * @throws IOException
	 * 		iff the {@link SyncOutputStream} instance throws
	 */
	private void writeThrottleBytes() throws IOException {
		// (throttle may be null for testing)
		if (throttle != null) {
			final int nThrottleBytes = throttle.apply(conn, eventsRead.get());

			// record number of throttle bytes written
			// (stats may be null for testing)
			if (stats != null) {
				stats.bytesPerSecondCatchupSent.update(nThrottleBytes);
			}
		}
	}

	/**
	 * Write a single byte into the output stream
	 *
	 * @param b
	 * 		the byte value to write
	 * @throws IOException
	 * 		iff the {@link SyncOutputStream} instance throws
	 */
	protected void writeByte(final byte b) throws IOException {
		log.debug(TIME_MEASURE.getMarker(), "start writeEvents,writeByte {}-{}", conn.getSelfId(), conn.getOtherId());

		getOutputStream().writeByte(b);

		log.debug(TIME_MEASURE.getMarker(), "end writeEvents,writeByte {}-{}", conn.getSelfId(), conn.getOtherId());
	}

	/**
	 * Write event data
	 *
	 * @param event
	 * 		the event
	 * @throws IOException
	 * 		iff the {@link SyncOutputStream} instance throws
	 */
	private void writeEventData(final EventImpl event) throws IOException {
		final String syncLogString = logString + " `writeEventData`: ";
		log.debug(TIME_MEASURE.getMarker(), "start writeEvents,writeEvent {}-{}", conn.getSelfId(), conn.getOtherId());

		getOutputStream().writeSerializable(event.getBaseEventHashedData(), false);
		getOutputStream().writeSerializable(event.getBaseEventUnhashedData(), false);

		eventsWritten.incrementAndGet();
		final int ntransactions = event.getTransactions() == null ? 0 : event.getTransactions().length;

		log.debug(SYNC_SGM.getMarker(), "{}sent event {}, num transaction = {}",
				() -> syncLogString,
				() -> SyncLogging.getSyncLogString(event),
				() -> ntransactions);

	}

	/**
	 * Read from the data stream all the event data that the other member has that self doesn't, and send each
	 * one to {@code addEvent}. Then {@code addEvent} will instantiate an {@code Event} object, add it to the
	 * hashgraph (updating consensus calculations), and add anything that just achieved consensus to the
	 * forCons and forCurr queues. Then this method will return.
	 * <p>
	 * This method assumes events are received in topological order, ancestors first.
	 *
	 * @throws IOException
	 * 		anything unexpected was received or the connection broke
	 */
	protected void readEvents() throws IOException {

		while (true) {
			final byte next = readByte();

			if (next == SyncConstants.COMM_EVENT_DONE) {
				readByteArray();
				break;
			} else if (next == SyncConstants.COMM_EVENT_NEXT) {
				final ValidateEventTask validateEventTask = readEventData();
				addEvent.accept(validateEventTask);
			} else {
				// Unconditionally throws IOException
				badByte(next);
			}
		}

		// record history of events read per sync
		// (stats may be null for testing)
		if (stats != null) {
			stats.avgEventsPerSyncRec.recordValue(eventsRead.get());
		}
	}

	protected void readFallenBehind() throws IOException {
		final byte next = readByte();
		if (next == SyncConstants.COMM_EVENT_DISCARDED) {
			log.debug(RECONNECT.getMarker(),
					"{}received COMM_EVENT_DISCARDED from {} ",
					() -> logString + " `readFallenBehind`: ",
					conn::getOtherId);
		} else {
			// Unconditionally throws IOException
			badByte(next);
		}
	}

	private void readByteArray() throws IOException {
		final String syncLogString = logString + ": `readByteArray` : ";

		// fix these log strings
		log.debug(TIME_MEASURE.getMarker(),
				"{} received COMM_EVENT_DONE, " +
						"about to read and discard any bytes for slowing the sync", syncLogString);

		// read and discard any bytes for slowing the sync
		getInputStream().readByteArray(Settings.throttle7maxBytes, true);

		log.debug(TIME_MEASURE.getMarker(),
				"{} finished discarding bytes, readEvents is done", syncLogString);
	}

	/**
	 * Read a single byte from the {@link SyncInputStream} used for this gossip session.
	 *
	 * @return the received byte
	 * @throws IOException
	 * 		iff the {@link SyncInputStream} throws
	 */
	protected byte readByte() throws IOException {
		final String syncLogString = logString + ": `readEvents`,`readByteArray` : ";

		log.debug(TIME_MEASURE.getMarker(),
				"{}start readEvents,readByte", syncLogString);

		final byte next = getInputStream().readByte();

		log.debug(TIME_MEASURE.getMarker(),
				"{}end readEvents,readByte", syncLogString);

		return next;
	}

	private ValidateEventTask readEventData() throws IOException {
		final String syncLogString = logString + " `readValidateEventTaskData`: ";
		log.debug(TIME_MEASURE.getMarker(),
				"{}start readEvents,readValidateEventTaskData", syncLogString);

		final BaseEventHashedData hashedData =
				getInputStream().readSerializable(false, BaseEventHashedData::new);
		final BaseEventUnhashedData unhashedData =
				getInputStream().readSerializable(false, BaseEventUnhashedData::new);

		final ValidateEventTask validateEventTask = new ValidateEventTask(hashedData, unhashedData);
		log.debug(SYNC_STEP_4.getMarker(),
				"{}created ValidateEventTask {}",
				() -> syncLogString,
				() -> EventStrings.toShortString(validateEventTask));

		eventsRead.incrementAndGet();
		log.debug(TIME_MEASURE.getMarker(),
				"{}end readEvents,readValidateEventTaskData", syncLogString);

		return validateEventTask;
	}

	private static void badByte(final byte next) throws IOException {
		throw new IOException(
				"during sync, received unexpected byte " + next);
	}


	/**
	 * Read the other node's tip hashes
	 *
	 * @throws IOException
	 * 		is a stream exception occurs
	 */
	protected void readTipHashes() throws IOException {
		final String syncLogString = logString + " : `readTipHashes`: ";

		final List<com.swirlds.common.crypto.Hash> receivedTipHashes = getInputStream().readSerializableList(
				(int) (numberOfNodes * 1000),
				false,
				Hash::new);

		syncData.getReceivedTipHashes().addAll(receivedTipHashes);

		syncShadowGraphManager.setReceivedTipHashes(syncData, syncLogString);

		log.debug(SYNC_SGM.getMarker(),
				"{}finished, received {} tip hashes",
				syncLogString,
				receivedTipHashes.size());
	}

	/**
	 * Write to the {@link SyncOutputStream} the hashes of the tip events from this node's shadow graph
	 *
	 * @throws IOException
	 * 		iff the {@link SyncOutputStream} throws
	 */
	protected void writeTipHashes() throws IOException {
		final List<Hash> tipHashes = syncData.getSendingTipHashes();
		getOutputStream().writeSerializableList(tipHashes, false, true);
	}


	/**
	 * Read the tip booleans sent by the remote node to this node. Determined by this node's tip hashes and the remote
	 * node's tip hashes.
	 *
	 * @throws IOException
	 * 		iff the {@link SyncInputStream} throws
	 */
	protected void readTipBooleans() throws IOException {
		final String syncLogString = getLogString() + ": `readTipBooleans`: ";

		final int ntips = Math.max(
				syncData.getReceivedTipHashes().size(),
				getSyncShadowGraphManager().getNumTips());

		final List<Boolean> tipBooleans = getInputStream().readBooleanList(ntips);

		getSyncShadowGraphManager().setReceivedTipBooleans(getSyncData(), tipBooleans, syncLogString);

		log.debug(SYNC_SGM.getMarker(),
				"{}set {} tip booleans in shadow graph",
				syncLogString,
				tipBooleans.size());

		log.debug(SYNC_SGM.getMarker(),
				"{}working tips is now",
				syncLogString);
	}

	/**
	 * Write the tip booleans to the remote node. Determined by this node's tip hashes and the remote node's tip hashes.
	 *
	 * @throws IOException
	 * 		iff the {@link SyncOutputStream} throws
	 */
	protected void writeTipBooleans() throws IOException {
		final String syncLogString = getLogString() + ": `writeTipBooleans`: ";

		final List<Boolean> tipBooleans = getSyncShadowGraphManager().getSendTipBooleans(getSyncData());

		getOutputStream().writeBooleanList(tipBooleans);

		log.debug(SYNC_SGM.getMarker(),
				"{}finished, sent {} tip booleans",
				syncLogString,
				tipBooleans.size());

		writeFlush();
	}

	/**
	 * Write a {@code  SyncConstants.COMM_SYNC_DONE} byte the remote node, then read a byte from the remote node.
	 *
	 * @throws IOException
	 * 		iff: (a) the {@link SyncOutputStream} or {@link SyncInputStream} throws, or (b) if the received by is not
	 *        {@code SyncConstants.COMM_SYNC_DONE}
	 */
	protected void writeAndReadSyncDone() throws IOException {
		// we have now finished reading and writing all the events of a sync. the remote node may not have
		// finished reading and processing all the events this node has sent. so we write a byte to tell the remote
		// node we have finished, and we wait for it to send us the same byte.
		writeByte(SyncConstants.COMM_SYNC_DONE);

		log.debug(SYNC.getMarker(),
				"syncStep5: {} sent COMM_SYNC_DONE to {}",
				conn.getSelfId(), conn.getOtherId());

		writeFlush();

		bytesWritten.addAndGet(Byte.BYTES);

		final byte done = readByte();

		if (done != SyncConstants.COMM_SYNC_DONE) {
			throw new BadIOException(
					"received " + done + " instead of COMM_SYNC_DONE");
		}

		log.debug(SYNC.getMarker(),
				"syncStep5: {} received COMM_SYNC_DONE from {}",
				conn.getSelfId(), conn.getOtherId());
	}

	/**
	 * Initialize {@code this} instance for a gossip session.
	 *
	 * @param canAcceptSync
	 * 		If this function is executed from a {@link SyncListener} thread, this value is true iff a
	 * 		listener thread can accept a sync request from a caller.
	 *
	 * 		If executed from a {@link SyncCaller} thread, this value is ignored.
	 * @param caller
	 * 		true iff {@code this} is invoked from a caller thread
	 * 		false  iff {@code this} is invoked from a listener thread
	 * @throws IOException
	 * 		iff:
	 * 		<p>	(a) the {@link SyncOutputStream} or {@link SyncInputStream} throws, or
	 * 		<p>	(b) {@code this.conn} is {@code null}, or
	 * 		<p> (c) {@code this.conn}'s socket instance is {@code null}, or
	 * 		<p> (d) either the input stream or output stream objects are {@code null}
	 */
	protected void init(final boolean canAcceptSync, final boolean caller) throws IOException {

		if (conn == null || !conn.connected()) {
			throw new BadIOException("not a valid connection ");
		}

		// conn.connected() was true above, but maybe it became false right after the check so dis or dos
		// is null.
		if (conn.getSocket() == null || conn.getDis() == null || conn.getDos() == null) {
			throw new BadIOException("not a valid connection ");
		}

		/* track the number of bytes written and read during a sync */
		getInputStream().getSyncByteCounter().resetCount();
		getOutputStream().getSyncByteCounter().resetCount();

		conn.getSocket().setSoTimeout(Settings.timeoutSyncClientSocket);

		final boolean listener = !caller;
		log.debug(SYNC_SGM.getMarker(), "{} : `sync`: start sync, {}",
				() -> logString,
				() -> {
					if (listener && canAcceptSync) {
						return "listener can accept";
					} else if (listener) {
						return "listener can not accept";
					} else {
						return "caller";
					}
				});
	}

}
