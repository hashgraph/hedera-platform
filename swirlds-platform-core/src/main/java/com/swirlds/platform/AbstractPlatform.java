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

package com.swirlds.platform;

import com.swirlds.common.AddressBook;
import com.swirlds.common.InvalidNodeIdException;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldMain;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.threading.QueueThread;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.EventTaskCreator;
import com.swirlds.platform.components.SwirldMainManager;
import com.swirlds.platform.components.SystemTransactionHandler;
import com.swirlds.platform.components.TransactionTracker;
import com.swirlds.platform.crypto.SocketFactory;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.SignedStateManager;
import com.swirlds.platform.sync.ShadowGraph;
import com.swirlds.platform.sync.ShadowGraphSynchronizer;

public abstract class AbstractPlatform implements Platform, SwirldMainManager {
	/**
	 * Returns the ID of the member running this Platform. This value will never change for the Platform
	 * object.
	 *
	 * @return the ID of the member running this Platform
	 */
	@Override
	public abstract NodeId getSelfId();

	/**
	 * check if this platform is a mirror node
	 *
	 * @return true if this platform is a mirror node, false otherwise
	 */
	@Override
	public abstract boolean isMirrorNode();

	/**
	 * Get the name of the current swirld. A given app can be used to create many different swirlds (also
	 * called networks, or ledgers, or shared worlds). This is a unique identifier for this particular
	 * swirld.
	 *
	 * @return the name of the swirld
	 */
	abstract String getSwirldName();

	/**
	 * Returns the name of the main class this platform is running
	 *
	 * @return the main class name
	 */
	abstract String getMainClassName();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract byte[] sign(byte[] data);

	/**
	 * get the FreezeManager used by this platform
	 *
	 * @return The FreezeManager used by this platform
	 */
	public abstract FreezeManager getFreezeManager();

	/**
	 * get the StartUpEventFrozenManager used by this platform
	 *
	 * @return The StartUpEventFrozenManager used by this platform
	 */
	abstract StartUpEventFrozenManager getStartUpEventFrozenManager();

	/**
	 * Returns the SignedStateFileManager used by this platform to manage signed state on disk
	 *
	 * @return the SignedStateFileManager
	 */
	abstract SignedStateFileManager getSignedStateFileManager();

	/**
	 * @return The EventFlow object used by this platform
	 */
	abstract AbstractEventFlow getEventFlow();

	/**
	 * Get the Statistics object that monitors and reports on the network and syncing.
	 *
	 * @return the Statistics object associated with this Platform.
	 */
	@Override
	public abstract Statistics getStats();

	/**
	 * Get the round number of the last event recovered from event stream file
	 *
	 * @return the round number of the last event recovered from event stream file
	 */
	public abstract long getRoundOfLastRecoveredEvent();

	/**
	 * @return the instance responsible for creating event tasks
	 */
	abstract EventTaskCreator getEventTaskCreator();

	abstract EventMapper getEventMapper();

	abstract QueueThread<EventIntakeTask> getIntakeQueue();

	/**
	 * @return the consensus object used by this platform
	 */
	abstract Consensus getConsensus();

	/**
	 * @return the object that tracks recent events created
	 */
	abstract CriticalQuorum getCriticalQuorum();

	/**
	 * @return the object that tracks user transactions in the hashgraph
	 */
	abstract TransactionTracker getTransactionTracker();

	/**
	 * Return the sync server used by this platform.
	 */
	abstract SyncServer getSyncServer();

	/**
	 * @return the SyncManager used by this platform
	 */
	abstract SyncManagerImpl getSyncManager();

	/**
	 * Get the shadow graph used by this platform
	 *
	 * @return the {@link ShadowGraph} used by this platform
	 */
	abstract ShadowGraph getShadowGraph();

	/**
	 * @return the connection ID for this platform
	 */
	abstract NodeId getSelfConnectionId();

	/**
	 * @return the signed state manager for this platform
	 */
	abstract SignedStateManager getSignedStateManager();

	/**
	 * @return the system transaction handler for this platform
	 */
	abstract SystemTransactionHandler getSystemTransactionHandler();

	/**
	 * @return the sync client for this platform
	 */
	abstract SyncClient getSyncClient();

