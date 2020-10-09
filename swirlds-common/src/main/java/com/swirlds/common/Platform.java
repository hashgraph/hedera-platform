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

package com.swirlds.common;


import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.events.Event;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.stream.Signer;

import javax.swing.JFrame;
import java.time.Instant;

/**
 * An interface for Swirlds Platform
 */
public interface Platform extends Signer {

	/**
	 * Add new entry to Application statistics
	 *
	 * @param newEntry
	 * 		the new entry
	 */
	void addAppStatEntry(final StatEntry newEntry);

	/**
	 * Registers a listener to be notified each time an invalid state signature is received from a remote peer.
	 *
	 * @param listener
	 * 		the listener to be registered
	 */
	void addSignedStateListener(final InvalidSignedStateListener listener);

	/**
	 * Initialize application statistics after adding entries
	 */
	void appStatInit();


	/**
	 * Create a new window with a text console, of the recommended size and location, including the Swirlds
	 * menu.
	 *
	 * @param visible
	 * 		should the window be initially visible? If not, call setVisible(true) later.
	 * @return the new window
	 */
	Console createConsole(boolean visible);

	/**
	 * The SwirldMain object calls this method when it wants to create a new transaction. The newly-created
	 * transaction is then embedded inside a newly-created event, and sent to all the other members of the
	 * community during syncs. It is also sent to the swirldState object.
	 * <p>
	 * If transactions are being created faster than they can be handled, then eventually a large backlog
	 * will build up. At that point, a call to createTransaction will return false, and will not actually
	 * create a transaction.
	 * <p>
	 * A transaction can be at most 1024 bytes. If trans.length &gt; 1024, then this will return false, and
	 * will not actually create a transaction.
	 * <p>
	 * WARNING: Do not add signatures to the {@link Transaction} here! Any signatures added will be silently
	 * ignored!
	 *
	 * @param trans
	 * 		the transaction to handle, encoded any way the swirld author chooses
	 * @return true if successful
	 */
	boolean createTransaction(Transaction trans);

	/**
	 * Create a new window of the recommended size and location, including the Swirlds menu.
	 *
	 * @param visible
	 * 		should the window be initially visible? If not, call setVisible(true) later.
	 * @return the new window
	 */
	JFrame createWindow(boolean visible);

	/**
	 * Find a rough estimate of what consensus timestamp a transaction would eventually have, if it were
	 * created right now through a call to createTransaction().
	 *
	 * A real-time app, such as a game, will typically redraw the screen by first calling estTime(), then
	 * rendering everything to the screen reflecting the predicted state as it will be at this time.
	 *
	 * @return the estimated time
	 */
	Instant estimateTime();

	/**
	 * returns the latest version of the "about" string from the app.
	 *
	 * @return the "about" string for this app
	 */

	String getAbout();

	/**
	 * Get the Address for the member running this Platform.
	 *
	 * @return the Address
	 */
	Address getAddress();

	/**
	 * Get the address for the member with the given ID
	 *
	 * @param id
	 * 		the member ID of the address to get
	 * @return the address or null if it doesn't exist
	 */
	Address getAddress(long id);

	/**
	 * Get an array of all the events in the hashgraph. This method is slow, so do not call it very often.
	 * The returned array is a shallow copy, so the caller may change it, and no other threads will change
	 * it. However, the events it references may have fields that are changed by other threads, and must not
	 * be changed by the caller. The array will contain first the consensus events (in consensus order),
	 * then the non-consensus events (sorted by time received).
	 *
	 * @return an array of all the events
	 */
	Event[] getAllEvents();

	/**
	 * Get the ApplicationStatistics object that has user-added statistics monitoring entries
	 *
	 * @return the ApplicationStatistics object associated with this platform
	 * @see ApplicationStatistics
	 */
	//ApplicationStatistics getAppStats();

	/**
	 * Return the sequence numbers of the last event created by each member. This is a copy, so it is OK for
	 * the caller to modify it.
	 *
	 * The returned array values may be slightly out of date, and different elements of the array may be out
	 * of date by different amounts. If it needs to be up to date, then call this method from inside a
	 * hashgraph.lock.lock("location") block.
	 *
	 * @return an array of sequence numbers indexed by member id number
	 */
	long[] getLastSeqByCreator();

	/**
	 * Get the speed of the last sync for a given node
	 *
	 * @param nodeIndex
	 * 		the index of the node
	 * @return the speed in bytes/second, or -1 if no speed is available
	 */
	double getLastSyncSpeed(int nodeIndex);

	/**
	 * Get the number of participating members. This is the size of the current address book.
	 *
	 * @return the number of members
	 */
	int getNumMembers();

