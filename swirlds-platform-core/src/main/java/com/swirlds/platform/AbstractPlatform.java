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

import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.InvalidNodeIdException;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldMain;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.events.Event;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.state.SignedStateManager;

import java.time.Instant;

public abstract class AbstractPlatform implements Platform {
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
	 * This is called just before an event is created, to give the Platform a chance to create any system
	 * transactions that should be sent out immediately. It is similar to SwirldMain.preEvent, except that
	 * the "app" is the Platform itself, and it will pass "true" for it being a system transaction.
	 */
	abstract void preEvent();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract byte[] sign(byte[] data);

	/** used to calculate Java hashes for HashMap keys that are cryptographic hashes */
	abstract int getHashMapSeed();

	/**
	 * get the FreezeManager used by this platform
	 *
	 * @return The FreezeManager used by this platform
	 */
	public abstract FreezeManager getFreezeManager();

	/**
	 * Returns the SignedStateFileManager used by this platform to manage signed state on disk
	 *
	 * @return the SignedStateFileManager
	 */
	abstract SignedStateFileManager getSignedStateFileManager();

	/**
	 * All system transactions are handled by calling this method. Platform.handleTransactions is the
	 * equivalent of SwirldMain.handleTransaction, except that the "app" is the Platform itself.
	 * <p>
	 * Every system transaction is sent here twice, first with consensus being false, and again with
	 * consensus being true. In other words, the platform will act like an app that implements SwirldState2
	 * rather than an app implementing SwirldState.
	 * <p>
	 * This method is only called by the doCons thread in EventFlow. But it could have deadlock problems if
	 * it tried to get a lock on Platform or Hashgraph while, for example, one of the syncing threads is
	 * holding those locks and waiting for the forCons queue to not be full. So if this method is changed,
	 * it should avoid that.
	 *
	 * @param creator
	 * 		the ID number of the member who created this transaction
	 * @param isConsensus
	 * 		is this transaction's timeCreated and position in history part of the consensus?
	 * @param timeCreated
	 * 		the time when this transaction was first created and sent to the network, as claimed by
	 * 		the member that created it (which might be dishonest or mistaken)
	 * @param timestamp
	 * 		the consensus timestamp for when this transaction happened (or an estimate of it, if it
	 * 		hasn't reached consensus yet)
	 * @param trans
	 * 		the transaction to handle, encoded any way the swirld app author chooses
	 * @param address
	 * 		this transaction is a request by member "id" to create a new member with this address
	 */
	abstract void handleSystemTransaction(long creator, boolean isConsensus,
			Instant timeCreated, Instant timestamp, Transaction trans,
			Address address);

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
	 */
	public abstract long getRoundOfLastRecoveredEvent();

	/**
	 * get the hashgraph used by this platform
	 *
	 * @return the hashgraph used by this platform
	 */
	public abstract Hashgraph getHashgraph();

	/**
	 * Return the sync server used by this platform.
	 */
	abstract SyncServer getSyncServer();

	/**
	 * @return the SyncManager used by this platform
	 */
	abstract SyncManager getSyncManager();

	/**
	 * Get the shadow graph and tip Event set used by this platform
	 *
	 * @return the SyncShadowGraphManager used by this platform
	 */
	abstract SyncShadowGraphManager getSyncShadowGraphManager();

	/**
	 * @return the connection ID for this platform
	 */
	abstract NodeId getSelfConnectionId();

	/**
	 * @return the signed state manager for this platform
	 */
	abstract SignedStateManager getSignedStateManager();

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
	 * Looks for a saved state on disk and loads the latest one if any are found. This should be called before the
	 * platform has started
	 *
	 * @return whether a saved state has been loaded from Disk
	 */
	abstract boolean loadSavedStateFromDisk() throws SignedStateLoadingException;

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
	 * @param trans
	 * 		the new system transaction to be included in a future Event
	 * @return true if successful, false otherwise
	 */
	public abstract boolean createSystemTransaction(byte[] trans);

	/**
	 * Record a statistics value in the Statistics object
	 *
	 * @param statsType
	 * @param value
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
		return isZeroStakeNode(getAddressBook(), nodeId);
	}

	/**
	 * Indicates whether or not the stake configured in the {@link AddressBook} for this {@link
	 * Platform} instance is set to zero.
	 *
	 * @param addressBook
	 * 		the current platform address book
	 * @param nodeId
	 * 		the node identifier
	 * @return true if this Platform has zero stake assigned; otherwise, false if stake is greater than zero
	 * @throws InvalidNodeIdException
	 * 		if the specified {@code nodeId} is invalid
	 */
	public static boolean isZeroStakeNode(final AddressBook addressBook, final long nodeId) {
		final Address nodeAddress = addressBook.getAddress(nodeId);

		if (nodeAddress == null) {
			throw new InvalidNodeIdException("NodeId may not be null");
		}

		return nodeAddress.getStake() == 0;
	}

	/**
	 * @return the instance for calculating RunningHash of all consensus Events
	 */
	abstract RunningHashCalculator getRunningHashCalculator();
}
