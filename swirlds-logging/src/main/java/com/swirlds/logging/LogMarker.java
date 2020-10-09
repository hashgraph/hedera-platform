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

package com.swirlds.logging;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

/**
 * Definitions of all log markers.
 */
public enum LogMarker {

	/**
	 * log all exceptions, and serious problems. These should never happen there is a bug in the code. In most cases,
	 * this should include a full stack trace of the exception.
	 */
	EXCEPTION(LogMarkerType.ERROR),

	/**
	 * exceptions that shouldn't happen during testing, but can happen in production if there is a malicious
	 * node. This should be turned off in production so that a malicious node cannot clutter the logs
	 */
	TESTING_EXCEPTIONS(LogMarkerType.ERROR),

	/**
	 * log the 4 sync exceptions (EOFException, SocketTimeoutException, SocketException, IOException)
	 */
	SOCKET_EXCEPTIONS(LogMarkerType.ERROR),

	/**
	 * log socket exceptions related to connecting to a node, this is a separate marker to avoid filling the log file
	 * in case one node is down, or it has not started yet
	 */
	TCP_CONNECT_EXCEPTIONS(LogMarkerType.ERROR),

	/**
	 * exceptions that shouldn't happen during testing, except for all nodes in reconnect test
	 */
	TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT(LogMarkerType.ERROR),

	/**
	 * exceptions that shouldn't happen during testing, except for reconnect node(s)
	 */
	TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT_NODE(LogMarkerType.ERROR),

	/**
	 * Exceptions acceptable only for killNodeReconnect, as when node is killed delete/create
	 * transactions can be submitted for deleted/already existing entities if the previous
	 * transaction was not handled before node is killed
	 */
	PERFORM_ON_CREATE_DELETE(LogMarkerType.ERROR),

	ERROR(LogMarkerType.ERROR),

	/**
	 * log any events received which were not valid
	 */
	INVALID_EVENT_ERROR(LogMarkerType.ERROR),

	/**
	 * logs events related to the startup of the application
	 */
	STARTUP(LogMarkerType.INFO),

	/**
	 * log events related to the shadow graph manager during gossiping
	 */
	SYNC_SGM(LogMarkerType.INFO),

	/**
	 * log all the steps of a sync
	 */
	SYNC(LogMarkerType.INFO),

	/**
	 * about to sync
	 */
	SYNC_START(LogMarkerType.INFO),

	/**
	 * completed sync
	 */
	SYNC_DONE(LogMarkerType.INFO),

	/**
	 * connection error C or L -1 or -2
	 */
	SYNC_ERROR(LogMarkerType.INFO), // doesn't seem to be an actual error, should be renamed

	/**
	 * log each new event created (not received)
	 */
	CREATE_EVENT(LogMarkerType.INFO),

	/**
	 * log each event as it's added to the hashgraph
	 */
	ADD_EVENT(LogMarkerType.INFO),

	/**
	 * log each event as it's added to the intake queue
	 */
	INTAKE_EVENT(LogMarkerType.INFO),

	/**
	 * log every event sent and received
	 */
	WATCH_EVENTS_SEND_REC(LogMarkerType.INFO),

	/**
	 * log when events enter and leave the various event queues in eventFlow
	 */
	QUEUES(LogMarkerType.INFO),

	/**
	 * log the sending and receiving of the heartbeats from SyncHeartbeat to SyncListener
	 */
	HEARTBEAT(LogMarkerType.INFO),

	/**
	 * logs info related to event signatures
	 */
	EVENT_SIG(LogMarkerType.INFO),

	/**
	 * logs the certificates either loaded of created
	 */
	CERTIFICATES(LogMarkerType.INFO),

	/**
	 * logs info related to obtaining and releasing synchronization locks
	 */
	LOCKS(LogMarkerType.INFO),

	/**
	 * logs info that is parsed and used to measure duration of anything
	 */
	TIME_MEASURE(LogMarkerType.INFO),

	/**
	 * logs events related the distribution of state signatures
	 */
	STATE_SIG_DIST(LogMarkerType.INFO),

	/**
	 * logs events related to event streaming
	 */
	EVENT_STREAM(LogMarkerType.INFO),

	/**
	 * logs detail information related to object streaming
	 */
	OBJECT_STREAM_DETAIL(LogMarkerType.INFO),

	OBJECT_STREAM(LogMarkerType.INFO),

	OBJECT_STREAM_FILE(LogMarkerType.INFO),

	/**
	 * logs events related platform freezing
	 */
	FREEZE(LogMarkerType.INFO),

	/**
	 * logs info related to signed states being saved to disk
	 */
	STATE_TO_DISK(LogMarkerType.INFO),

	/**
	 * logs related to the state deleter
	 */
	STATE_DELETER(LogMarkerType.INFO),

	/**
	 * logs related to the lastCompleteSigned state
	 */
	LAST_COMPLETE_SIGNED_STATE(LogMarkerType.INFO),

	/**
	 * logs related to a signed state
	 */
	SIGNED_STATE(LogMarkerType.INFO),

	/**
	 * logs related to state recovery
	 */
	EVENT_PARSER(LogMarkerType.INFO),

	/**
	 * logs events related to reconnect
	 */
	RECONNECT(LogMarkerType.INFO),

	/**
	 * logs related to PTA runs. It is useful during debugging PTA with info from the platform
	 */
	DEMO_INFO(LogMarkerType.INFO),

	/**
	 * logs related to stale events
	 */
	STALE_EVENTS(LogMarkerType.INFO),

	// Crypto
	ADV_CRYPTO_SYSTEM(LogMarkerType.INFO),
	OPENCL_INIT_EXCEPTIONS(LogMarkerType.INFO),
	MERKLE_HASHING(LogMarkerType.INFO),

	// Signed State Manager Queue Failures
	/**
	 * logs related to the save to disk queue which holds the list of signed states written to disk
	 */
	STATE_ON_DISK_QUEUE(LogMarkerType.INFO),

	/**
	 * logs related to new signed state transfer queue used to notify
	 * com.swirlds.common.SwirldMain#newSignedState(SwirldState, Instant, long)} application method.
	 */
	NEW_SIGNED_STATE_EVENT_QUEUE(LogMarkerType.INFO),

	/**
	 * logs explicitly related to beta mirror node operation and warnings. Enabling this marker may generate large
	 * amounts of log output and is not recommended from production environments.
	 */
	BETA_MIRROR_NODE(LogMarkerType.INFO),

	/**
	 * log all platform status changes
	 */
	PLATFORM_STATUS(LogMarkerType.INFO),

	/**
	 * logs containing entire event content (metadata, transactions, etc). In order to prevent DoS and DDoS attacks
	 * from consuming excessive disk space we should only write event contents to the log when this diagnostic marker
	 * is enabled.
	 */
	EVENT_CONTENT(LogMarkerType.INFO);

	private LogMarkerType type;
	private Marker marker;

	LogMarker(LogMarkerType type) {
		this.type = type;
		this.marker = MarkerManager.getMarker(name());
	}

	public LogMarkerType getType() {
		return type;
	}

	public Marker getMarker() {
		return marker;
	}
}
