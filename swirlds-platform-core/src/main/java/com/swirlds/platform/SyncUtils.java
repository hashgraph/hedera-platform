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
package com.swirlds.platform;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.NodeId;
import com.swirlds.common.Transaction;
import com.swirlds.common.events.BaseEventHashedData;
import com.swirlds.common.events.BaseEventUnhashedData;
import com.swirlds.common.io.BadIOException;
import com.swirlds.platform.internal.ArrayLimitExceededException;
import com.swirlds.platform.internal.PlatformThreadFactory;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.SyncFailedException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.STATE_SIG_DIST;
import static com.swirlds.logging.LogMarker.SYNC;
import static com.swirlds.logging.LogMarker.SYNC_DONE;
import static com.swirlds.logging.LogMarker.SYNC_SGM;
import static com.swirlds.logging.LogMarker.SYNC_START;
import static com.swirlds.logging.LogMarker.TIME_MEASURE;

/**
 * Static IO utilities that are useful during syncs.
 */
public class SyncUtils {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	private static final ExecutorService syncThreadPool = Executors
			.newCachedThreadPool(new PlatformThreadFactory("sync_util_"));

	/**
	 * Run two tasks in parallel, the second one in the current thread, and the first in a thread from the
	 * syncThreadPool. This method returns the results from the first task. It returns only after both have
	 * finished. If either throws an exception, the first one to throw an exception has it caught here,
	 * wrapped in an IOException, and re-thrown, while the other one's exception is ignored.
	 * <p>
	 * This is intended to be used in the comm system for syncing, so if anything goes wrong, we will
	 * consider it an IOException. But it is wrapped around the actual exception, so logging etc can still
	 * see what the real exception was. The real exception will, itself, have been wrapped in an
	 * ExecutionException, so the real one ends up being wrapped in two layers.
	 *
	 * @param task1
	 * 		one task to execute in parallel, whose result is returned
	 * @param task2
	 * 		the other task, whose result is not returned
	 * @return whatever Object task1 returned. (task2's return value is ignored).
	 * @throws Exception
	 */

	private static Object doParallel(Callable<Object> task1,
			Callable<Object> task2) throws Exception {
		try {
			Future<Object> future1 = syncThreadPool.submit(task1);
			task2.call();
			return future1.get();
		} catch (ExecutionException e) {
			// Because ExecutionException does not tell us much, we want to throw the exception that caused the
			// ExecutionException, which will usually be an IOException
			if (e.getCause() != null && e.getCause() instanceof Exception) {
				throw (Exception) e.getCause();
			} else {
				throw e;
			}
		}
	}


	/** read an Instant from a data stream and increment byteCount[0] by the number of bytes */
	public static Instant readInstant(DataInput dis, int[] byteCount)
			throws IOException {
		Instant time = Instant.ofEpochSecond(//
				dis.readLong(), // from getEpochSecond()
				dis.readLong()); // from getNano()
		byteCount[0] += 2 * Long.BYTES;
		return time;
	}



	/** read a byte[] from a data stream and increment byteCount[0] by the number of bytes */
	public static byte[] readByteArray(DataInputStream dis, int[] byteCount, int maxArrayLength)
			throws IOException {
		int len = dis.readInt();
		checkArrayLength(len, maxArrayLength);
		return readByteArrayOfLength(dis, byteCount, len);
	}

	private static void checkArrayLength(int len, int maxArrayLength) {
		if (len > maxArrayLength) {
			throw new ArrayLimitExceededException(String.format(
					"Array length (%d) is larger than maxArrayLength (%d)", len, maxArrayLength
			));
		}
	}

	private static byte[] readByteArrayOfLength(DataInputStream dis, int[] byteCount, int len) throws IOException {
		int checksum = dis.readInt();
		if (len < 0 || checksum != (101 - len)) { // must be at wrong place in the stream
			throw new BadIOException(
					"SyncServer.readByteArray tried to create array of length "
							+ len + " with wrong checksum.");
		}
		byte[] data = new byte[len];
		dis.readFully(data);
		byteCount[0] += 2 * Integer.BYTES + len * Byte.BYTES;
		return data;
	}

	/**
	 * Read from the data stream all the events that the other member has that self doesn't, and send each
	 * one to Platform.recordEvent. Then recordEvent will instantiate an Event object, add it to the
	 * hashgraph (updating consensus calculations), and add anything that just achieved consensus to the
	 * forCons and forCurre queues. Then this method will return.
	 * <p>
	 * This method will modify otherCounts, by adding any deltas received while reading.
	 *
	 * @param dis
	 * 		the stream to read from
	 * @param platform
	 * 		the platform running this sync
	 * @param selfCounts
	 * 		how many events by each creator that self knows
	 * @param otherCounts
	 * 		how many events by each creator that the other member knows
	 * @param id
	 * 		my member ID
	 * @param otherId
	 * 		their member ID
	 * @param gotEventDiscarded
	 * 		a variable that should be set to true if we encounter a commEventDiscarded message
	 * @param eventsRead
	 * 		keeps track of the number of events read
	 * @throws IOException
	 * 		anything unexpected was received or the connection broke
	 */
	static void readUnknownEvents(SyncInputStream dis, AbstractPlatform platform,
			long[] selfCounts, AtomicLongArray otherCounts, NodeId id,
			NodeId otherId, AtomicBoolean gotEventDiscarded,
			AtomicInteger eventsRead) throws IOException {
		log.debug(SYNC.getMarker(),
				"{} starting readUnknownEvents from {}", id, otherId);
		while (true) {
			log.debug(TIME_MEASURE.getMarker(),
					"start readUnknownEvents,readByte {}-{}", id, otherId);
			byte next = dis.readByte();
			log.debug(TIME_MEASURE.getMarker(),
					"end readUnknownEvents,readByte {}-{}", id, otherId);
			if (next == SyncConstants.commEventDone) {
				log.debug(TIME_MEASURE.getMarker(),
						"{} received commEventDone from {}, " +
								"about to read and discard any bytes for slowing the sync", id, otherId);
				// read and discard any bytes for slowing the sync
				dis.readByteArray(Settings.throttle7maxBytes, true);
				log.debug(TIME_MEASURE.getMarker(),
						"{} finished discarding bytes from {}, " +
								"readUnknownEvents is done", id, otherId);
				break;
			} else if (next == SyncConstants.commEventNext) {
				log.debug(TIME_MEASURE.getMarker(),
						"start readUnknownEvents,readEvent {}-{}", id, otherId);

				BaseEventHashedData hashedData =
						dis.readSerializable(false, BaseEventHashedData::new);
				BaseEventUnhashedData unhashedData =
						dis.readSerializable(false, BaseEventUnhashedData::new);

				ValidateEventTask validateEventTask = new ValidateEventTask(hashedData, unhashedData);
				log.debug(SYNC_SGM.getMarker(), "{} <- {} `readUnknownEvents`: adding event with self-parent gen {} and self-parent hash {}",
						id, otherId, hashedData.getSelfParentGen(), hashedData.getSelfParentHash());
				platform.getHashgraph().addEvent(validateEventTask);

				eventsRead.incrementAndGet();
				log.debug(TIME_MEASURE.getMarker(),
						"end readUnknownEvents,readEvent {}-{}", id, otherId);
			} else if (next == SyncConstants.commEventDiscarded) {
				gotEventDiscarded.set(true);
				log.debug(RECONNECT.getMarker(),
						"{} received commEventDiscarded from {} ", id, otherId);
				break;
			} else {
				log.error(EXCEPTION.getMarker(),
						"{} received from {} the unexpected byte {}", id,
						otherId, next);
				throw new IOException(
						"during sync, received unexpected byte " + next);
			}
		}
		// record history of events read per sync
		platform.getStats().avgEventsPerSyncRec.recordValue(eventsRead.get());
		log.debug(SYNC.getMarker(),
				"{} finished readUnknownEvents from {}", id, otherId);
	}

