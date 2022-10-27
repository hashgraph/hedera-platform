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

package com.swirlds.platform.chatter.protocol;

import com.swirlds.common.Clock;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.sequence.Shiftable;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.chatter.ChatterSettings;
import com.swirlds.platform.chatter.protocol.heartbeat.HeartbeatMessage;
import com.swirlds.platform.chatter.protocol.heartbeat.HeartbeatSendReceive;
import com.swirlds.platform.chatter.protocol.input.InputDelegate;
import com.swirlds.platform.chatter.protocol.input.InputDelegateBuilder;
import com.swirlds.platform.chatter.protocol.input.MessageTypeHandlerBuilder;
import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.chatter.protocol.messages.ChatterEventDescriptor;
import com.swirlds.platform.chatter.protocol.output.MessageOutput;
import com.swirlds.platform.chatter.protocol.output.PriorityOutputAggregator;
import com.swirlds.platform.chatter.protocol.output.SendAction;
import com.swirlds.platform.chatter.protocol.output.VariableTimeDelay;
import com.swirlds.platform.chatter.protocol.output.queue.QueueOutputMain;
import com.swirlds.platform.chatter.protocol.peer.CommunicationState;
import com.swirlds.platform.chatter.protocol.peer.PeerGossipState;
import com.swirlds.platform.chatter.protocol.peer.PeerInstance;
import com.swirlds.platform.chatter.protocol.processing.ProcessingTimeMessage;
import com.swirlds.platform.chatter.protocol.processing.ProcessingTimeSendReceive;
import com.swirlds.platform.chatter.protocol.processing.ProcessingTimes;
import com.swirlds.platform.state.signed.LoadableFromSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.stats.AverageStat;
import com.swirlds.platform.stats.PerSecondStat;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_8_1;

/**
 * Links all components of chatter together. Constructs and keeps track of peer instances.
 *
 * @param <E>
 * 		the type of {@link ChatterEvent} used
 */
public class ChatterCore<E extends ChatterEvent> implements Shiftable, LoadableFromSignedState {
	/** the number of milliseconds to sleep while waiting for the chatter protocol to stop */
	private static final int STOP_WAIT_SLEEP_MILLIS = 10;
	private final Class<E> eventClass;
	private final MessageHandler<E> prepareReceivedEvent;
	private final ChatterSettings settings;
	private final BiConsumer<NodeId, Long> pingConsumer;
	private final MessageOutput<E> selfEventOutput;
	private final MessageOutput<E> otherEventOutput;
	private final MessageOutput<ChatterEventDescriptor> hashOutput;
	private final Map<Long, PeerInstance> peerInstances;

	private final PerSecondStat msgsPerSecRead;
	private final PerSecondStat msgsPerSecWrit;
	private final ProcessingTimes processingTimes;

