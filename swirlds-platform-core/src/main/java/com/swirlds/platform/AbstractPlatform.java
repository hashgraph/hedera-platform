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

package com.swirlds.platform;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.exceptions.InvalidNodeIdException;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.EventTaskCreator;
import com.swirlds.platform.components.SwirldMainManager;
import com.swirlds.platform.components.TransactionTracker;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.PreConsensusEventHandler;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.unidirectional.SharedConnectionLocks;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.sync.ShadowGraph;
import com.swirlds.platform.sync.ShadowGraphSynchronizer;
import com.swirlds.platform.sync.SimultaneousSyncThrottle;

public abstract class AbstractPlatform implements Platform, SwirldMainManager, ConnectionTracker {
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
	public abstract String getSwirldName();

	/**
	 * Returns the name of the main class this platform is running
	 *
	 * @return the main class name
	 */
	public abstract String getMainClassName();

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
	 * @return the signed state manager for this platform
	 */
	abstract SignedStateManager getSignedStateManager();

	/**
	 * @return locks used to synchronize usage of outbound connections
	 */
	abstract SharedConnectionLocks getSharedConnectionLocks();

	/**
	 * @return the cryto object for this platform
	 */
	abstract Crypto getCrypto();

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
	 * Get the ApplicationStatistics object that has user-added statistics monitoring entries
	 *
	 * @return the ApplicationStatistics object associated with this platform
	 * @see ApplicationStatistics
	 */
	public abstract ApplicationStatistics getAppStats();

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
	public abstract boolean createSystemTransaction(final SystemTransaction systemTransaction);

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
		return getAddressBook().getAddress(nodeId).isZeroStake();
	}

	/**
	 * @return the instance for calculating runningHash and writing event stream files
	 */
	abstract EventStreamManager<EventImpl> getEventStreamManager();

	/**
	 * Loads the signed state data into consensus and event mapper
	 *
	 * @param signedState
	 * 		the state to get the data from
	 */
	abstract void loadIntoConsensusAndEventMapper(final SignedState signedState);

	/**
	 * @return the platform instance of the {@link ShadowGraphSynchronizer}
	 */
	public abstract ShadowGraphSynchronizer getShadowGraphSynchronizer();

	/**
	 * @return the instance that throttles simultaneous syncs
	 */
	public abstract SimultaneousSyncThrottle getSimultaneousSyncThrottle();

	/**
	 * @return the instance that manages interactions with the {@link SwirldState}
	 */
	public abstract SwirldStateManager getSwirldStateManager();

	/**
	 * @return the handler that applies consensus events to state and creates signed states
	 */
	public abstract ConsensusRoundHandler getConsensusHandler();

	/**
	 * @return the handler that applies pre-consensus events to state
	 */
	public abstract PreConsensusEventHandler getPreConsensusHandler();
}
