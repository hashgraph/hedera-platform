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

import com.swirlds.common.io.BadIOException;
import com.swirlds.common.threading.ParallelExecutionException;
import com.swirlds.common.threading.ThreadConfiguration;
import com.swirlds.platform.event.ValidateEventTask;
import com.swirlds.platform.sync.SyncFallenBehind;
import com.swirlds.platform.sync.SyncLogging;
import com.swirlds.platform.sync.SyncShadowGraphManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.SyncFailedException;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.SYNC;
import static com.swirlds.logging.LogMarker.SYNC_DONE;
import static com.swirlds.logging.LogMarker.SYNC_GENERATIONS;
import static com.swirlds.logging.LogMarker.SYNC_SGM;
import static com.swirlds.logging.LogMarker.SYNC_STEP_1;
import static com.swirlds.logging.LogMarker.SYNC_STEP_2;
import static com.swirlds.logging.LogMarker.SYNC_STEP_3;
import static com.swirlds.logging.LogMarker.SYNC_STEP_4;
import static com.swirlds.logging.LogMarker.SYNC_STEP_5;

/* **********************************************************************************************
 *
 * This is the algorithm implemented by NodeSynchronizer, for both the caller and the listener. The
 * individual steps are listed below. A side-by-side double box executes both its halves in parallel,
 * and waits for both of those threads to finish before executing the next step.
 *
 * Just before calling this method, the listener will have read the sync request, and decided whether to
 * agree to sync (ACK) or refuse (NACK).
 *
 * The "send Events" step (step 4) sends all event data that the other member does not yet know,
 * possibly followed by a small number of extra bytes. The extra bytes are sent only if neither this
 * node nor the other node are not falling behind. This is defined by min/max generations for the
 * two nodes, relative to each other.
 *
 * The number of extra bytes to send equals the total number of bytes sent by the end of the exchange
 * (end of step 4), times the value Settings.throttle7extra.
 *
 * If either node has fallen behind the other, then no extra bytes are sent, so the sync will happen faster.
 * This helps a member who is falling behind to catch up.
 *
 * <pre>
 *
 * CALLER:                                         LISTENER:
 *
 * STEP 1:                                         STEP 1:
 *    write the COMM_SYNC_REQUEST byte             <no work>
 *
 * STEP 2:                                         STEP 2:
 * #=====================#=====================#   #=====================#=====================#
 * #  send tip hashes    #  read ACK/NACK      #   #  send ACK/NACK      #  read tip hashes    #
 * #  send tip gens      #                     #   #                     #  read tip gens      #
 * #  flush              #  if ACK             #   #  if ACK             #                     #
 * #                     #    read counts      #   #    send counts      #                     #
 * #                     #    read tip hashes  #   #    send tip hashes  #                     #
 * #                     #                     #   #    flush            #                     #
 * #=====================#=====================#   #=====================#=====================#
 *
 * #=====================#=====================#   #=====================#=====================#
 * #  send tip booleans  #  read tip booleans  #   #  send tip booleans  #  read tip booleans  #
 * #  flush              #                     #   #  flush              #                     #
 * #                     #                     #   #                     #                     #
 * #=====================#=====================#   #=====================#=====================#
 *
 *
 * STEP 3:                                         STEP 3:
 * #===========================================#   #===========================================#
 * #  If both caller/listener not falling      #   #  If both caller/listener not falling      #
 * #  behind, enable sending extra random      #   #  behind, enable sending extra random      #
 * #  bytes to slow down.                      #   #  bytes to slow down.                      #
 * #                                           #   #                                           #
 * #===========================================#   #===========================================#
 *
 *
 * STEP 4:                                         STEP 4:
 * #=====================#=====================#   #=====================#=====================#
 * #  if ACK             #  if ACK             #   #  if ACK             #  if ACK             #
 * #    send Events      #    read peer Events #   #    send Events      #    read peer Events #
 * #    send extra bytes #    insert peer      #   #    send extra bytes #    insert peer      #
 * #    flush            #      Events         #   #    flush            #      Events         #
 * #                     #                     #   #                     #                     #
 * #=====================#=====================#   #=====================#=====================#
 *
 *
 * STEP 5:                                         STEP 5:
 * #===========================================#   #===========================================#
 * #  if ACK                                   #   #  if ACK                                   #
 * #    Send DONE byte (optional)              #   #    Send DONE byte (optional)              #
 * #    Create a new Event                     #   #    Create a new Event                     #
 * #    Record sync statistics                 #   #    Record sync statistics                 #
 * #    Record caller statistics               #   #    Record listener statistics             #
 * #    Sleep (optional)                       #   #    Sleep (optional)                       #
 * #                                           #   #                                           #
 * #===========================================#   #===========================================#
 *
 * </pre>
 ***********************************************************************************************/