	/*******************************************************************************************************
	 * This is the algorithm implemented in SyncUtils.Sync for both the caller and the listener. The
	 * algorithms are listed side by side. A double box executes both its halves in parallel, and waits for
	 * both threads to finish before continuing.
	 *
	 * Just before calling this method, the listener will have read the sync request, and decided whether to
	 * agree to sync (ACK) or refuse (NACK).
	 *
	 * The "send Events" step sends all events that the other member does not yet know, possibly followed by
	 * a small number of extra bytes. The extra bytes are sent only if the original count arrays indicated
	 * that both members would be sending fewer than numMmebers events each. If either or both will send
	 * more, then the extra bytes are skipped, so the sync will happen faster, and a member who is falling
	 * behind can catch up. The number of extra bytes equals the number of bytes sent in the sync up to that
	 * point, times Settings.throttle7extra.
	 *
	 * <pre>
	 *
	 * CALLER:                                        LISTENER:
	 *
	 * STEP 1:                                        STEP 1:
	 *    write the commSyncRequest byte
	 *
	 * STEP 2:                                        STEP 2:
	 * #==================#=======================#   #===================#=======================#
	 * #   send counts    #   read ACK/NACK       #   #   read counts     #   send ACK/NACK       #
	 * #   flush          #   if ACK              #   #                   #   if ACK              #
	 * #                  #     read counts       #   #                   #     send counts       #
	 * #                  #                       #   #                   #   flush               #
	 * #==================#=======================#   #===================#=======================#
	 *
	 * STEP 3:                                        STEP 3:
	 * if both caller/listener not falling behind     if both caller/listener not falling behind
	 *     send extra random bytes to slow down           send extra random bytes to slow down
	 *
	 * STEP 4:                                        STEP 4:
	 * #==================#=======================#   #===================#=======================#
	 * #   if ACK         #   if ACK              #   #   if ACK          #   if ACK              #
	 * #     send Events  #     read Events       #   #     send Events   #     read Events       #
	 * #     flush        #     create new Event  #   #     flush         #     create new Event  #
	 * #==================#=======================#   #===================#=======================#
	 *
	 * </pre>
	 *********************************************************************************************/
	/**
	 * Perform a sync, where both parties call this method, and both do each step in parallel.
	 *
	 * @param conn
	 * 		the connection to sync through
	 * @param caller
	 * 		true if this computer is calling the other (initiating the sync). False if this computer
	 * 		is receiving the call from the other computer.
	 * @param canAcceptSync
	 * 		true if the receiver is willing to sync now, false if it is not. If caller==true, then
	 * 		canAcceptSync is ignored.
	 * @return true if the sync took place, false otherwise
	 * @throws Exception
	 * 		timeouts and other errors
	 */
	static boolean sync(SyncConnection conn, boolean caller, boolean canAcceptSync)
			throws Exception {
		if (conn == null || !conn.connected()) {
			throw new BadIOException("not a valid connection ");
		}

		final boolean slowDown;
		NodeId otherId = conn.getOtherId();
		NodeId selfId = conn.getSelfId();
		SyncInputStream dis = conn.getDis();
		SyncOutputStream dos = conn.getDos();
		AbstractPlatform platform = conn.getPlatform();
		String threadName = Thread.currentThread().getName();
		/** last sequence number for known for each member */
		long[] myCounts = platform.getHashgraph().getLastSeqByCreator();
		/**
		 * Counts of known events created by each member, as known to the member we are syncing with. During
		 * the sync, they may update these counts, so self can skip some of the events that would have been
		 * sent.
		 */
		AtomicLongArray otherCounts;
		/** time when a sync starts (used to update stats) */
		long t0 = System.nanoTime();
		/** time at the end of each step (used to update stats) */
		long t1, t2, t3, t4, t5;
		/* track the number of bytes written and read during a sync */
		dis.getSyncByteCounter().resetCount();
		dos.getSyncByteCounter().resetCount();
		AtomicLong bytesWritten = new AtomicLong(0);

		// conn.connected() was true above, but maybe it became false right after the check so dis or dos
		// is null.
		if (dis == null || dos == null) {
			throw new BadIOException("not a valid connection ");
		}

		conn.getSocket().setSoTimeout(Settings.timeoutSyncClientSocket);

		log.debug(TIME_MEASURE.getMarker(), "start sync {}-{}", selfId,
				otherId);

		if (caller)
			log.debug(SYNC_SGM.getMarker(), "{} -> {} `sync`: caller, start sync", selfId, otherId);
		else
			log.debug(SYNC_SGM.getMarker(), "{} <- {} `sync`: receiver, start sync", selfId, otherId);

		////////// STEP 1: WRITE sync request (only for caller; listener READ already happened)

		syncStep1(caller, selfId, otherId, dos);
		t1 = System.nanoTime();
		final long timeSyncRequestSent = t1;

		SyncShadowGraphManager sgm = getSyncShadowGraphManager(platform, selfId, otherId, caller);

		////////// STEP 2: READ and WRITE the ACK/NACK and counts
		otherCounts = (AtomicLongArray) doParallel(
				syncStep2aReadTipHashesAndCounts(
						sgm, caller, selfId, otherId, dis, threadName, canAcceptSync,
						timeSyncRequestSent, platform.getNumMembers()),
				syncStep2bWriteTipHashesAndCounts(
						sgm, caller, canAcceptSync, dos, myCounts, threadName, selfId,
						otherId));

		doParallel(
				syncStep2aReadTipFlags(
						sgm, caller, selfId, otherId, dis, threadName, canAcceptSync,
						timeSyncRequestSent, platform.getNumMembers()),
				syncStep2bWriteTipFlags(
						sgm, caller, canAcceptSync, dos, myCounts, threadName, selfId,
						otherId));

		t2 = System.nanoTime();

		// now otherCounts is null if and only if a NACK was sent or received.

		////////// STEP 3: decide to slow down if neither self nor other is falling behind

		slowDown = syncStep3(otherCounts, myCounts, platform);
		t3 = System.nanoTime();

		////////// STEP 4: READ and WRITE Events the other doesn't know

		if (otherCounts != null) { // if listener agreed to the sync, then sync (else return false)
			AtomicBoolean hasThisNodeFallenBehind = new AtomicBoolean(false);
			AtomicBoolean hasOtherNodeFallenBehind = new AtomicBoolean(false);
			AtomicInteger eventsRead = new AtomicInteger(0);
			AtomicInteger eventsWritten = new AtomicInteger(0);
			doParallel(
					// THREAD A: READ the events, and create a new event
					syncStep4aReadEvents(sgm, caller, selfId, otherId, dis, platform, myCounts,
							otherCounts, threadName, hasThisNodeFallenBehind, eventsRead),
					// THREAD B: WRITE the events
					syncStep4bWriteEvents(sgm, dos, platform, myCounts, otherCounts, selfId,
							otherId, slowDown, threadName, hasOtherNodeFallenBehind, eventsWritten));

			t4 = System.nanoTime();

			if (hasThisNodeFallenBehind.get()) {
				platform.getHashgraph().compensateForStaleEvent(otherId, otherCounts);
			}

			////// STEP 5: sync now completed, without errors. So send commSyncDone if sendSyncDoneByte=true, create an
			////// event, log various stats, maybe sleep
			syncStep5(platform, caller, selfId, otherId, dos, dis, bytesWritten,
					hasThisNodeFallenBehind.get() || hasOtherNodeFallenBehind.get(), eventsRead,
					eventsWritten);

			if (hasThisNodeFallenBehind.get()) {
				platform.getSyncManager().reportFallenBehind(otherId);
			}

			t5 = System.nanoTime();

			Statistics stats = platform.getStats();
			stats.avgSyncDuration1.recordValue((t1 - t0) / 1_000_000_000.0);
			stats.avgSyncDuration2.recordValue((t2 - t1) / 1_000_000_000.0);
			stats.avgSyncDuration3.recordValue((t3 - t2) / 1_000_000_000.0);
			stats.avgSyncDuration4.recordValue((t4 - t3) / 1_000_000_000.0);
			double syncDurationSec = (t5 - t0) / 1_000_000_000.0;
			stats.avgSyncDuration.recordValue(syncDurationSec);

			double speed = Math.max(dis.getSyncByteCounter().getCount(), dos.getSyncByteCounter().getCount())
					/ syncDurationSec;
			// set the speed of the sync currently measured
			platform.setLastSyncSpeed(otherId.getIdAsInt(), speed);
			platform.getStats().avgBytesPerSecSync.recordValue(speed);

			return true;
		} else {
			return false;
		}
	}

