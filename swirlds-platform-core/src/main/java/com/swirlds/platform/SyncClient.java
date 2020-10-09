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


import com.swirlds.common.NodeId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicReferenceArray;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * This class remembers connections to multiple members. It connects to servers when requested. The get()
 * methods return the socket or stream for a given member, given their index in the address book. If that
 * connection doesn't yet exist, it creates it.
 * <p>
 * This member will instantiate an separate instance of SyncConnection for the connection to each other
 * member.
 */
class SyncClient {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	/** the platform object that uses these connections */
	private final AbstractPlatform platform;
	/** the number of members in the community */
	private final int numMembers;
	/** a separate SyncConnection for the connection to each other member. Some may be null. */
	private final AtomicReferenceArray<SyncConnection> callerConn;

	/**
	 * The client that has a method to create new connections to the server.
	 *
	 * @param platform
	 * 		the platform that is using this
	 */
	SyncClient(AbstractPlatform platform) {
		this.platform = platform;
		this.numMembers = platform.getNumMembers();
		this.callerConn = new AtomicReferenceArray<>(numMembers);
	}

	/**
	 * Return the total number of bytes written to all the caller's connections since the last time this was
	 * called.
	 *
	 * @return the number of bytes written
	 */
	long getBytesWrittenSinceLast() {
		return SyncConnection.getBytesWrittenSinceLast(platform, callerConn);
	}

	/**
	 * Returns the SyncConnection to the member with the given ID. If the connection isn't currently
	 * working, then it will try just once to connect to it. Returns null if there shouldn't be a connection
	 * to that member.
	 *
	 * @param otherConnId
	 * 		the connection ID of the member to get the connection to
	 * @return the connection (or null if none)
	 */
	SyncConnection getCallerConnOrConnectOnce(NodeId otherConnId) {
		SyncConnection connection;
		NodeId selfConnId = platform.getSelfConnectionId();
		if (selfConnId.sameNetwork(otherConnId) &&
				!platform.getConnectionGraph().isAdjacent(selfConnId.getIdAsInt(), otherConnId.getIdAsInt())) {
			log.error(EXCEPTION.getMarker(),
					"ERROR: {} tried to connect to {}, but they should never connect",
					platform.getSelfConnectionId(), otherConnId);
			return null;
		}
		connection = callerConn.get(otherConnId.getIdAsInt());
		boolean connected = (connection != null && connection.connected());
		if (!connected) {
			// only try once. This may take a while.
			connection = SyncConnection.connect(platform, platform.getSelfId(),
					otherConnId);
			callerConn.set(otherConnId.getIdAsInt(), connection);
			if (connection != null) {
				platform.getSyncServer().connsCreated.incrementAndGet(); // count new connections
			}
		}
		return connection;
	}

	/**
	 * Returns the SyncConnection to the member with the given ID. If the connection isn't currently
	 * working, then it will NOT try to connect to it.
	 *
	 * @param id
	 * 		the ID of the member to connect to
	 * @return the connection
	 */
	SyncConnection getExistingCallerConn(int id) {
		return callerConn.get(id);
	}
}