/**
 * A stateful type, which runs the gossip protocol over a {@code SyncConnection} instance.
 * This type supercedes the static I|O utilities defined in {@code SyncUtils}.
 */
class NodeSynchronizerImpl extends AbstractNodeSynchronizer {

	/**
	 * The static thread pool used by this class.
	 */
	private static final ExecutorService SYNC_THREAD_POOL = Executors
			.newCachedThreadPool(
					new ThreadConfiguration()
							.setThreadName("node-sync")
							.setComponent("node-sync")
							.buildFactory());


	/**
	 * use this for all logging, as controlled by the optional data/log4j2.xml file
	 */
	private static final Logger LOG = LogManager.getLogger();

	/**
	 * accumulates time points for each step in the execution of a single gossip session, used for
	 * stats reporting and performance analysis
	 */
	private final SyncTiming timing = new SyncTiming();

	/**
	 * Defines whether this node or the remote has fallen behind the other.
	 */
	private final SyncFallenBehind fallenBehind = new SyncFallenBehind();

	/**
	 * sleep in ms after each sync in SyncCaller. A public setter for this exists.
	 */
	private final long callerDelayAfterSync;

	/**
	 * A functor invoked to create  an event from event data received from peer nodes.
	 */
	private final Consumer<Long> createEvent;

	/**
	 * true iff the listener accepts the caller sync request.
	 * Used only when this instance is executed on a caller thread.
	 * Ignored by listener thread.
	 * Initialized in `syncStep2`
	 */
	private boolean syncAccepted;


	/**
	 * Production constructor
	 *
	 * @param conn
	 * 		the connection to sync through
	 * @param caller
	 * 		true if this computer is calling the other (initiating the sync). False if this computer
	 * 		is a listener.
	 */
	public NodeSynchronizerImpl(final SyncConnection conn, final boolean caller) throws SyncFailedException {
		super(
				conn,
				caller,
				getSyncShadowGraphManager(conn),
				new SyncThrottle(conn.getPlatform().getNumMembers()),
				conn.getPlatform().getStats(),
				(ValidateEventTask task) -> conn.getPlatform().getEventTaskCreator().addEvent(task),
				conn.getPlatform().getNumMembers(),
				LOG,
				SyncLogging.getSyncLogString(conn, caller));
		this.callerDelayAfterSync = conn.getPlatform().getSleepAfterSync();
		this.createEvent = (Long otherID) -> conn.getPlatform().getEventTaskCreator().createEvent(otherID);
	}

