/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.common.system;

import com.swirlds.common.Console;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.MetricConfig;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.PlatformEvent;
import com.swirlds.common.utility.AutoCloseableWrapper;
import java.time.Instant;
import javax.swing.JFrame;

/** An interface for Swirlds Platform */
public interface Platform extends Signer {

    /**
     * Checks if a {@link Metric} with the category and name as specified in the config-object
     * exists and returns it. If there is no such {@code Metric}, a new one is created and returned.
     *
     * @param config the configuration of the {@code Metric}
     * @param <T> class of the {@code Metric} that will be returned
     * @return the registered {@code Metric} (either existing or newly generated)
     * @throws IllegalArgumentException if {@code config} is {@code null}
     * @throws IllegalStateException if a {@code Metric} with the same category and name exists, but
     *     has a different type
     */
    <T extends Metric> T getOrCreateMetric(final MetricConfig<T, ?> config);

    /**
     * Create a new window with a text console, of the recommended size and location, including the
     * Swirlds menu.
     *
     * @param visible should the window be initially visible? If not, call setVisible(true) later.
     * @return the new window
     */
    Console createConsole(boolean visible);

    /**
     * The SwirldMain object calls this method when it wants to create a new transaction. The
     * newly-created transaction is then embedded inside a newly-created event, and sent to all the
     * other members of the community during syncs. It is also sent to the swirldState object.
     *
     * <p>If transactions are being created faster than they can be handled, then eventually a large
     * backlog will build up. At that point, a call to createTransaction will return false, and will
     * not actually create a transaction.
     *
     * <p>A transaction can be at most 1024 bytes. If trans.length &gt; 1024, then this will return
     * false, and will not actually create a transaction.
     *
     * @param trans the transaction to handle, encoded any way the swirld author chooses
     * @return true if successful
     */
    boolean createTransaction(byte[] trans);

    /**
     * Create a new window of the recommended size and location, including the Swirlds menu.
     *
     * @param visible should the window be initially visible? If not, call setVisible(true) later.
     * @return the new window
     */
    JFrame createWindow(boolean visible);

    /**
     * Find a rough estimate of what consensus timestamp a transaction would eventually have, if it
     * were created right now through a call to createTransaction().
     *
     * <p>A real-time app, such as a game, will typically redraw the screen by first calling
     * estTime(), then rendering everything to the screen reflecting the predicted state as it will
     * be at this time.
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
     * @param id the member ID of the address to get
     * @return the address or null if it doesn't exist
     */
    Address getAddress(long id);

    /**
     * Get an array of all the events in the hashgraph. This method is slow, so do not call it very
     * often. The returned array is a shallow copy, so the caller may change it, and no other
     * threads will change it. However, the events it references may have fields that are changed by
     * other threads, and must not be changed by the caller. The array will contain first the
     * consensus events (in consensus order), then the non-consensus events (sorted by time
     * received).
     *
     * @return an array of all the events
     * @deprecated to be removed in a future release
     */
    @Deprecated
    PlatformEvent[] getAllEvents();

    /**
     * get the highest generation number of all events with the given creator
     *
     * @param creatorID the ID of the node in question
     * @return the highest generation number known stored for the given creator ID
     */
    long getLastGen(long creatorID);

    /**
     * Get the number of participating members. This is the size of the current address book.
     *
     * @return the number of members
     */
    int getNumMembers();

    /**
     * get any parameters that were given to the platform at startup, such as in the config.txt
     * file.
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
     * Get the number of milliseconds the Platform should delay after each gossip sync it initiates.
     * This is zero by default, but can be changed to slow down the system. This can be useful for
     * testing.
     *
     * @return the delay in milliseconds
     */
    long getSleepAfterSync();

    /**
     * The SwirldMain object can call this to get the current state object. The SwirldMain should
     * call this often, so it is always using the latest State object. Because the Platform will
     * frequently change which state object is the "current" one.
     *
     * <p>The Platform will make sure that the state will not be deleted until the releaseState()
     * method is called. Any other thread trying to access a state will be blocked until a state is
     * released, so the operations on a state should be done quickly, and then it should be
     * released.
     *
     * <p>The SwirldMain must ensure that every access to the State object is synchronized on that
     * object. So either the State should be written with methods such as getters and setters marked
     * as "synchronized", or the SwirldMain object should be written so that every time it reads
     * from the state, it does so within a synchronized(...){...} block.
     *
     * @param <T> the type of the state object
     * @return the current state
     * @see #releaseState()
     */
    <T extends SwirldState> T getState();

    /**
     * Get a reference to the metrics-system of the current node
     *
     * @return the reference to the metrics-system
     */
    Metrics getMetrics();

    /**
     * Get the ID of the current swirld. A given app can be used to create many different swirlds
     * (also called networks, or ledgers, or shared worlds). This is a unique identifier for this
     * particular swirld.
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
     * @return true if this platform instance has zero stake assigned; otherwise, false if stake is
     *     greater than zero
     */
    boolean isZeroStakeNode();

    /**
     * This method releases a state returned by the getState() method. getState will block the
     * thread until this method gets called.
     */
    void releaseState();

    /**
     * The SwirldMain calls this to set the string that is shown when the user chooses "About" from
     * the Swirlds menu in the upper-right corner of the window. It is recommended that this be a
     * short string that includes the name of the app, the version number, and the year.
     *
     * @param about what should show in the "about" window from the menu
     */
    void setAbout(String about);

    /**
     * Set the number of milliseconds the Platform should delay after each gossip sync it initiates.
     * This is zero by default, but can be changed to slow down the system. This can be useful for
     * testing.
     *
     * @param delay the delay in milliseconds
     */
    void setSleepAfterSync(long delay);

    /**
     * Gets the {@link Cryptography} instance attached to this platform. The provided instance is
     * already configured with the settings defined for this {@link Platform}. The returned
     * reference may be a single disposal instance or may be a singleton.
     *
     * @return a preconfigured cryptography instance
     */
    Cryptography getCryptography();

    /**
     * Digitally sign the data with the platforms signing private key. Return null if anything goes
     * wrong.
     *
     * @param data the data to sign
     * @return the signature (or null if any errors)
     */
    @Override
    Signature sign(byte[] data);

    /**
     * @return consensusTimestamp of the last signed state
     */
    Instant getLastSignedStateTimestamp();

    /**
     * Returns the latest signed {#link SwirldState} signed by members with more than 1/3 of total
     * stake.
     *
     * <p>The {#link SwirldState} is returned in a {#link AutoCloseableWrapper} that <b>must</b> be
     * use with a try-with-resources.
     *
     * @param <T> A type extending from {#link SwirldState}
     * @return the latest complete signed swirld state, or null if none are complete
     */
    <T extends SwirldState> AutoCloseableWrapper<T> getLastCompleteSwirldState();

    /**
     * @return true if state recovery is in progress
     */
    boolean isStateRecoveryInProgress();
}
