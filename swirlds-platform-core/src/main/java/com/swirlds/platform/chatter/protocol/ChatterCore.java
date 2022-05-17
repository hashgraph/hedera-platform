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

import com.swirlds.platform.chatter.protocol.input.InputDelegate;
import com.swirlds.platform.chatter.protocol.input.InputDelegateBuilder;
import com.swirlds.platform.chatter.protocol.input.MessageTypeHandlerBuilder;
import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.chatter.protocol.messages.ChatterEventDescriptor;
import com.swirlds.platform.chatter.protocol.output.MessageOutput;
import com.swirlds.platform.chatter.protocol.output.PriorityOutputAggregator;
import com.swirlds.platform.chatter.protocol.output.SendAction;
import com.swirlds.platform.chatter.protocol.output.WaitBeforeSending;
import com.swirlds.platform.chatter.protocol.output.queue.QueueOutputMain;
import com.swirlds.platform.chatter.protocol.peer.PeerGossipState;
import com.swirlds.platform.chatter.protocol.peer.PeerInstance;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Links all components of chatter together. Constructs and keeps track of peer instances.
 *
 * @param <E>
 * 		the type of {@link ChatterEvent} used
 */
public class ChatterCore<E extends ChatterEvent> implements Purgable, MessageHandler<E> {
	private final long selfId;
	private final Class<E> eventClass;
	private final MessageHandler<E> prepareReceivedEvent;
	private final Supplier<Instant> now;
	private final MessageOutput<E> eventOutput;
	private final MessageOutput<ChatterEventDescriptor> hashOutput;
	private final Map<Long, PeerInstance> peerInstances;

	/**
	 * @param selfId
	 * 		the ID of this node
	 * @param eventClass
	 * 		the class of the type of event used
	 * @param prepareReceivedEvent
	 * 		the first handler to be called when an event is received, this should do any preparation work that might be
	 * 		needed by other handlers (such as hashing)
	 * @param now
	 * 		supplies the current wall clock time
	 */
	public ChatterCore(
			final long selfId,
			final Class<E> eventClass,
			final MessageHandler<E> prepareReceivedEvent,
			final Supplier<Instant> now) {
		this.selfId = selfId;
		this.eventClass = eventClass;
		this.prepareReceivedEvent = prepareReceivedEvent;
		this.now = now;
		this.eventOutput = new QueueOutputMain<>();
		this.hashOutput = new QueueOutputMain<>();
		this.peerInstances = new HashMap<>();
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
		final MessageProvider eventPeerInstance = eventOutput.createPeerInstance(
				new WaitBeforeSending<>(selfId, state, now)
		);
		final MessageProvider hashPeerInstance = hashOutput.createPeerInstance(
				d -> SendAction.SEND // always send hashes
		);
		final PriorityOutputAggregator outputAggregator = new PriorityOutputAggregator(List.of(
				hashPeerInstance,
				eventPeerInstance
		));
		final InputDelegate inputDelegate = InputDelegateBuilder.builder()
				.addHandler(MessageTypeHandlerBuilder.builder(eventClass)
						.addHandler(prepareReceivedEvent)
						.addHandler(state::handleEvent)
						.addHandler(eventHandler)
						.build())
				.addHandler(MessageTypeHandlerBuilder.builder(ChatterEventDescriptor.class)
						.addHandler(state::handleDescriptor)
						.build())
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
	 * Enqueue an event and its descriptor to be sent to appropriate peers
	 *
	 * @param event
	 * 		the event to send
	 */
	@Override
	public void handleMessage(final E event) {
		hashOutput.send(event.getDescriptor());
		eventOutput.send(event);
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
}