	/**
	 * @param conn
	 * 		the connection to sync through
	 * @param caller
	 * 		true if this computer is calling the other (initiating the sync). False if this computer
	 * 		is a listener.
	 * @param canAcceptSync
	 * 		If this function is executed from a {@link SyncListener} thread, this value is true iff a
	 * 		listener thread can accept a sync request from a caller.
	 *
	 * 		If executed from a {@link SyncCaller} thread, this value is ignored.
	 * @param reconnected
	 * 		true iff this call is the first call after a reconnect on this thread, used for logging
	 * @return true iff a sync was (a) accepted, and (b) completed, including exchange of event data
	 * @throws IOException
	 * 		iff:
	 * 		<p>	(a) an underlying stream instance throws an exception (timeouts and other stream errors)
	 * 		<p> (b) the gossip implementor itself throws an exception
	 */
	protected static boolean synchronize(
			final SyncConnection conn,
			final boolean caller,
			final boolean canAcceptSync,
			final boolean reconnected) throws IOException {

		final NodeSynchronizerImpl nodeSync = new NodeSynchronizerImpl(conn, caller);

		return nodeSync.synchronize(canAcceptSync, reconnected);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean synchronize(final boolean canAcceptSync, final boolean reconnected)
			throws IOException {

		init(canAcceptSync, isCaller());
		timing.start();

		// STEP 1: WRITE sync request (only for caller; listener READ already happened)
		syncStep1();
		timing.setTimePoint(1);

		// STEP 2: READ and WRITE the ACK/NACK, and tip hashes, tip booleans, and generation numbers
		syncStep2(canAcceptSync, reconnected);
		timing.setTimePoint(2);

		// If listener and can accept sync, then sync.
		// If caller and sync request was accepted, then sync.
		// Else, stop here.
		if ((isListener() && canAcceptSync) || (isCaller() && syncAccepted)) {

			// STEP 3: decide to slow down if neither self nor other is falling behind
			syncStep3();
			timing.setTimePoint(3);

			// STEP 4: READ and WRITE events the other doesn't know
			syncStep4();
			timing.setTimePoint(4);

			// STEP 5: sync now completed, without errors. So send COMM_SYNC_DONE if sendSyncDoneByte=true,
			// create an event, log various stats, maybe sleep
			syncStep5();
			timing.setTimePoint(5);

			// Update statistics accumulators and record lastSyncSpeed in platform.
			timing.finish(conn, getStats());

			LOG.debug(SYNC_SGM.getMarker(),
					"Sizes after `sync`, workingTips: {}, receivedTipHashes: {}, sendList: {}",
					getSyncData().getWorkingTips().size(),
					getSyncData().getReceivedTipHashes().size(),
					getSyncData().getSendList().size());

			return true;
		} else {
			LOG.debug(SYNC_SGM.getMarker(),
					"Sizes after `sync`, workingTips: {}, receivedTipHashes: {}, sendList: {}",
					getSyncData().getWorkingTips().size(),
					getSyncData().getReceivedTipHashes().size(),
					getSyncData().getSendList().size());

			return false;
		}
	}


	/**
	 * Read the other node's generation numbers from an input stream
	 *
	 * @throws IOException
	 * 		if a stream exception occurs
	 */
	public void readGenerations() throws IOException {

		final long otherMaxRoundGeneration = getInputStream().readLong();
		final long otherMinRoundGeneration = getInputStream().readLong();

		fallenBehind.setOtherGenerations(otherMinRoundGeneration, otherMaxRoundGeneration);

		final String syncLogString = getLogString() + " : `readGenerations`: ";

		LOG.debug(SYNC_GENERATIONS.getMarker(),
				"{}received generations: min = {}, max = {}",
				syncLogString,
				fallenBehind.getOtherMinRoundGeneration(),
				fallenBehind.getOtherMaxRoundGeneration());
	}


	/**
	 * Write this node's generation numbers to an output stream
	 *
	 * @throws IOException
	 * 		if a stream exception occurs
	 */
	protected void writeGenerations() throws IOException {

		fallenBehind.setSelfGenerations(conn.getPlatform().getConsensus());

		getOutputStream().writeLong(fallenBehind.getSelfMaxRoundGeneration());
		getOutputStream().writeLong(fallenBehind.getSelfMinRoundGeneration());

		final String syncLogString = getLogString() + " : `writeGenerations`: ";

		LOG.debug(SYNC_GENERATIONS.getMarker(),
				"{}wrote generations: min = {}, max = {}",
				syncLogString,
				fallenBehind.getSelfMinRoundGeneration(),
				fallenBehind.getSelfMaxRoundGeneration());
	}

	/**
	 * Sync step 1: WRITE sync request (only for caller; listener READ already happened)
	 *
	 * @throws IOException
	 * 		error during write
	 */
	private void syncStep1() throws IOException {
		if (isCaller()) {// if we are a caller requesting to sync with the listener

			LOG.debug(SYNC_STEP_1.getMarker(), "{} : about to send sync request byte", getLogString());

			// try to initiate a sync
			writeByte(SyncConstants.COMM_SYNC_REQUEST);

			LOG.debug(SYNC_STEP_1.getMarker(), "{} : sent sync request byte", getLogString());
		}
	}


	/**
	 * Sync step 2: Exchange tip hashes, tip booleans, and generation numbers
	 *
	 * @param canAcceptSync
	 * 		true iff this node is caller, or else this node is listener and the {@code SyncLister}
	 * 		instance which invoked {@code NodeSynchronizer.synchronize} is able to accept a sync request
	 * @param reconnected
	 * 		true iff the currently executing sync is a first sync after this node reconnects. Used
	 * 		for logging.
	 */
	private void syncStep2(final boolean canAcceptSync, final boolean reconnected) {

		try {
			doParallel(
					syncStep2aReadTipHashesAndGenerations(reconnected),
					syncStep2bWriteTipHashesAndGenerations(canAcceptSync),
					"syncStep2(tip hashes & gens)");
		} catch (ParallelExecutionException e) {
			LOG.debug(SYNC_STEP_2.getMarker(), "{}", getLogString(), e);
		}
		try {
			doParallel(
					syncStep2aReadTipBooleans(),
					syncStep2bWriteTipBooleans(),
					"syncStep2(tip booleans)");
		} catch (ParallelExecutionException e) {
			LOG.debug(SYNC_STEP_2.getMarker(), "{}", getLogString(), e);
		}

	}


	/**
	 * @param reconnected
	 * 		true iff this is the first sync after a reconnection
	 * @return a {@code Callable} to be executed in parallel with the {@code Callable} returned by
	 *        {@code syncStep2bWriteTipHashesAndGenerations}
	 */
	private Callable<Object> syncStep2aReadTipHashesAndGenerations(final boolean reconnected) {
		return () -> {
			final String syncLogString = getLogString() + " : `syncStep2aReadTipHashesAndGenerations`: ";

			LOG.debug(SYNC_STEP_2.getMarker(), "{}start", syncLogString);

			// Caller thread requested a sync, so now caller thread reads if its request was accepted.
			syncAccepted = false;
			if (isCaller()) {
				readSyncRequestResponse(reconnected);
			}

			// Listener thread always reads data. Caller thread reads if listener accepted sync request.
			if (isListener() || syncAccepted) {
				readGenerations();
				readTipHashes();
			}

			LOG.debug(SYNC_STEP_2.getMarker(), "{}finished", syncLogString);

			// (ignored)
			return null;
		};
	}


	/**
	 * @param canAcceptSync
	 * 		true iff a listener thread can accept a sync request from a caller. This value is ignored whenever
	 * 		this function is invoked on a caller thread
	 * @return a {@code Callable} to be executed in parallel with the {@code Callable} returned by
	 *        {@code syncStep2aReadTipHashesAndGenerations}
	 */
	private Callable<Object> syncStep2bWriteTipHashesAndGenerations(final boolean canAcceptSync) {
		return () -> {
			final String syncLogString = getLogString() + " : `syncStep2bWriteTipHashesAndGenerations`: ";

			LOG.debug(SYNC_STEP_2.getMarker(), "{}start", syncLogString);

			// Listener thread accepts or rejects caller thread's sync request
			if (isListener()) {
				writeSyncRequestResponse(canAcceptSync);
			}

			// Caller thread always writes data. Listener thread writes if it can accept a sync from caller.
			if (isCaller() || canAcceptSync) {
				writeGenerations();
				writeTipHashes();
			}

			LOG.debug(SYNC_STEP_2.getMarker(), "{}finished", syncLogString);

			writeFlush();

			// (ignored)
			return null;
		};
	}


	/**
	 * Write a response byte, in response to the caller requesting a
	 * connection. Executed only by a listener thread.
	 *
	 * @param canAcceptSync
	 * 		true iff the listener can accept a sync request
	 * @throws IOException
	 * 		iff the DataOutputStream instance throws
	 */
	private void writeSyncRequestResponse(final boolean canAcceptSync) throws IOException {

		getOutputStream().writeByte(canAcceptSync
				? SyncConstants.COMM_SYNC_ACK
				: SyncConstants.COMM_SYNC_NACK);
	}


	/**
	 * Get the result of a request to synchronize. This function is called only
	 * by a caller thread.
	 *
	 * @throws IOException
	 * 		if a byte other than SyncConstants.COMM_SYNC_ACK or SyncConstants.COMM_SYNC_NACK
	 * 		is received
	 */
	private void readSyncRequestResponse(final boolean reconnected) throws IOException {
		final String syncLogString = getLogString() + ": " + "`receiveSyncRequestResponse`: ";

		syncAccepted = false; // did listener accept caller's request to sync?
		final byte b = readByte();
		if (b == SyncConstants.COMM_SYNC_ACK) {
			syncAccepted = true;
			if (reconnected) {
				LOG.debug(RECONNECT.getMarker(), "{}received COMM_SYNC_ACK", syncLogString);
			}
		} else if (b == SyncConstants.COMM_SYNC_NACK) {
			if (reconnected) {
				LOG.debug(RECONNECT.getMarker(), "{}received COMM_SYNC_NACK", syncLogString);
			}
		} else if (b == SyncConstants.COMM_END_OF_STREAM) {
			// the time at which the sync request was sent, for debugging purposes
			final long timeSyncRequestSent = timing.getTimePoint(1);

			throw new BadIOException(
					syncLogString + "COMM_SYNC_REQUEST was sent " + ((System.nanoTime() - timeSyncRequestSent) / 1_000_000L)
							+ " ms ago, but COMM_END_OF_STREAM was received "
							+ " instead of the expected response of either COMM_SYNC_ACK or COMM_SYNC_NACK");
		} else {
			// the time at which the sync request was sent, for debugging purposes
			final long timeSyncRequestSent = timing.getTimePoint(1);

			throw new BadIOException(
					syncLogString + "COMM_SYNC_REQUEST was sent " + ((System.nanoTime() - timeSyncRequestSent) / 1_000_000L)
							+ " ms ago, but reply was " + b
							+ " instead of COMM_SYNC_ACK or COMM_SYNC_NACK");
		}

		if (syncAccepted) {
			if (reconnected) {
				LOG.debug(RECONNECT.getMarker(), "{}request accepted", syncLogString);
			}
		} else {
			if (reconnected) {
				LOG.debug(RECONNECT.getMarker(), "{}request refused", syncLogString);
			}
		}
	}

	/**
	 * Sync step 2 thread A: read the ACK/NACK (for caller only) and counts (in parallel with 2-B)
	 *
	 * @return the Callable to run
	 */
	private Callable<Object> syncStep2aReadTipBooleans() {
		return () -> {
			final String syncLogString = getLogString() + ": `syncStep2aReadTipBooleans`: ";

			LOG.debug(SYNC_STEP_2.getMarker(), "{}reading tip booleans", syncLogString);

			readTipBooleans();

			LOG.debug(SYNC_STEP_2.getMarker(), "{}reading tip booleans: finished", syncLogString);

			// (ignored)
			return null;
		};
	}


	/**
	 * Sync step 2 thread B: send the ACK/NACK (listener only) and counts (in parallel with 2-A)
	 *
	 * @return the Callable to run
	 */
	private Callable<Object> syncStep2bWriteTipBooleans() {
		return () -> {
			final String syncLogString = getLogString() + ": `syncStep2bWriteTipBooleans`: ";

			LOG.debug(SYNC_STEP_2.getMarker(), "{}writing tip booleans", syncLogString);

			writeTipBooleans();

			LOG.debug(SYNC_STEP_2.getMarker(), "{}writing tip booleans: finished", syncLogString);

			// (ignored)
			return null;
		};
	}


	/**
	 * Sync step 3: Determine whether the current sync will send extra random bytes
	 * to slow down. Also determine whether either node in this communicating pair has
	 * fallen behind.
	 */
	private void syncStep3() {
		fallenBehind.detect();

		final String syncLogString = getLogString() + ": `syncStep3`: ";

		if (fallenBehind.selfFallenBehind()) {
			LOG.debug(RECONNECT.getMarker(),
					"{}node {} has fallen behind: self: min, max = {}, {}; other: min, max = {}, {}",
					() -> syncLogString,
					conn::getSelfId,
					fallenBehind::getSelfMinRoundGeneration,
					fallenBehind::getSelfMaxRoundGeneration,
					fallenBehind::getOtherMinRoundGeneration,
					fallenBehind::getOtherMaxRoundGeneration);
		}
		if (fallenBehind.otherFallenBehind()) {
			LOG.debug(RECONNECT.getMarker(),
					"{}node {} has fallen behind: self: min, max = {}, {}; other: min, max = {}, {}",
					() -> syncLogString,
					conn::getOtherId,
					fallenBehind::getSelfMinRoundGeneration,
					fallenBehind::getSelfMaxRoundGeneration,
					fallenBehind::getOtherMinRoundGeneration,
					fallenBehind::getOtherMaxRoundGeneration);
		}

		final boolean slowDown = !fallenBehind.detected();

		getStats().fracSyncSlowed.recordValue(slowDown ? 1 : 0);

		LOG.debug(SYNC_STEP_3.getMarker(),
				"{}slowDown = {}",
				syncLogString, slowDown);
	}


	/**
	 * Exchange selected events
	 */
	private void syncStep4() {
		try {
			doParallel(
					// THREAD A: READ the events, and create a new ValidateEventTask
					syncStep4aReadEvents(),
					// THREAD B: WRITE the events
					syncStep4bWriteEvents(),
					"syncStep4");
		} catch (ParallelExecutionException e) {
			LOG.debug(SYNC_STEP_4.getMarker(), "{}", getLogString(), e);
		}
	}

	/**
	 * Sync step 4 thread A: READ the events
	 *
	 * @return the {@code Callable} to run
	 */
	private Callable<Object> syncStep4aReadEvents() {
		return () -> {
			final String syncLogString = getLogString() + ": `syncStep4aReadEvents` : ";

			LOG.debug(SYNC_STEP_4.getMarker(), "{}reading events", syncLogString);

			// If either node in this gossip session has fallen behind the other, then there is nothing to read from the
			// peer.
			if (fallenBehind.detected()) {
				readFallenBehind();
			} else {
				readEvents();

				LOG.debug(SYNC_STEP_4.getMarker(), "{}reading events finished, read {} events",
						syncLogString, getEventsRead().get());
			}

			// (ignored)
			return null;
		};
	}


	/**
	 * Sync step 4 thread B: WRITE the events
	 *
	 * @return the {@code Callable} to run
	 */
	private Callable<Object> syncStep4bWriteEvents() {
		return () -> {
			final String syncLogString = getLogString() + ": `syncStep4bWriteEvents `: ";

			LOG.debug(SYNC_STEP_4.getMarker(), "{}writing events", syncLogString);

			// If either node in this gossip session has fallen behind the other, then there is nothing to write to the
			// peer.
			if (fallenBehind.detected()) {
				writeFallenBehind();
			} else {
				writeEvents();

				LOG.debug(SYNC_STEP_4.getMarker(), "{}writing events finished, wrote {} events",
						syncLogString, getEventsWritten().get());
			}

			// (ignored)
			return null;
		};
	}


	/**
	 * Sync step 5: sync now completed, without errors. So send COMM_SYNC_DONE if sendSyncDoneByte=true, create an
	 * event,
	 * log various stats, maybe sleep
	 *
	 * @throws IOException
	 * 		if there is a comm error
	 */
	private void syncStep5() throws IOException {
		final String syncLogString = getLogString() + " : `syncStep5` : ";
		LOG.debug(SYNC_STEP_5.getMarker(), "{}finishing sync", syncLogString);

		if (Settings.sendSyncDoneByte) {
			// we have now finished reading and writing all the events of a sync. the remote node may not have
			// finished reading and processing all the events this node has sent. so we write a byte to tell the remote
			// node we have finished, and we wait for it to send us the same byte.
			writeAndReadSyncDone();
		}

		if (shouldCreateEvent()) {
			createEvents();
		}

		if (isCaller()) {
			LOG.debug(SYNC_DONE.getMarker(),
					"|||************** {} called {}", conn.getSelfId(), conn.getOtherId());
			LOG.debug(SYNC.getMarker(),
					"{} (caller) done syncing with {}", conn.getSelfId(), conn.getOtherId());
			getStats().callSyncsPerSecond.cycle();
			try {
				Thread.sleep(callerDelayAfterSync);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		} else {
			LOG.debug(SYNC_DONE.getMarker(),
					"|||############## {} listened to {}", conn.getSelfId(), conn.getOtherId());
			LOG.debug(SYNC.getMarker(),
					"{} (listener) done syncing with {}", conn.getSelfId(), conn.getOtherId());
			getStats().recSyncsPerSecond.cycle();
		}

		if (fallenBehind.selfFallenBehind()) {
			reportSelfHasFallenBehind();
		}

		LOG.debug(SYNC_STEP_5.getMarker(), "{}finishing sync: finished", syncLogString);
	}

	/**
	 * Determine whether this node should create an event when the current sync is complete.
	 *
	 * @return true iff an event should be created after events have been exchanged
	 */
	private boolean shouldCreateEvent() {
		final boolean hasOneNodeFallenBehind = fallenBehind.detected();

		final boolean shouldCreateEvent = conn.getSyncManager().shouldCreateEvent(
				conn.getOtherId(),
				hasOneNodeFallenBehind,
				getEventsRead().get(),
				getEventsWritten().get());

		getStats().shouldCreateEvent.recordValue(shouldCreateEvent ? 1 : 0);

		return shouldCreateEvent;
	}

	/**
	 * Notify the sync manager that the remote node reports that this node has fallen behind
	 */
	private void reportSelfHasFallenBehind() {
		conn.getSyncManager().reportFallenBehind(conn.getOtherId());
	}

	/**
	 * Request create an event for this node, with other-parent as the remote node for this sync.
	 * <p>
	 * If enabled ({@code Settings.randomEventProbability > 0}), enqueue creation of another event with
	 * a randomly-chosen other-parent.
	 * <p>
	 * If enabled ({@code Settings.rescueChildlessProbability > 0}), enqueue creation a child event
	 * for randomly selected other-nodes in this node's hashgraph.
	 */
	private void createEvents() {

		createEvent.accept(conn.getOtherId().getId());

		LOG.debug(SYNC.getMarker(), "{} created event for sync otherId:{}", conn.getSelfId(), conn.getOtherId());

		// ThreadLocalRandom used to avoid locking issues
		final Random random = ThreadLocalRandom.current();
		// maybe create an event with a random other parent
		if (Settings.randomEventProbability > 0 &&
				random.nextInt(Settings.randomEventProbability) == 0) {
			final long randomOtherId = random.nextInt((int) getNumberOfNodes());
			// we don't want to create an event with selfId==otherId
			if (!conn.getSelfId().equalsMain(randomOtherId)) {
				createEvent.accept(randomOtherId);
				LOG.debug(SYNC.getMarker(), "{} created random event otherId:{}", conn.getSelfId(), randomOtherId);
			}
		}

		conn.getPlatform().getEventTaskCreator().rescueChildlessEvents();
	}


	/**
	 * getSyncShadowGraphManager
	 *
	 * @param conn
	 * 		the connection instance over which to gossip/synchronize
	 * @return a (reference to) this node's shadow graph
	 */
	private static synchronized SyncShadowGraphManager getSyncShadowGraphManager(
			final SyncConnection conn) {

		final AbstractPlatform platform = conn.getPlatform();

		return platform.getSyncShadowGraphManager();
	}


	/**
	 * Run two tasks in parallel, the second one in the current thread, and the first in a thread from the
	 * syncThreadPool. This method returns only after both have finished.
	 * <p>
	 * If either throws an exception, the first one to throw an exception has it caught here,
	 * wrapped in a {@link ParallelExecutionException}, and re-thrown, while the other one's exception is ignored.
	 * <p>
	 * This is intended to be used in the comm system for syncing, so if anything goes wrong, we will
	 * log here the exception type reported.
	 *
	 * A {@link ParallelExecutionException} wraps either of an {@code Exception} (if task2 threw),
	 * or else an exception type thrown by task1. It is a wrapper around the actual exception, so logging
	 * can still see what the real exception was.
	 *
	 * @param task1
	 * 		a task to execute in parallel
	 * @param task2
	 * 		a task to execute in parallel
	 * @throws ParallelExecutionException
	 * 		iff either of the invoked tasks throws
	 */
	private static void doParallel(
			Callable<Object> task1,
			Callable<Object> task2,
			String callerName) throws ParallelExecutionException {

		try {
			Future<Object> future1 = SYNC_THREAD_POOL.submit(task1);
			task2.call();
			future1.get();

			// task1 exception
		} catch (ExecutionException | InterruptedException | CancellationException exception) {

			if (exception.getCause() != null) {
				throw new ParallelExecutionException(exception.getCause(), callerName, 1);
			} else {
				throw new ParallelExecutionException(exception, callerName, 1);
			}

			// task2 exception
		} catch (Exception exception) {

			if (exception.getCause() != null) {
				throw new ParallelExecutionException(exception.getCause(), callerName, 2);
			} else {
				throw new ParallelExecutionException(exception, callerName, 2);
			}
		}
	}

}