	/**
	 * @param eventClass
	 * 		the class of the type of event used
	 * @param prepareReceivedEvent
	 * 		the first handler to be called when an event is received, this should do any preparation work that might be
	 * 		needed by other handlers (such as hashing)
	 * @param settings
	 * 		chatter settings
	 * @param pingConsumer
	 * 		consumer of the reported ping time for a given peer. accepts the ID of the peer and the number of
	 * 		nanoseconds it took for the peer to respond
	 * @param metrics
	 * 		reference to the metrics-system
	 */
	public ChatterCore(
			final Class<E> eventClass,
			final MessageHandler<E> prepareReceivedEvent,
			final ChatterSettings settings,
			final BiConsumer<NodeId, Long> pingConsumer,
			final Metrics metrics) {
		this.eventClass = eventClass;
		this.prepareReceivedEvent = prepareReceivedEvent;
		this.settings = settings;
		this.pingConsumer = pingConsumer;
		this.selfEventOutput = new QueueOutputMain<>("selfEvent", settings.getSelfEventQueueCapacity(), metrics);
		this.otherEventOutput = new QueueOutputMain<>("otherEvent", settings.getOtherEventQueueCapacity(), metrics);
		this.hashOutput = new QueueOutputMain<>("descriptor", settings.getDescriptorQueueCapacity(), metrics);
		this.peerInstances = new HashMap<>();
		this.processingTimes = new ProcessingTimes(metrics);

		this.msgsPerSecRead = new PerSecondStat(
				new AverageStat(
						metrics,
						"chatter",
						"msgsPerSecRead",
						"number of chatter messages read per second",
						FORMAT_8_1,
						AverageStat.WEIGHT_VOLATILE
				)
		);
		this.msgsPerSecWrit = new PerSecondStat(
				new AverageStat(
						metrics,
						"chatter",
						"msgsPerSecWrit",
						"number of chatter messages written per second",
						FORMAT_8_1,
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
		final PeerGossipState state = new PeerGossipState(settings.getFutureGenerationLimit());
		final CommunicationState communicationState = new CommunicationState();
		final HeartbeatSendReceive heartbeat = new HeartbeatSendReceive(
				peerId,
				pingConsumer,
				settings.getHeartbeatInterval()
		);

		final ProcessingTimeSendReceive processingTimeSendReceive = new ProcessingTimeSendReceive(
				peerId,
				settings.getProcessingTimeInterval(),
				processingTimes,
				Clock.DEFAULT
		);

		final MessageProvider hashPeerInstance = hashOutput.createPeerInstance(
				communicationState,
				d -> SendAction.SEND // always send hashes
		);
		final MessageProvider selfEventPeerInstance = selfEventOutput.createPeerInstance(
				communicationState,
				d -> SendAction.SEND // always send self events
		);
		final MessageProvider otherEventPeerInstance = otherEventOutput.createPeerInstance(
				communicationState,
				new VariableTimeDelay<>(
						() -> getOtherEventDelay(peerId, heartbeat),
						state,
						Instant::now)
		);
		final PriorityOutputAggregator outputAggregator = new PriorityOutputAggregator(
				List.of(
						// heartbeat is first so that responses are not delayed
						heartbeat,
						processingTimeSendReceive,
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
				.addHandler(MessageTypeHandlerBuilder.builder(HeartbeatMessage.class)
						.addHandler(heartbeat)
						.build())
				.addHandler(MessageTypeHandlerBuilder.builder(ProcessingTimeMessage.class)
						.addHandler(processingTimeSendReceive)
						.build())
				.setStat(msgsPerSecRead)
				.build();
		final PeerInstance peerInstance = new PeerInstance(
				communicationState,
				state,
				outputAggregator,
				inputDelegate
		);
		peerInstances.put(peerId, peerInstance);
	}

	/**
	 * Calculates the delay for sending other events to a peer.
	 *
	 * @param peerId
	 * 		the id of the peer to calculate other event delay for
	 * @param heartbeat
	 * 		the heartbeat instance used to send and receive heartbeats with the peer
	 * @return the other event delay to use, or null if other events should not be sent to the peer at this time
	 */
	private Duration getOtherEventDelay(final long peerId, final HeartbeatSendReceive heartbeat) {
		final Long peerProcessingNanos = processingTimes.getPeerProcessingTime(peerId);
		final Long roundTripNanos = heartbeat.getLastRoundTripNanos();
		if (peerProcessingNanos == null || roundTripNanos == null) {
			return null;
		}
		final long oneWayTripNanos = roundTripNanos / 2;
		return Duration.ofNanos(oneWayTripNanos + peerProcessingNanos).plus(settings.getOtherEventDelay());
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
	 * @return the instances responsible for all communication with all peers
	 */
	public Collection<PeerInstance> getPeerInstances() {
		return peerInstances.values();
	}

	/**
	 * Notify chatter that a new event has been created
	 *
	 * @param event
	 * 		the new event
	 */
	public void eventCreated(final E event) {
		selfEventOutput.send(event);
		recordProcessingTime(event);
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
		recordProcessingTime(event);
	}

	private void recordProcessingTime(final E event) {
		processingTimes.recordSelfProcessingTime(Duration.between(event.getTimeReceived(), Instant.now()));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shiftWindow(final long firstSequenceNumberInWindow) {
		for (final PeerInstance peer : peerInstances.values()) {
			peer.state().shiftWindow(firstSequenceNumberInWindow);
		}
	}

	@Override
	public void loadFromSignedState(final SignedState signedState) {
		shiftWindow(signedState.getMinRoundGeneration());
	}

	/**
	 * Stop chattering with all peers
	 */
	public void stopChatter() {
		// set the chatter state to suspended for all peers
		for (final PeerInstance peer : peerInstances.values()) {
			peer.communicationState().suspend();
		}
		// wait for all communication to end
		for (final PeerInstance peer : peerInstances.values()) {
			while (peer.communicationState().isAnyProtocolRunning()) {
				try {
					// we assume the thread calling this will never be interrupted
					Thread.sleep(STOP_WAIT_SLEEP_MILLIS);
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
		// clear all queues
		for (final PeerInstance peer : peerInstances.values()) {
			peer.outputAggregator().clear();
		}
	}

	/**
	 * Start chatter if it has been previously stopped
	 */
	public void startChatter() {
		// allow chatter to start
		for (final PeerInstance peer : peerInstances.values()) {
			peer.communicationState().unsuspend();
		}
	}
}
