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

package com.swirlds.platform.reconnect;

import com.swirlds.common.threading.locks.LockedResource;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.Connection;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.SystemExitReason;
import com.swirlds.platform.system.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Semaphore;

import static com.swirlds.logging.LogMarker.RECONNECT;

/**
 * Responsible for executing the whole reconnect process
 */
public class ReconnectController implements Runnable {
	private static final Logger LOG = LogManager.getLogger();
	private static final int FAILED_RECONNECT_SLEEP_MILLIS = 1000;

	private final ReconnectHelper helper;
	private final Semaphore threadRunning;
	private final BlockingResourceProvider<Connection> connectionProvider;
	private final Runnable startChatter;

	/**
	 * @param helper
	 * 		executes phases of a reconnect
	 * @param startChatter
	 * 		starts chatter if previously suspended
	 */
	public ReconnectController(final ReconnectHelper helper, final Runnable startChatter) {
		this.helper = helper;
		this.startChatter = startChatter;
		this.threadRunning = new Semaphore(1);
		this.connectionProvider = new BlockingResourceProvider<>();
	}

	/**
	 * Starts the reconnect controller thread if it's not already running
	 */
	public void start() {
		if (!threadRunning.tryAcquire()) {
			LOG.error(LogMarker.EXCEPTION.getMarker(),
					"Attempting to start reconnect controller while its already running");
			return;
		}
		LOG.error(LogMarker.RECONNECT.getMarker(), "Starting ReconnectController");
		new Thread(this).start();
	}

	@Override
	public void run() {
		try {
			// the ReconnectHelper uses a ReconnectLearnerThrottle to exit if there are too many failed attempts
			// so in this thread we can just try until it succeeds or the throttle kicks in
			while (!executeReconnect()) {
				LOG.error(LogMarker.RECONNECT.getMarker(), "Reconnect failed, retrying");
				Thread.sleep(FAILED_RECONNECT_SLEEP_MILLIS);
			}
		} catch (final RuntimeException | InterruptedException e) {
			LOG.error(LogMarker.EXCEPTION.getMarker(), "Unexpected error occurred while reconnecting", e);
			SystemUtils.exitSystem(SystemExitReason.RECONNECT_FAILURE);
		} finally {
			threadRunning.release();
		}
	}

	private boolean executeReconnect() throws InterruptedException {
		helper.prepareForReconnect();

		final SignedState signedState;
		LOG.info(RECONNECT.getMarker(), "waiting for reconnect connection");
		try (final LockedResource<Connection> connection = connectionProvider.waitForResource()) {
			LOG.info(RECONNECT.getMarker(), "acquired reconnect connection");
			signedState = helper.receiveSignedState(connection.getResource());
		} catch (final RuntimeException e) {
			LOG.info(RECONNECT.getMarker(), "receiving signed state failed", e);
			return false;
		}
		if (!helper.loadSignedState(signedState)) {
			return false;
		}
		startChatter.run();
		return true;
	}

	/**
	 * Try to acquire a permit for negotiate a reconnect in the role of the learner
	 *
	 * @return true if the permit has been acquired
	 */
	public boolean acquireLearnerPermit() {
		return connectionProvider.acquireProvidePermit();
	}

	/**
	 * Releases a previously acquired permit for reconnect
	 */
	public void cancelLearnerPermit() {
		connectionProvider.releaseProvidePermit();
	}

	/**
	 * Provides a connection over which a reconnect learner has been already negotiated. This method should only be
	 * called if {@link #acquireLearnerPermit()} has returned true previously. This method blocks until the reconnect is
	 * done.
	 *
	 * @param connection
	 * 		the connection to use to execute the reconnect learner protocol
	 * @throws InterruptedException
	 * 		if the calling thread is interrupted while the connection is being used
	 */
	public void provideLearnerConnection(final Connection connection) throws InterruptedException {
		connectionProvider.provide(connection);
	}
}
