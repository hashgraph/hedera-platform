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

import com.swirlds.common.threading.ParallelExecutionException;
import com.swirlds.common.threading.ParallelExecutor;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.components.EventTaskCreator;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.stats.SyncStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.swirlds.logging.LogMarker.SYNC_INFO;

/**
 * The goal of the ShadowGraphSynchronizer is to compare graphs with a remote node, and update them so both sides
 * have the same events in the graph. This process is called a sync.
 * <p>
 * This instance can be called by multiple threads at the same time. To avoid accidental concurrency issues, all the
 * variables in this class are final. The ones that are used for storing information about an ongoing sync are method
 * local.
 */
public class ShadowGraphSynchronizer {
	private static final Logger LOG = LogManager.getLogger();

	/** The shadow graph manager to use for this sync */
	private final ShadowGraph shadowGraph;
	/** Number of member nodes in the network for this sync */
	private final int numberOfNodes;
	/** All sync stats */
	private final SyncStats stats;
	/**
	 * provides the current consensus instance, a supplier is used because this instance will change after a
	 * reconnect, so we have to make sure we always get the latest one
	 */
	private final Supplier<GraphGenerations> generationsSupplier;
	/** for event creation and validation */
	private final EventTaskCreator eventTaskCreator;
	/** manages sync related decisions */
	private final FallenBehindManager fallenBehindManager;
	/** executes tasks in parallel */
	private final ParallelExecutor executor;
	/** provides system settings */
	private final SettingsProvider settings;

	public ShadowGraphSynchronizer(
			final ShadowGraph shadowGraph,
			final int numberOfNodes,
			final SyncStats stats,
			final Supplier<GraphGenerations> generationsSupplier,
			final EventTaskCreator eventTaskCreator,
			final FallenBehindManager fallenBehindManager,
			final ParallelExecutor executor,
			final SettingsProvider settings) {
		this.shadowGraph = shadowGraph;
		this.numberOfNodes = numberOfNodes;
		this.stats = stats;
		this.generationsSupplier = generationsSupplier;
		this.eventTaskCreator = eventTaskCreator;
		this.fallenBehindManager = fallenBehindManager;
		this.executor = executor;
		this.settings = settings;
	}

	/**
	 * Synchronize with a remote node using the supplied connection
	 *
	 * @param conn
	 * 		the connection to sync through
	 * @return true iff a sync was (a) accepted, and (b) completed, including exchange of event data
	 * @throws IOException
	 * 		if any problem occurs with the connection
	 * @throws ParallelExecutionException
	 * 		if issue occurs while executing tasks in parallel
	 * @throws SyncException
	 * 		if any sync protocol issues occur
	 */
	public boolean synchronize(final SyncConnection conn)
			throws IOException, ParallelExecutionException, SyncException {
		LOG.info(SYNC_INFO.getMarker(), "{} sync start", conn.getDescription());
		try {
			return reserveSynchronize(conn);
		} finally {
			LOG.info(SYNC_INFO.getMarker(), "{} sync end", conn.getDescription());
		}
	}

