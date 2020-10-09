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