	/**
	 * Sync step 1: WRITE sync request (only for caller; listener READ already happened)
	 *
	 * @param caller
	 * 		did self (not other) initiate this sync (so caller, not listener)?
	 * @param selfId
	 * 		the member ID of self (the member running this Platform)
	 * @param otherId
	 * 		the member ID of the member that self is syncing with
	 * @param dos
	 * 		the DataOutputStream to write to during the sync
	 * @throws IOException
	 * 		error during write
	 */
	private static void syncStep1(boolean caller, NodeId selfId, NodeId otherId,
			DataOutputStream dos) throws IOException {
		if (caller) {// if we are a caller requesting to sync with the listener
			// try to initiate a sync
			log.debug(SYNC_START.getMarker(),
					"{} about to send sync byte to {}", selfId, otherId);
			dos.write(SyncConstants.commSyncRequest);
			log.debug(SYNC.getMarker(), "{} sent sync request to {}",
					selfId, otherId);
		}
	}

	/**
	 * Sync step 3: return boolean: are neither self nor other falling behind?
	 *
	 * @param myCounts
	 * 		number of events by each member known to self at the start of the sync
	 * @param otherCounts
	 * 		number of events by each member known to other at the start of the sync
	 * @param platform
	 * 		the Platform performing this sync
	 * @return should we send random bytes to slow down, to allow other members to catch up?
	 * @throws Exception
	 * 		if myCounts and otherCounts aren't the same length
	 */
	static boolean syncStep3(AtomicLongArray otherCounts,
			long[] myCounts, AbstractPlatform platform) throws Exception {
		final boolean slowDown;
		if (otherCounts == null) {
			slowDown = false; // not used, but must be set because it is final
		} else {
			// otherCounts exists, so myCounts should exist, and should be the same length
			if (myCounts == null || myCounts.length != otherCounts.length()) {
				String err = String.format(
						"ERROR myCounts.length = {} != {} = otherCounts.length", //
						myCounts == null ? "null" : myCounts.length, //
						otherCounts.length());
				log.error(EXCEPTION.getMarker(), err);
				throw new Exception(err);
			} else { // no errors, so prepare for the sync
				/** count of Events to send, used to throttle those not falling behind */
				long numToSend = 0;
				/** count of Events to receive, used to throttle those not falling behind */
				long numToRec = 0;
				/** number of members in the address book. Normally, send/rec this minus one events */
				long fallBehindThreshold = (long) (platform.getNumMembers()
						* Settings.throttle7threshold);

				for (int i = 0; i < myCounts.length; i++) {
					long d = myCounts[i] - otherCounts.get(i);
					if (d > 0) {
						numToSend += d;
					} else {
						numToRec += -d;
					}
				}

				slowDown = numToSend < fallBehindThreshold
						&& numToRec < fallBehindThreshold;
				platform.getStats().fracSyncSlowed.recordValue(slowDown ? 1 : 0);
			}
		}
		return slowDown;
	}