	/**
	 * @return the cryto object for this platform
	 */
	abstract Crypto getCrypto();

	/**
	 * @return the connection graph of all the connections in the network
	 */
	abstract RandomGraph getConnectionGraph();

	/**
	 * Store the infoMember that has metadata about this (app,swirld,member) triplet. This also creates the
	 * long name that will be returned by getPlatformName.
	 *
	 * @param infoMember
	 * 		the metadata
	 */
	abstract void setInfoMember(StateHierarchy.InfoMember infoMember);

	/**
	 * Start the platform, which will in turn start the app and all the syncing threads. When using the
	 * normal browser, only one Platform is running. But with config.txt, multiple can be running.
	 */
	abstract void run();

	/**
	 * returns a name for this Platform, which includes the app, the swirld, the member id, and the member
	 * name. This is useful in the Browser window for showing data about each of the running Platform
	 * objects.
	 *
	 * @return the name for this Platform
	 */
	abstract String getPlatformName();

	/**
	 * Checks the status of the platform and notifies the SwirldMain if there is a change in status
	 */
	abstract void checkPlatformStatus();

	/**
	 * Notifies the platform that a connection has been closed
	 */
	abstract void connectionClosed();

	/**
	 * Notifies the platform that a new connection has been opened
	 */
	abstract void newConnectionOpened();

	abstract boolean displayDBStats();

	/**
	 * Get the ApplicationStatistics object that has user-added statistics monitoring entries
	 *
	 * @return the ApplicationStatistics object associated with this platform
	 * @see ApplicationStatistics
	 */
	public abstract ApplicationStatistics getAppStats();

	/**
	 * get database statistics
	 *
	 * @return DBStatistics
	 */
	abstract DBStatistics getDBStatistics();

	public abstract SwirldMain getAppMain();

	/**
	 * Stores a new system transaction that will be added to an event in the future. This is currently
	 * called by SignedStateMgr.newSelfSigned() to create the system transaction in which self signs a new
	 * signed state.
	 *
	 * @param systemTransaction
	 * 		the new system transaction to be included in a future Event
	 * @return true if successful, false otherwise
	 */
	public abstract boolean createSystemTransaction(final Transaction systemTransaction);

	/**
	 * Record a statistics value in the Statistics object
	 *
	 * @param statsType
	 * 		type of this stats
	 * @param value
	 * 		value to be recorded
	 */
	public abstract void recordStatsValue(SwirldsPlatform.StatsType statsType, double value);

	/**
	 * Get the Address Book
	 *
	 * @return AddressBook
	 */
	public abstract AddressBook getAddressBook();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract Cryptography getCryptography();

	/**
	 * {@inheritDoc}
	 */
	public boolean isZeroStakeNode() {
		return isZeroStakeNode(getSelfId().getId());
	}

	/**
	 * Indicates whether or not the stake configured in the {@link AddressBook} for this {@link
	 * Platform} instance is set to zero.
	 *
	 * @param nodeId
	 * 		the node identifier
	 * @return true if this Platform has zero stake assigned; otherwise, false if stake is greater than zero
	 * @throws InvalidNodeIdException
	 * 		if the specified {@code nodeId} is invalid
	 */
	public boolean isZeroStakeNode(final long nodeId) {
		return getAddressBook().isZeroStakeNode(nodeId);
	}

	/**
	 * @return the instance for calculating runningHash and writing event stream files
	 */
	abstract EventStreamManager<EventImpl> getEventStreamManager();

	/**
	 * Loads the signed state data into consensus and event intake
	 *
	 * @param signedState
	 * 		the state to get the data from
	 */
	abstract void loadIntoConsensusAndEventIntake(SignedState signedState) throws SignedStateLoadingException;

	/**
	 * Sets event creation to be frozen, platform's status will be MAINTENANCE after this method is called
	 */
	public abstract void enterMaintenance();

	/**
	 * @return the platform instance of the {@link ShadowGraphSynchronizer}
	 */
	public abstract ShadowGraphSynchronizer getShadographSynchronizer();

	/**
	 * @return the instance that creates and connects sockets
	 */
	public abstract SocketFactory getSocketFactory();

}