	/**
	 * Executes a sync using the supplied connection. This method contains all the logic while {@link
	 * #synchronize(SyncConnection)} is just for exception handling.
	 */
	private boolean reserveSynchronize(final SyncConnection conn)
			throws IOException, ParallelExecutionException, SyncException {
		// accumulates time points for each step in the execution of a single gossip session, used for stats
		// reporting and performance analysis
		final SyncTiming timing = new SyncTiming();
		final List<EventImpl> sendList;
		try (GenerationReservation reservation = shadowGraph.reserve()) {
			conn.initForSync();

			timing.start();

			SyncComms.writeFirstByte(conn);

			// the generation we reserved is our minimum round generation
			// the ShadowGraph guarantees it won't be expired until we release it
			final SyncGenerations myGenerations = getGenerations(reservation.getGeneration());
			final List<ShadowEvent> myTips = getTips();
			// READ and WRITE generation numbers & tip hashes
			final Phase1Response theirGensTips = executor.doParallel(
					SyncComms.phase1Read(conn, numberOfNodes),
					SyncComms.phase1Write(conn, myGenerations, myTips)
			);
			timing.setTimePoint(1);

			if (theirGensTips.isSyncRejected()) {
				LOG.info(SYNC_INFO.getMarker(), "{} sync rejected by other", conn.getDescription());
				// null means the sync was rejected
				return false;
			}

			stats.generations(myGenerations, theirGensTips.getGenerations());

			if (fallenBehind(myGenerations, theirGensTips.getGenerations(), conn)) {
				// aborting the sync since someone has fallen behind
				return false;
			}

			// events that I know they already have
			final Set<ShadowEvent> knownSet = new HashSet<>();

			// process the hashes received
			final List<ShadowEvent> theirTipShadows = shadowGraph.shadows(theirGensTips.getTips());
			final List<Boolean> myBooleans = getMyBooleans(theirTipShadows);
			// add known shadows to known set
			theirTipShadows.stream().filter(Objects::nonNull).forEach(knownSet::add);

			// comms phase 2
			timing.setTimePoint(2);
			final List<Boolean> theirBooleans = executor.doParallel(
					SyncComms.phase2Read(conn, myTips.size()),
					SyncComms.phase2Write(conn, myBooleans)
			);
			timing.setTimePoint(3);

			// process their booleans and add them to the known set
			final List<ShadowEvent> knownTips = processTheirBooleans(conn, myTips, theirBooleans);
			knownSet.addAll(knownTips);

			// create a send list based on the known set
			sendList = createSendList(knownSet, myGenerations, theirGensTips.getGenerations());
		}

		phase3(conn, timing, sendList);
		return true;
	}

	private SyncGenerations getGenerations(final long minRoundGen) {
		return new SyncGenerations(
				minRoundGen,
				generationsSupplier.get().getMinGenerationNonAncient(),
				generationsSupplier.get().getMaxRoundGeneration()
		);
	}

	private List<ShadowEvent> getTips() {
		final List<ShadowEvent> myTips = shadowGraph.getTips();
		stats.updateTipsPerSync(myTips.size());
		stats.updateMultiTipsPerSync(SyncUtils.computeMultiTipCount(myTips));
		return myTips;
	}

	private boolean fallenBehind(final SyncGenerations self, final SyncGenerations other, final SyncConnection conn) {
		final SyncFallenBehindStatus status = SyncFallenBehindStatus.getStatus(self, other);
		if (status == SyncFallenBehindStatus.SELF_FALLEN_BEHIND) {
			fallenBehindManager.reportFallenBehind(conn.getOtherId());
		}

		if (status != SyncFallenBehindStatus.NONE_FALLEN_BEHIND) {
			LOG.info(SYNC_INFO.getMarker(), "{} aborting sync due to {}", conn.getDescription(), status);
			return true; //abort the sync
		}
		return false;
	}

	private static List<Boolean> getMyBooleans(final List<ShadowEvent> theirTipShadows) {
		final List<Boolean> myBooleans = new ArrayList<>(theirTipShadows.size());
		for (ShadowEvent s : theirTipShadows) {
			myBooleans.add(s != null);// is this event is known to me
		}
		return myBooleans;
	}

	private static List<ShadowEvent> processTheirBooleans(
			final SyncConnection conn,
			final List<ShadowEvent> myTips,
			final List<Boolean> theirBooleans
	) throws SyncException {
		if (theirBooleans.size() != myTips.size()) {
			throw new SyncException(
					conn,
					String.format(
							"peer booleans list is wrong size. Expected: %d Actual: %d,",
							myTips.size(), theirBooleans.size())
			);
		}
		List<ShadowEvent> knownTips = new ArrayList<>();
		// process their booleans
		for (int i = 0; i < theirBooleans.size(); i++) {
			if (Boolean.TRUE.equals(theirBooleans.get(i))) {
				knownTips.add(myTips.get(i));
			}
		}

		return knownTips;
	}