	/**
	 * Sync step 5: sync now completed, without errors. So send commSyncDone if sendSyncDoneByte=true, create an event,
	 * log various stats, maybe sleep
	 *
	 * @param platform
	 * 		the Platform performing this sync
	 * @param caller
	 * 		did self (not other) initiate this sync (so caller, not listener)?
	 * @param selfId
	 * 		the member ID of self (the member running this Platform)
	 * @param otherId
	 * 		the member ID of the member that self is syncing with
	 * @param dos
	 * 		the DataOutputStream to write to during the sync
	 * @param dis
	 * 		the DataInputStream to read from during this sync
	 * @param bytesWritten
	 * 		tracks how many bytes have been written
	 * @param hasOneNodeFallenBehind
	 * 		true if one of the nodes in this sync has fallen behind, false otherwise
	 * @param eventsRead
	 * 		keeps track of the number of events read
	 * @param eventsWritten
	 * 		keeps track of the number of events written
	 * @throws IOException
	 * 		if there is a comm error
	 */
	private static void syncStep5(AbstractPlatform platform, boolean caller,
			NodeId selfId, NodeId otherId, DataOutputStream dos, DataInputStream dis, AtomicLong bytesWritten,
			boolean hasOneNodeFallenBehind, AtomicInteger eventsRead, AtomicInteger eventsWritten) throws IOException {
		if (Settings.sendSyncDoneByte) {
			// we have now finished reading and writing all the events of a sync. the remote node may not have
			// finished reading and processing all the events this node has sent. so we write a byte to tell the remote
			// node we have finished, and we wait for it to send us the same byte.
			dos.writeByte(SyncConstants.commSyncDone);
			log.debug(SYNC.getMarker(),
					"syncStep5: {} sent commSyncDone to {}",
					selfId, otherId);
			dos.flush();
			bytesWritten.addAndGet(Byte.BYTES);
			byte done = dis.readByte();
			if (done != SyncConstants.commSyncDone) {
				throw new BadIOException(
						"received " + done + " instead of commSyncDone");
			}
			log.debug(SYNC.getMarker(),
					"syncStep5: {} received commSyncDone from {}",
					selfId, otherId);
		}

		boolean shouldCreateEvent = platform.getSyncManager().shouldCreateEvent(otherId, hasOneNodeFallenBehind,
				eventsRead.get(),
				eventsWritten.get());

		platform.getStats().shouldCreateEvent.recordValue(shouldCreateEvent ? 1 : 0);

		// new event will include hash of last known event by otherId
		// Create a new Event and add it to the hashgraph.
		if (shouldCreateEvent) {
			log.debug(SYNC.getMarker(), "{} created event for sync otherId:{}", selfId, otherId);
			platform.getHashgraph().createEvent(otherId.getId());


			// ThreadLocalRandom used to avoid locking issues
			Random random = ThreadLocalRandom.current();
			// maybe create an event with a random other parent
			if (Settings.randomEventProbability > 0 &&
					random.nextInt(Settings.randomEventProbability) == 0) {
				long randomOtherId = random.nextInt(platform.getHashgraph().getAddressBook().getSize());
				// we don't want to create an event with selfId==otherId
				if (!selfId.equalsMain(randomOtherId)) {
					platform.getHashgraph().createEvent(randomOtherId);
					log.debug(SYNC.getMarker(), "{} created random event otherId:{}", selfId, randomOtherId);
				}
			}

			platform.getHashgraph().rescueChildlessEvents();
		}

		log.debug(SYNC_START.getMarker(),
				"{} finished syncWithCreate while syncing with {}",
				selfId, otherId);

		if (caller) {
			log.debug(SYNC_DONE.getMarker(), "|||************** {} called {}",
					selfId, otherId);
			log.debug(SYNC.getMarker(),
					"{} (caller) done syncing with {}", selfId, otherId);
			platform.getStats().callSyncsPerSecond.cycle();
			try {
				Thread.sleep(platform.getSleepAfterSync());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		} else {
			log.debug(SYNC_DONE.getMarker(),
					"|||############## {} listened to {}", selfId, otherId);
			log.debug(SYNC.getMarker(),
					"{} (listener) done syncing with {}", selfId, otherId);
			platform.getStats().recSyncsPerSecond.cycle();
		}
	}






	/**
	 * Write to a data stream the events that self has that the other member does not.
	 * <p>
	 * If other needs an event that self has discarded, then increment SyncServer.oldEventsNeeded
	 * <p>
	 * If slowDown is true, then send extra bytes at the end to intentionally slow down, to allow those who
	 * have fallen behind to catch up. The number of bytes to write is the total written up to that point,
	 * times Settings.throttle7extra.
	 *
	 * @param dos
	 * 		the stream to write to
	 * @param platform
	 * 		the platform running this sync
	 * @param selfCounts
	 * 		how many events by each creator that self knows
	 * @param otherCounts
	 * 		how many events by each creator that they know
	 * @param selfId
	 * 		my member ID
	 * @param otherId
	 * 		their member ID
	 * @param slowDown
	 * 		should random bytes be sent to slow down so that others who fall behind can catch
	 * 		up?
	 * @param hasRemoteFallenBehind
	 * 		a variable that should be set to true if the remote node asks for an old discarded
	 * 		event
	 * @param eventsWritten
	 * 		keeps track of the number of events written
	 * @throws IOException
	 */
	static void writeUnknownEvents(SyncShadowGraphManager sgm, SyncOutputStream dos, AbstractPlatform platform,
			long[] selfCounts, AtomicLongArray otherCounts, NodeId selfId,
			NodeId otherId, boolean slowDown, AtomicBoolean hasRemoteFallenBehind,
			AtomicInteger eventsWritten)
			throws IOException {
		/* local copy of my counts for known events created by each member */
		long[] currMyCounts = selfCounts.clone();
		/* count of bytes written here, used to slow down when not falling behind */
		long syncByteCountAtStart = dos.getSyncByteCounter().getCount();
		List<EventImpl> diffEvents = sgm.getSendEventList(selfId, otherId);

		log.debug(SYNC_SGM.getMarker(), "{} -> {} `writeUnknownEvents`: {} events to send",
				selfId, otherId, diffEvents.size());
		for(EventImpl e : diffEvents)
			log.debug(SYNC_SGM.getMarker(), "{} -> {} `writeUnknownEvents`:   to be sent: {}",
					selfId, otherId, e.getBaseHash());

		// Determine whether the remote has fallen behind.

		for (int i = 0; i < currMyCounts.length; i++) {
			long c = 0;
			c = otherCounts.get(i);
			for (long j = c + 1; j <= currMyCounts[i]; j++) {
				EventImpl e = platform.getHashgraph().getEventByCreatorSeq(i, j);
				if(e == null) {  // invalid index returns a null, so don't waste memory adding it

					// The other member requested an event that self used to have, but doesn't now.
					// That means the event was already discarded it because it was too old.
					platform.getSyncServer().discardedEventsRequested
							.incrementAndGet();

					// in this case, we should not send any events, we should just notify the remote node that it
					// has fallen behind
					dos.writeByte(SyncConstants.commEventDiscarded);

					Supplier selfCountsSupplier = () -> (Arrays.toString(IntStream.range(0, currMyCounts.length)
							.mapToObj(n -> "<node" + n + ": " + currMyCounts[n] + ">").toArray()));
					Supplier otherCountsSupplier = () -> (Arrays.toString(IntStream.range(0, currMyCounts.length)
							.mapToObj(n -> "<node" + n + ": " + otherCounts.get(n) + ">").toArray()));
					log.debug(RECONNECT.getMarker(),
							"node{} sent commEventDiscarded to node{}. " +
									"The discarded event was created by node{}, sequence number: {}. " +
									"\n selfCounts: {} \n otherCounts: {}",
							selfId, otherId, i, j,
							selfCountsSupplier.get(), otherCountsSupplier.get());

					hasRemoteFallenBehind.set(true);
					return;
				}
			}
		}


		// sort the events by generation, sub-sorting randomly within a generation.
		Collections.shuffle(diffEvents);
		diffEvents.sort((EventImpl e1, EventImpl e2) -> (int) (e1.getGeneration() - e2.getGeneration()));

		for (EventImpl event : diffEvents) {
			log.debug(TIME_MEASURE.getMarker(), "start writeUnknownEvents,writeByte {}-{}", selfId, otherId);

			dos.writeByte(SyncConstants.commEventNext);

			log.debug(TIME_MEASURE.getMarker(), "end writeUnknownEvents,writeByte {}-{}", selfId, otherId);

			log.debug(TIME_MEASURE.getMarker(), "start writeUnknownEvents,writeEvent {}-{}", selfId, otherId);
			log.debug(SYNC_SGM.getMarker(), "{} -> {} `writeUnknownEvents`: begin send event", selfId, otherId);


			dos.writeSerializable(event.getBaseEventHashedData(), false);
			dos.writeSerializable(event.getBaseEventUnhashedData(), false);
			// Detect and log when a state signature is written.
			Transaction[] trans = event.getTransactions();

			// Leaving log.isEnabled(Level.DEBUG) to avoid executing the for loop when DEBUG is completely disabled
			if (trans != null && log.isEnabled(Level.DEBUG, STATE_SIG_DIST.getMarker())) {
				for (int i = 0; i < trans.length; i++) {
					// Satisfy the lambda constraints of final or effectively final variables
					final int idx = i;
					if (trans[i].isSystem()) {
						// Using lambda version to optimize when marker is disabled
						log.debug(STATE_SIG_DIST.getMarker(),
								"platform {} sent sig to {} for round {}",
								platform::getSelfId,
								() -> otherId,
								() -> Utilities.toLong(trans[idx].getContentsDirect(), 1));
					}
				}
			}


			eventsWritten.incrementAndGet();
			int ntransactions = event.getTransactions() == null ? 0 : event.getTransactions().length;
			log.debug(SYNC_SGM.getMarker(), "{} -> {} `writeUnknownEvents`: end send event {}, num transactions = {}", selfId, otherId, event.getBaseHash(), ntransactions);


		}
		log.debug(SYNC_SGM.getMarker(), "{} -> {} `writeUnknownEvents`: sent {} events", selfId, otherId, diffEvents.size());
		dos.writeByte(SyncConstants.commEventDone);
		long bytesWritten = dos.getSyncByteCounter().getCount() - syncByteCountAtStart;
		int bytesToWrite = slowDown
				? (int) (1 + bytesWritten * Settings.throttle7extra)
				: 0;
		if (bytesToWrite > Settings.throttle7maxBytes) {
			bytesToWrite = Settings.throttle7maxBytes;
		}

		byte[] randomBytes = new byte[bytesToWrite];
		new Random().nextBytes(randomBytes);
		dos.writeByteArray(randomBytes, true);

		platform.getStats().bytesPerSecondCatchupSent.update(bytesToWrite);

		log.debug(SYNC_SGM.getMarker(), "{} -> {} `writeUnknownEvents`: wrote {} bytes to slow down", selfId, otherId, bytesToWrite);

		// record history of events written per sync
		platform.getStats().avgEventsPerSyncSent.recordValue(eventsWritten.get());

	}

	private static long applySlowDown(boolean slowDown, long bytesWritten, SyncOutputStream dos) throws IOException {
		if(!slowDown)
			return 0;

		long bytesToWrite = (long)(1 + bytesWritten * Settings.throttle7extra);
		if (bytesToWrite > Settings.throttle7maxBytes)
			bytesToWrite = Settings.throttle7maxBytes;

		byte[] randomBytes = new byte[(int)bytesToWrite];
		new Random().nextBytes(randomBytes);
		dos.writeByteArray(randomBytes, true);

		return bytesToWrite;
	}

	private static void writeSyncRequestResponse(
			NodeId selfId,
			NodeId otherId,
			DataOutputStream dos,
			boolean canAcceptSync) throws IOException
	{
		dos.writeByte(canAcceptSync
				? SyncConstants.commSyncAck
				: SyncConstants.commSyncNack);
		log.debug(SYNC_SGM.getMarker(), "{} -> {} : `writeSyncRequestResponse`, {}", selfId, otherId,
				(canAcceptSync
						? "sent commSyncAck to accept the sync"
						: "sent commSyncNack to refuse sync because busy"));
	}

	private static void sendSyncRequest(
			NodeId selfId,
			NodeId otherId,
			DataOutputStream dos) throws IOException
	{
		dos.writeByte(SyncConstants.commSyncRequest);
		log.debug(SYNC_SGM.getMarker(), "{} -> {} : `requestSync       `: caller, sent {}", selfId, otherId, SyncConstants.commSyncRequest);
	}

	private static void receiveSyncRequest(
			NodeId selfId,
			NodeId otherId,
			DataInputStream dis) throws IOException
	{
		log.debug(SYNC_SGM.getMarker(), "{} <- {} : `receiveSyncRequest`: receiver", selfId, otherId);
		byte done = dis.readByte();
		if (done != SyncConstants.commSyncRequest) {
			String msg = String.format("received %s instead of %s", done, SyncConstants.commSyncRequest);
			throw new BadIOException(msg);
		}
		log.debug(SYNC_SGM.getMarker(), "{} <- {} : `receiveSyncRequest`: receiver, got {}", selfId, otherId, SyncConstants.commSyncRequest);
	}

	private static boolean receiveSyncRequestResponse(
			NodeId selfId,
			NodeId otherId,
			SyncInputStream dis,
			long timeSyncRequestSent) throws IOException
	{
		boolean accepted;

		// Caller requested a sync, so now read if it was accepted
		byte b = dis.readByte();
		if (b == SyncConstants.commSyncAck) {
			accepted = true;
			log.debug(SYNC_SGM.getMarker(), "{} -> {} : received commSyncAck", selfId, otherId);
		} else if (b == SyncConstants.commSyncNack) {
			accepted = false;
			log.debug(SYNC_SGM.getMarker(), "{} -> {} : received commSyncNack", selfId, otherId);
		} else if (b == SyncConstants.commEndOfStream) {
			accepted = false;
			throw new BadIOException(
					"commSyncRequest was sent " + ((System.nanoTime() - timeSyncRequestSent) / 1_000_000L)
							+ " ms ago, but end of stream was reached "
							+ " instead of the expected response of either commSyncAck or commSyncNack");
		} else {
			accepted = false;
			throw new BadIOException(
					"commSyncRequest was sent " + ((System.nanoTime() - timeSyncRequestSent) / 1_000_000L)
							+ " ms ago, but reply was " + b
							+ " instead of commSyncAck or commSyncNack");
		}


		if(accepted)
			log.debug(SYNC_SGM.getMarker(), "{} -> {} : request accepted", selfId, otherId);
		else
			log.debug(SYNC_SGM.getMarker(), "{} -> {} : request refused", selfId, otherId);

		return accepted;
	}


	/**
	 * Sync step 2 thread A: read the ACK/NACK (for caller only) and counts (in parallel with 2-B)
	 *
	 * @param caller
	 * 		did self (not other) initiate this sync (so caller, not listener)?
	 * @param selfId
	 * 		the member ID of self (the member running this Platform)
	 * @param otherId
	 * 		the member ID of the member that self is syncing with
	 * @param dis
	 * 		the DataInputStream to read from during this sync
	 * @param threadName
	 * 		name of the current thread
	 * @param canAcceptSync
	 * 		true if the receiver is willing to sync now, false if it is not. If caller==true, then
	 * 		accept is ignored.
	 * @param timeSyncRequestSent
	 * 		the time at which the sync request was sent, for debugging purposes
	 * @param numberOfNodes
	 * 		the number of nodes in the network
	 * @return the SyncCallable to run
	 */
	private static SyncCallable syncStep2aReadTipFlags(SyncShadowGraphManager sgm, boolean caller, NodeId selfId,
			NodeId otherId, SyncInputStream dis, String threadName,
			boolean canAcceptSync, long timeSyncRequestSent,
			int numberOfNodes) {
		return new SyncCallable(String.format("<tp %6s ACK   %3s%3s>", //
				(caller ? "caller" : "lstnr"), selfId, otherId)) {
			@Override
			public Object syncCall() throws IOException {
				if (!caller) {
					List<Boolean> tipFlags = new ArrayList<>();
					dis.readBooleanList(1000);
					sgm.setReceivedTipFlags(tipFlags);
					log.debug(SYNC_SGM.getMarker(), "{} <- {} `syncStep2aReadTipFlags`: finished, received {} tip flags", selfId, otherId, tipFlags.size());

//					if(nflags != sgm.tips.size())
//						throw new SyncFailedException(selfId + " <- " + otherId + " `syncStep2aReadTipFlags`: expected " + nflags + " tip flags, got " + sgm.tips.size() + " tip flags");
				}



				return null;
			}
		};
	}

	/**
	 * Sync step 2 thread A: read the ACK/NACK (for caller only) and counts (in parallel with 2-B)
	 *
	 * @param caller
	 * 		did self (not other) initiate this sync (so caller, not listener)?
	 * @param selfId
	 * 		the member ID of self (the member running this Platform)
	 * @param otherId
	 * 		the member ID of the member that self is syncing with
	 * @param dis
	 * 		the DataInputStream to read from during this sync
	 * @param threadName
	 * 		name of the current thread
	 * @param canAcceptSync
	 * 		true if the receiver is willing to sync now, false if it is not. If caller==true, then
	 * 		accept is ignored.
	 * @param timeSyncRequestSent
	 * 		the time at which the sync request was sent, for debugging purposes
	 * @param numberOfNodes
	 * 		the number of nodes in the network
	 * @return the SyncCallable to run
	 */
	private static SyncCallable syncStep2aReadTipHashesAndCounts(
			SyncShadowGraphManager sgm, boolean caller, NodeId selfId,
			NodeId otherId, SyncInputStream dis, String threadName,
			boolean canAcceptSync, long timeSyncRequestSent,
			int numberOfNodes) {
		return new SyncCallable(String.format("<tp %6s ACK   %3s%3s>", //
				(caller ? "caller" : "lstnr"), selfId, otherId)) {
			@Override
			public Object syncCall() throws IOException {
//				boolean syncAccepted = false;
//				if(caller) {
//					syncAccepted = receiveSyncRequestResponse(selfId, otherId, dis, timeSyncRequestSent);
//				}
				boolean syncAccepted = false; // did listener accept caller's request to sync?
				if (caller) { // caller requested a sync, so now read if it was accepted
					int b = dis.read();
					if (b == SyncConstants.commSyncAck) {
						syncAccepted = true;
						log.debug(SYNC.getMarker(),
								"{} received commSyncAck from {}", selfId,
								otherId);
					} else if (b == SyncConstants.commSyncNack) {
						syncAccepted = false;
						log.debug(SYNC.getMarker(),
								"{} received commSyncNack from {}", selfId,
								otherId);
					} else if (b == SyncConstants.commEndOfStream) {
						syncAccepted = false;
						throw new BadIOException(
								"commSyncRequest was sent " + ((System.nanoTime() - timeSyncRequestSent) / 1_000_000L)
										+ " ms ago, but end of stream was reached "
										+ " instead of the expected response of either commSyncAck or commSyncNack");
					} else {
						syncAccepted = false;
						throw new BadIOException(
								"commSyncRequest was sent " + ((System.nanoTime() - timeSyncRequestSent) / 1_000_000L)
										+ " ms ago, but reply was " + b
										+ "` instead of commSyncAck or commSyncNack");
					}
				}
				Object otherCountsLocal = null;
				// listener always reads counts. Caller reads if listener accepted.
				if (!caller || syncAccepted) {

					otherCountsLocal = dis.readAtomicLongArray(numberOfNodes);
					log.debug(SYNC_SGM.getMarker(), "{} <- {} : `syncStep2aReadTipHashesAndCounts`: read {} counts", selfId, otherId, ((AtomicLongArray)otherCountsLocal).length());

					int nhashes = dis.readInt();
					log.debug(SYNC_SGM.getMarker(), "{} <- {} : `syncStep2aReadTipHashesAndCounts`: received: other shadow graph has {} tip hashes", selfId, otherId, nhashes);

					List<Hash> receivedTipHashes = new ArrayList<>();
					for(int i = 0; i < nhashes; ++i) {
						Hash hash = new Hash();
						hash.deserialize(dis, 0);
//						log.debug(SYNC_SGM.getMarker(), "{} <- {} : `syncStep2aReadTipHashesAndCounts`:    received tip hash {}", selfId, otherId, hash.toString());
						receivedTipHashes.add(hash);
					}

					sgm.setReceivedTipHashes(receivedTipHashes);
					log.debug(SYNC_SGM.getMarker(), "{} <- {} : `syncStep2aReadTipHashesAndCounts`: finished, received {} tip hashes", selfId, otherId, receivedTipHashes.size());
				}
				// Both caller and listener always read the counts.
				// But if the listener doesn't accept the sync,
				// then it ignores the counts. So otherCounts will
				// be set to either null or otherCountsLocal
				return (!caller && !canAcceptSync) ? null : otherCountsLocal;
			}
		};
	}



	/**
	 * Sync step 2-B: send the ACK/NACK (listener only) and counts (in parallel with 2-A)
	 *
	 * @param caller
	 * 		did self (not other) initiate this sync (so caller, not listener)?
	 * @param canAcceptSync
	 * 		true if the receiver is willing to sync now, false if it is not. If caller==true, then
	 * 		accept is ignored.
	 * @param dos
	 * 		the DataOutputStream to write to during the sync
	 * @param myCounts
	 * 		number of events by each member known to self at the start of the sync
	 * @param threadName
	 * 		name of the current thread
	 * @param selfId
	 * 		the member ID of self (the member running this Platform)
	 * @param otherId
	 * 		the member ID of the member that self is syncing with
	 * @return the Callable to run
	 */
	private static Callable<Object> syncStep2bWriteTipHashesAndCounts(
			SyncShadowGraphManager sgm, boolean caller, boolean canAcceptSync,
			SyncOutputStream dos, long[] myCounts, String threadName,
			NodeId selfId, NodeId otherId) {
		return new Callable<Object>() {
			@Override
			public Object call() throws IOException {
				if (!caller) { // listener accepts or rejects sync request
					dos.writeByte(canAcceptSync
							? SyncConstants.commSyncAck
							: SyncConstants.commSyncNack);
					log.debug(SYNC.getMarker(), "{} -> {} : `syncStep2bWriteTipHashesAndCounts` {}", selfId, otherId,
							(canAcceptSync
									? "sent commSyncAck to accept the sync"
									: "sent commSyncNack to refuse sync because busy"));
				}
				if (caller || canAcceptSync) {
					dos.writeLongArray(myCounts);
					log.debug(SYNC_SGM.getMarker(), "{} -> {} : `syncStep2bWriteTipHashesAndCounts`: sent {} counts", selfId, otherId, myCounts.length);

					List<Hash> tipHashes = sgm.getSendTipHashes();
					dos.writeInt(tipHashes.size());
					log.debug(SYNC_SGM.getMarker(), "{} -> {} : `syncStep2bWriteTipHashesAndCounts`: sent: shadow graph has {} tip hashes", selfId, otherId, tipHashes.size());
					for(Hash tipHash : tipHashes) {
						dos.writeInt(tipHash.getDigestType().id());
						dos.writeByteArray(tipHash.getValue());
//						log.debug(SYNC_SGM.getMarker(), "{} -> {} : `syncStep2bWriteTipHashesAndCounts`:    sent tip hash     {}", selfId, otherId, tipHash.toString());
					}

					log.debug(SYNC_SGM.getMarker(), "{} -> {} : `syncStep2bWriteTipHashesAndCounts`: finished, sent {} tip hashes", selfId, otherId, tipHashes.size());
				}
				dos.flush();
				return null; // returned value is ignored by the caller
			}
		};
	}



	/**
	 * Sync step 2-B: send the ACK/NACK (listener only) and counts (in parallel with 2-A)
	 *
	 * @param caller
	 * 		did self (not other) initiate this sync (so caller, not listener)?
	 * @param canAcceptSync
	 * 		true if the receiver is willing to sync now, false if it is not. If caller==true, then
	 * 		accept is ignored.
	 * @param dos
	 * 		the DataOutputStream to write to during the sync
	 * @param myCounts
	 * 		number of events by each member known to self at the start of the sync
	 * @param threadName
	 * 		name of the current thread
	 * @param selfId
	 * 		the member ID of self (the member running this Platform)
	 * @param otherId
	 * 		the member ID of the member that self is syncing with
	 * @return the Callable to run
	 */
	private static Callable<Object> syncStep2bWriteTipFlags(SyncShadowGraphManager sgm, boolean caller, boolean canAcceptSync,
			SyncOutputStream dos, long[] myCounts, String threadName,
			NodeId selfId, NodeId otherId) {
		return new Callable<Object>() {
			@Override
			public Object call() throws IOException {

				if (caller) {
					List<Boolean> tipFlags = sgm.getSendTipFlags();
					log.debug(SYNC_SGM.getMarker(), "{} -> {} `syncStep2bWriteTipFlags`: {} tip flags to send", selfId, otherId, tipFlags.size());
					dos.writeBooleanList(tipFlags);
					log.debug(SYNC_SGM.getMarker(), "{} -> {} `syncStep2bWriteTipFlags`: finished, sent {} tip flags", selfId, otherId, tipFlags.size());
				}
				dos.flush();
				return null; // returned value is ignored by the caller
			}
		};
	}


	/**
	 * Sync step 4-A: READ the events, and create a new event
	 *
	 * @param caller
	 * 		did self (not other) initiate this sync (so caller, not listener)?
	 * @param selfId
	 * 		the member ID of self (the member running this Platform)
	 * @param otherId
	 * 		the member ID of the member that self is syncing with
	 * @param dis
	 * 		the DataInputStream to read from during this sync
	 * @param platform
	 * 		the Platform performing this sync
	 * @param myCounts
	 * 		number of events by each member known to self at the start of the sync
	 * @param otherCounts
	 * 		number of events by each member known to other at the start of the sync
	 * @param threadName
	 * 		name of the current thread
	 * @param gotEventDiscarded
	 * 		a variable that should be set to true if we encounter a commEventDiscarded message
	 * @param eventsRead
	 * 		keeps track of the number of events read
	 * @return the SyncCallable to run
	 */
	private static SyncCallable syncStep4aReadEvents(SyncShadowGraphManager sgm,
			boolean caller, NodeId selfId,
			NodeId otherId, SyncInputStream dis, AbstractPlatform platform,
			long[] myCounts, AtomicLongArray otherCounts, String threadName,
			AtomicBoolean gotEventDiscarded, AtomicInteger eventsRead) {
		return new SyncCallable(String.format("<tp %6s ACK   %3s%3s>", //
				(caller ? "caller" : "lstnr"), selfId, otherId)) {
			@Override
			public Object syncCall() throws IOException {
				readUnknownEvents(dis, platform, myCounts, otherCounts, selfId,
						otherId, gotEventDiscarded, eventsRead);
				log.debug(SYNC.getMarker(),
						"{} -> {} `syncStep4aReadEvents`: finished ",
						selfId, otherId);
				return null;
			}
		};
	}



	/**
	 * Sync step 4-B: WRITE the events
	 *
	 * @param dos
	 * 		the DataOutputStream to write to during the sync
	 * @param platform
	 * 		the Platform performing this sync
	 * @param myCounts
	 * 		number of events by each member known to self at the start of the sync
	 * @param otherCounts
	 * 		number of events by each member known to other at the start of the sync
	 * @param selfId
	 * 		the member ID of self (the member running this Platform)
	 * @param otherId
	 * 		the member ID of the member that self is syncing with
	 * @param slowDown
	 * 		should random bytes be sent to slow down so that others who fall behind can catch
	 * 		up?
	 * @param threadName
	 * 		name of the current thread
	 * @param hasRemoteFallenBehind
	 * 		a variable that should be set to true if the remote node asks for an old discarded
	 * 		event
	 * @param eventsWritten
	 * 		keeps track of the number of events written
	 * @return the Callable to run
	 */
	private static Callable<Object> syncStep4bWriteEvents(SyncShadowGraphManager sgm, SyncOutputStream dos,
			AbstractPlatform platform, long[] myCounts, AtomicLongArray otherCounts,
			NodeId selfId, NodeId otherId, boolean slowDown, String threadName,
			AtomicBoolean hasRemoteFallenBehind, AtomicInteger eventsWritten) {
		return new Callable<Object>() {
			@Override
			public Object call() throws IOException {


				writeUnknownEvents(sgm, dos, platform, myCounts, otherCounts, selfId,
						otherId, slowDown, hasRemoteFallenBehind, eventsWritten);
				dos.flush();
				log.debug(SYNC_SGM.getMarker(),
						"{} -> {} `syncStep4bWriteEvents`: finished",
						selfId, otherId);
				return null; // returned value is ignored by the caller
			}
		};
	}





	private static synchronized SyncShadowGraphManager getSyncShadowGraphManager(AbstractPlatform platform, NodeId selfId, NodeId otherId, boolean caller) throws SyncFailedException {

		Hashgraph hashgraph = platform.getHashgraph();
		SyncShadowGraphManager sgm = new SyncShadowGraphManager(hashgraph);
//		SyncShadowGraphManager sgm = platform.getSyncShadowGraphManager();
		sgm.resetForSync();

		String connectionLogString = "";
		if (caller)
			connectionLogString = String.format("%s -> %s", selfId, otherId);
		else
			connectionLogString = String.format("%s <- %s", selfId, otherId);

		{
			EventImpl[] allEvents = hashgraph.getAllEvents();
			int nullCurrentHashCount = 0, nExpired = 0;
			for (EventImpl e : allEvents) {
				if (e.getBaseHash() == null)
					if (e.getGeneration() >= hashgraph.getMinGenerationNonAncient())
						++nullCurrentHashCount;
				if (e.getGeneration() < hashgraph.getMinGenerationNonAncient())
					++nExpired;
			}

			if (nullCurrentHashCount > 0)
				log.debug(SYNC_SGM.getMarker(),
					connectionLogString + " `getSyncShadowGraphManager`: {} Hashgraph current Events have null hashes",
					nullCurrentHashCount);
			log.debug(SYNC_SGM.getMarker(),
				connectionLogString + " `getSyncShadowGraphManager`: {} Hashgraph Events are expired",
				nExpired);
		}

//		sgm.updateByGeneration(hashgraph, selfId, otherId, caller);

		{
			int result = sgm.verify(platform.getHashgraph());
			switch (result) {
				case 0:
					log.debug(SYNC_SGM.getMarker(),
							connectionLogString + " `getSyncShadowGraphManager`: (`result` is {}) Shadow graph is verified",
							result);
					break;
				case 1:
				case 3:
				case 4:
					log.debug(SYNC_SGM.getMarker(),
							connectionLogString + " `getSyncShadowGraphManager`: (`result` is {}) Shadow graph is conditionally verified",
							result);
					break;
				default:
					log.debug(SYNC_SGM.getMarker(),
							connectionLogString + " `getSyncShadowGraphManager`: (`result` is {}) Shadow graph construction failed",
							result);
			}
		}

		if (sgm.shadowGraph.shadowEvents.size() > 0 && sgm.tips.size() == 0)
			throw new SyncFailedException(
					connectionLogString + ": shadow graph has " + sgm.shadowGraph.shadowEvents.size() + " events but zero tips!");

		return sgm;
	}
}