	/**
	 * get any parameters that were given to the platform at startup, such as in the config.txt file.
	 *
	 * @return the parameters
	 */
	String[] getParameters();

	/**
	 * Get the ID of current node
	 *
	 * @return node ID
	 */
	NodeId getSelfId();

	/**
	 * Get the number of milliseconds the Platform should delay after each gossip sync it initiates. This is
	 * zero by default, but can be changed to slow down the system. This can be useful for testing.
	 *
	 * @return the delay in milliseconds
	 */
	long getSleepAfterSync();

	/**
	 * The SwirldMain object can call this to get the current state object. The SwirldMain should call this
	 * often, so it is always using the latest State object. Because the Platform will frequently change
	 * which state object is the "current" one.
	 * <p>
	 * The Platform will make sure that the state will not be deleted until the releaseState() method is
	 * called. Any other thread trying to access a state will be blocked until a state is released, so the
	 * operations on a state should be done quickly, and then it should be released.
	 * <p>
	 * The SwirldMain must ensure that every access to the State object is synchronized on that object. So
	 * either the State should be written with methods such as getters and setters marked as "synchronized",
	 * or the SwirldMain object should be written so that every time it reads from the state, it does so
	 * within a synchronized(...){...} block.
	 *
	 * @param <T>
	 * 		the type of the state object
	 * @return the current state
	 * @see #releaseState()
	 */

	<T extends SwirldState> T getState();

	/**
	 * Get the statistics of current node
	 *
	 * @return statistics of current node
	 */
	Statistics getStats();

	/**
	 * Get the ID of the current swirld. A given app can be used to create many different swirlds (also
	 * called networks, or ledgers, or shared worlds). This is a unique identifier for this particular
	 * swirld.
	 *
	 * @return a copy of the swirld ID
	 */
	byte[] getSwirldId();

	/**
	 * Get the transactionMaxBytes in Settings
	 *
	 * @return integer representing the maximum number of bytes allowed in a transaction
	 */
	static int getTransactionMaxBytes() {
		return SettingsCommon.transactionMaxBytes;
	}

	/**
	 * if it is a Mirror node
	 *
	 * @return true/false based on if this is mirror node
	 */
	boolean isMirrorNode();

	/**
	 * Indicates whether or not the stake configured in the {@link AddressBook} for this {@link
	 * Platform} instance is set to zero.
	 *
	 * @return true if this platform instance has zero stake assigned; otherwise, false if stake is greater than zero
	 */
	boolean isZeroStakeNode();

	/**
	 * This method releases a state returned by the getState() method. getState will block the thread until this method
	 * gets called.
	 */
	void releaseState();

	/**
	 * The SwirldMain calls this to set the string that is shown when the user chooses "About" from the
	 * Swirlds menu in the upper-right corner of the window. It is recommended that this be a short string
	 * that includes the name of the app, the version number, and the year.
	 *
	 * @param about
	 * 		what should show in the "about" window from the menu
	 */
	void setAbout(String about);

	/**
	 * Sets the period in which the platform will stop creating events and accepting transactions. This is used to
	 * safely shut down the platform for maintenance.
	 *
	 * @param startHour
	 * 		The start hour, a value between 0 and 23
	 * @param startMin
	 * 		The start minute, a value between 0 and 59
	 * @param endHour
	 * 		The end hour, a value between 0 and 23
	 * @param endMin
	 * 		The end minute, a value between 0 and 59
	 */
	void setFreezeTime(int startHour, int startMin, int endHour, int endMin);

	/**
	 * set the speed of the last sync for a given node
	 *
	 * @param nodeIndex
	 * 		the index of the node
	 * @param lastSyncSpeed
	 * 		the speed in bytes/second
	 */
	void setLastSyncSpeed(int nodeIndex, double lastSyncSpeed);

	/**
	 * Set the number of milliseconds the Platform should delay after each gossip sync it initiates. This is
	 * zero by default, but can be changed to slow down the system. This can be useful for testing.
	 *
	 * @param delay
	 * 		the delay in milliseconds
	 */
	void setSleepAfterSync(long delay);


	/**
	 * Gets the {@link Cryptography} instance attached to this platform. The provided instance is already configured
	 * with the settings defined for this {@link Platform}. The returned reference may be a single disposal instance or
	 * may be a singleton.
	 *
	 * @return a preconfigured cryptography instance
	 */
	Cryptography getCryptography();

	/**
	 * Digitally sign the data with the platforms signing private key. Return null if anything goes wrong.
	 *
	 * @param data
	 * 		the data to sign
	 * @return the signature (or null if any errors)
	 */
	@Override
	byte[] sign(byte[] data);

	/**
	 * @return consensusTimestamp of the last signed state
	 */
	Instant getLastSignedStateTimestamp();

}
