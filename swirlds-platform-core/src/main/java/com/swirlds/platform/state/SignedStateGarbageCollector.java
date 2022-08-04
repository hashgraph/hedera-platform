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

package com.swirlds.platform.state;

import com.swirlds.platform.stats.SignedStateStats;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STATE_DELETER;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS;

public class SignedStateGarbageCollector implements Runnable {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	public static final int DELETION_QUEUE_SIZE = 200;

	private final LinkedBlockingQueue<SignedState> deletionQueue;
	private final LinkedBlockingQueue<SignedState> archivalQueue;
	private final Supplier<SignedStateStats> statsSupplier;
	private final StateSettings settings;

	/**
	 * The amount of time to sleep after attempting to delete/archive all requested states.
	 */
	private final int sleepMillis = 10;

	private volatile boolean alive;

	public SignedStateGarbageCollector(
			final Supplier<SignedStateStats> statsSupplier,
			final StateSettings settings) {
		this.statsSupplier = statsSupplier;
		this.settings = settings;
		deletionQueue = new LinkedBlockingQueue<>(DELETION_QUEUE_SIZE);
		archivalQueue = new LinkedBlockingQueue<>();
		alive = true;
	}

	public void deleteBackground(SignedState ss) {
		try {
			deletionQueue.put(ss);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		statsSupplier.get().updateDeletionQueue(deletionQueue.size());
	}

	public void archiveBackground(SignedState ss) {
		try {
			archivalQueue.put(ss);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		statsSupplier.get().updateArchivalQueue(archivalQueue.size());
	}

	public int getQueueSize() {
		return deletionQueue.size();
	}

	/**
	 * Stop the garbage collector thread if it is still running.
	 */
	public void kill() {
		alive = false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run() {
		try {
			while (alive) {

				boolean deletePerformed = processDeletionQueue();

				boolean archivePerformed = processArchivalQueue();

				boolean shouldSleep = !(deletePerformed && deletionQueue.size() > 0) &&
						!(archivePerformed && archivalQueue.size() > 0);

				if (shouldSleep) {
					try {
						Thread.sleep(sleepMillis);
					} catch (InterruptedException e) {
						log.warn(TESTING_EXCEPTIONS.getMarker(), "Thread Interrupted", e);
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
		} catch (final Exception ex) {
			log.error(EXCEPTION.getMarker(), "exception in SignedStateGarbageCollector", ex);
		}
	}

	private boolean processArchivalQueue() {
		StopWatch watch;
		boolean archivePerformed = false;
		Iterator<SignedState> archivalIterator = archivalQueue.iterator();
		while (archivalIterator.hasNext()) {
			watch = new StopWatch();
			watch.start();

			if (archivalIterator.next().tryArchive()) {
				archivalIterator.remove();
				archivePerformed = true;

				watch.stop();
				statsSupplier.get().updateArchivalTime(watch.getTime(TimeUnit.MICROSECONDS));
				statsSupplier.get().updateArchivalQueue(archivalQueue.size());
			}
		}
		return archivePerformed;
	}

	private boolean processDeletionQueue() {
		StopWatch watch;
		boolean deletePerformed = false;
		Iterator<SignedState> deletionIterator = deletionQueue.iterator();
		while (deletionIterator.hasNext()) {
			SignedState forDelete = deletionIterator.next();
			log.info(STATE_DELETER.getMarker(), " About to tryDelete signed state for round: {}",
					forDelete.getLastRoundReceived());

			watch = new StopWatch();
			watch.start();

			if (forDelete.tryDelete()) {
				log.info(STATE_DELETER.getMarker(), " Successfully deleted signed state for round: {}",
						forDelete.getLastRoundReceived());
				deletionIterator.remove();
				deletePerformed = true;

				watch.stop();
				statsSupplier.get().updateDeletionTime(watch.getTime(TimeUnit.MICROSECONDS));
				statsSupplier.get().updateDeletionQueue(deletionQueue.size());
			}
		}
		return deletePerformed;
	}
}