	private List<EventImpl> createSendList(
			final Set<ShadowEvent> knownSet,
			final SyncGenerations myGenerations,
			final SyncGenerations theirGenerations) {
		// add to knownSet all the ancestors of each known event
		final Set<ShadowEvent> knownAncestors =
				shadowGraph.findAncestors(knownSet,
						SyncUtils.unknownNonAncient(knownSet, myGenerations, theirGenerations));

		// since knownAncestors is a lot bigger than knownSet, it is a lot cheaper to add knownSet to knownAncestors
		// then vice versa
		knownAncestors.addAll(knownSet);

		stats.knownSetSize(knownAncestors.size());

		// predicate used to search for events to send
		final Predicate<ShadowEvent> knownAncestorsPredicate =
				SyncUtils.unknownNonAncient(knownAncestors, myGenerations, theirGenerations);


		// in order to get the peer the latest events, we get a new set of tips to search from
		final List<ShadowEvent> myNewTips = shadowGraph.getTips();

		// find all ancestors of tips that are not known
		final List<ShadowEvent> unknownTips =
				myNewTips.stream().filter(knownAncestorsPredicate).collect(Collectors.toList());
		final Set<ShadowEvent> sendSet = shadowGraph.findAncestors(unknownTips, knownAncestorsPredicate);
		// add the tips themselves
		sendSet.addAll(unknownTips);

		// convert to list
		final List<EventImpl> sendList = sendSet.stream()
				.map(ShadowEvent::getEvent)
				.collect(Collectors.toCollection(ArrayList::new));
		// sort by generation
		SyncUtils.sort(sendList);

		return sendList;
	}

	private void phase3(
			final SyncConnection conn,
			final SyncTiming timing,
			final List<EventImpl> sendList)
			throws ParallelExecutionException, IOException, SyncException {
		timing.setTimePoint(4);
		// the reading thread uses this to indicate to the writing thread that it is done
		final CountDownLatch eventReadingDone = new CountDownLatch(1);
		final Integer eventsRead = executor.doParallel(
				SyncComms.phase3Read(conn, eventTaskCreator::addEvent, stats, eventReadingDone),
				SyncComms.phase3Write(conn, sendList, eventReadingDone)
		);
		LOG.info(SYNC_INFO.getMarker(),
				"{} writing events done, wrote {} events",
				conn::getDescription, sendList::size);
		LOG.info(SYNC_INFO.getMarker(), "{} reading events done, read {} events",
				conn.getDescription(), eventsRead);

		SyncComms.syncDoneByte(conn);

		syncDone(new SyncResult(
				conn.isOutbound(),
				conn.getOtherId(),
				eventsRead,
				sendList.size()
		));

		throttle7(conn, eventsRead, sendList.size());

		timing.setTimePoint(5);
		stats.recordSyncTiming(timing, conn);
	}

	/**
	 * Applies throttle7 if enabled and applicable to this sync.
	 */
	private void throttle7(final SyncConnection conn, final int eventsReceived,
			final int eventsSent) throws ParallelExecutionException {
		if (settings.isThrottle7Enabled()) {
			SyncThrottle throttle7 = new SyncThrottle(numberOfNodes, settings);
			if (throttle7.shouldThrottle(eventsReceived, eventsSent)) {
				Integer throttle7BytesWritten = executor.doParallel(
						throttle7.sendThrottleBytes(conn),
						throttle7.receiveThrottleBytes(conn)
				);
				stats.syncThrottleBytesWritten(throttle7BytesWritten);
			}
		}
	}

	private void syncDone(final SyncResult info) {
		eventTaskCreator.syncDone(info);
		stats.syncDone(info);
	}

	/**
	 * Reject a sync
	 *
	 * @param conn
	 * 		the connection over which the sync was initiated
	 * @throws IOException
	 * 		if there are any connection issues
	 */
	public void rejectSync(final SyncConnection conn) throws IOException {
		try {
			conn.initForSync();
			SyncComms.rejectSync(conn, numberOfNodes);
		} finally {
			LOG.info(SYNC_INFO.getMarker(), "{} sync rejected by self", conn.getDescription());
		}
	}

}
