/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.platform.chatter.protocol;

import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.chatter.protocol.input.InputDelegate;
import com.swirlds.platform.chatter.protocol.input.InputDelegateBuilder;
import com.swirlds.platform.chatter.protocol.input.MessageTypeHandlerBuilder;
import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.chatter.protocol.messages.ChatterEventDescriptor;
import com.swirlds.platform.chatter.protocol.output.AgeBasedDelay;
import com.swirlds.platform.chatter.protocol.output.MessageOutput;
import com.swirlds.platform.chatter.protocol.output.PriorityOutputAggregator;
import com.swirlds.platform.chatter.protocol.output.SendAction;
import com.swirlds.platform.chatter.protocol.output.queue.QueueOutputMain;
import com.swirlds.platform.chatter.protocol.peer.PeerGossipState;
import com.swirlds.platform.chatter.protocol.peer.PeerInstance;
import com.swirlds.platform.stats.AverageStat;
import com.swirlds.platform.stats.PerSecondStat;
import com.swirlds.platform.stats.PerSecondStatsProvider;
import com.swirlds.platform.stats.StatsProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Links all components of chatter together. Constructs and keeps track of peer instances.
 *
 * @param <E>
 * 		the type of {@link ChatterEvent} used
 */
public class ChatterCore<E extends ChatterEvent> implements Purgable, StatsProvider, PerSecondStatsProvider {
	public static final int GEN_DIFF_SEND = 200;
	private final Class<E> eventClass;
	private final MessageHandler<E> prepareReceivedEvent;
	private final MessageOutput<E> selfEventOutput;
	private final MessageOutput<E> otherEventOutput;
	private final MessageOutput<ChatterEventDescriptor> hashOutput;
	private final Map<Long, PeerInstance> peerInstances;

	private final PerSecondStat msgsPerSecRead;
	private final PerSecondStat msgsPerSecWrit;

	/**
	 * @param eventClass
	 * 		the class of the type of event used
	 * @param prepareReceivedEvent
	 * 		the first handler to be called when an event is received, this should do any preparation work that might be
	 * 		needed by other handlers (such as hashing)
	 */
	public ChatterCore(
			final Class<E> eventClass,
			final MessageHandler<E> prepareReceivedEvent) {
		this.eventClass = eventClass;
		this.prepareReceivedEvent = prepareReceivedEvent;
		this.selfEventOutput = new QueueOutputMain<>("selfEvent");
		this.otherEventOutput = new QueueOutputMain<>("otherEvent");
		this.hashOutput = new QueueOutputMain<>("descriptor");
		this.peerInstances = new HashMap<>();

		this.msgsPerSecRead = new PerSecondStat(
				new AverageStat(
						"chatter",
						"msgsPerSecRead",
						"number of chatter messages read per second",
						"%,8.1f",
						AverageStat.WEIGHT_VOLATILE
				)
		);
		this.msgsPerSecWrit = new PerSecondStat(
				new AverageStat(
						"chatter",
						"msgsPerSecWrit",
						"number of chatter messages written per second",
						"%,8.1f",
						AverageStat.WEIGHT_VOLATILE
				)
		);
	}

	/**
	 * Creates an instance that will handle all communication with a peer
	 *
	 * @param peerId
	 * 		the peer's ID
	 * @param eventHandler
	 * 		a handler that will send the event outside of chatter
	 */
	public void newPeerInstance(final long peerId, final MessageHandler<E> eventHandler) {
		final PeerGossipState state = new PeerGossipState();
		final MessageProvider hashPeerInstance = hashOutput.createPeerInstance(
				d -> SendAction.SEND // always send hashes
		);
		final MessageProvider selfEventPeerInstance = selfEventOutput.createPeerInstance(
				d -> SendAction.SEND // always send self events
		);
		final MessageProvider otherEventPeerInstance = otherEventOutput.createPeerInstance(
				new AgeBasedDelay<>(GEN_DIFF_SEND, state)
		);
		final PriorityOutputAggregator outputAggregator = new PriorityOutputAggregator(
				List.of(
						hashPeerInstance,
						selfEventPeerInstance,
						otherEventPeerInstance),
				msgsPerSecWrit);
		final InputDelegate inputDelegate = InputDelegateBuilder.builder()
				.addHandler(MessageTypeHandlerBuilder.builder(eventClass)
						.addHandler(prepareReceivedEvent)
						.addHandler(state::handleEvent)
						.addHandler(eventHandler)
						.build())
				.addHandler(MessageTypeHandlerBuilder.builder(ChatterEventDescriptor.class)
						.addHandler(state::handleDescriptor)
						.build())
				.setStat(msgsPerSecRead)
				.build();
		final PeerInstance peerInstance = new PeerInstance(
				state,
				outputAggregator,
				inputDelegate
		);
		peerInstances.put(peerId, peerInstance);
	}

	/**
	 * @param id
	 * 		the ID of the peer
	 * @return the instance responsible for all communication with a peer
	 */
	public PeerInstance getPeerInstance(final long id) {
		return peerInstances.get(id);
	}

	/**
	 * Notify chatter that a new event has been created
	 *
	 * @param event
	 * 		the new event
	 */
	public void eventCreated(final E event) {
		selfEventOutput.send(event);
	}

	/**
	 * Notify chatter that an event has been received and validated
	 *
	 * @param event
	 * 		the event received
	 */
	public void eventReceived(final E event) {
		hashOutput.send(event.getDescriptor());
		otherEventOutput.send(event);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void purge(final long olderThan) {
		for (final PeerInstance peer : peerInstances.values()) {
			peer.state().purge(olderThan);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<StatEntry> getStats() {
		return CommonUtils.joinLists(
				hashOutput.getStats(),
				selfEventOutput.getStats(),
				otherEventOutput.getStats()
		);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<PerSecondStat> getPerSecondStats() {
		return List.of(msgsPerSecRead, msgsPerSecWrit);
	}
}
