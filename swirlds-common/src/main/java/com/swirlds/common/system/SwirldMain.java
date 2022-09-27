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
package com.swirlds.common.system;

import com.swirlds.common.system.address.AddressBook;

import java.time.Instant;

/**
 * To implement a swirld, create a class that implements SwirldMain. Its constructor should have no
 * parameters, and its run() method should run until the user quits the swirld.
 */
public interface SwirldMain extends Runnable {

	/**
	 * This should only be called by the Platform. It is passed a reference to the platform, so the
	 * SwirldMain will know who to call. (This is dependency injection).
	 *
	 * SwirldApp can use {@link Platform#getState()} to access {@link SwirldState},
	 * but must not make any changes to {@link SwirldState} returned from {@link Platform#getState()}.
	 *
	 * Any changes necessary to initialize {@link SwirldState} should be made
	 * in {@link SwirldState#init(Platform, AddressBook, SwirldDualState, InitTrigger, SoftwareVersion)}
	 *
	 * @param platform
	 * 		the Platform that instantiated this SwirldMain
	 * @param selfId
	 * 		the ID number for this member (myself)
	 */
	void init(Platform platform, NodeId selfId);

	/**
	 * Close any windows not instantiated via Platform.create*. This method may be called on a thread that
	 * is not the Swing event thread.
	 *
	 * @param platform
	 * 		the Platform that instantiated this SwirldMain
	 * @param selfId
	 * 		the ID number for this member (myself)
	 */
	default void release(Platform platform, NodeId selfId) {
	}

	/**
	 * This is where the app manages the screen and I/O, and creates transactions as needed. It should
	 * return when the user quits the app, but may also return earlier.
	 */
	@Override
	void run();

	/**
	 * The platform calls this method on the SwirldMain just before creating an event. The SwirldMain can
	 * optionally create transactions here, knowing they will be sent out almost immediately.
	 * <p>
	 * Note: The platform will stop generating new events if there are no transactions in the network
	 * waiting to reach consensus. That means that an application, that creates transactions only in the
	 * preEvent() method, might come to a standstill since this method will never be called in this
	 * situation.
	 * <p>
	 * The run() method is given its own thread, which belongs to the SwirldMain for a particular app
	 * running a particular swirld. The preEvent() method runs in a special thread created by the platform
	 * each time it is called, and is limited to 0.1 seconds. If preEvent() does not return in 0.1 seconds,
	 * then the thread will be interrupted. So when writing preEvent(), make sure that it can always finish
	 * within 0.1 seconds, and that the separate threads of run() and preEvent() don't conflict in accessing
	 * non-threadsafe objects.
	 */
	void preEvent();

	/**
	 * Instantiate and return a SwirldState object that corresponds to this SwirldMain object. Typically, if
	 * class ExampleMain implements SwirldMain, then newState will return an object of class ExampleMain.
	 *
	 * @return the newly instantiated SwirldState object
	 */
	SwirldState newState();

	/**
	 * A method called by the Platform each time the status of the Platform changes. It is also called once
	 * when the app first starts up, after init() is called.
	 *
	 * @param newStatus
	 * 		the new status of the Platform
	 */
	void platformStatusChange(PlatformStatus newStatus);

	/**
	 * <p>
	 * Get the current software version.
	 * </p>
	 *
	 * <ul>
	 * <li>
	 * This version should not change except when a node is restarted.
	 * </li>
	 * <li>
	 * Every time a node restarts, the supplied version must be greater or equal to the previous version.
	 * </li>
	 * <li>
	 * Every supplied version for a particular app should have the same type. Failure to follow this
	 * restriction may lead to miscellaneous {@link ClassCastException}s.
	 * </li>
	 * </ul>
	 *
	 * @return the current version
	 */
	SoftwareVersion getSoftwareVersion();

	/**
	 * Called any time a new {@link SwirldState} is signed by nodes with more than 2/3 of the stake. If this method
	 * call is still ongoing while a new SwirldState is signed, it will be skipped until it has finished.
	 *
	 * @param state
	 * 		A fast copy of the {@link SwirldState} which was signed
	 * @param consensusTimeStamp
	 * 		The consensus timestamp of this state
	 * @param roundNumber
	 * 		The last consensus round handled by this state
	 */
	default void newSignedState(SwirldState state, Instant consensusTimeStamp, long roundNumber) {
	}
}
