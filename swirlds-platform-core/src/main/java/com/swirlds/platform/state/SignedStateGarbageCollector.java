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

package com.swirlds.platform.state;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;

import static com.swirlds.logging.LogMarker.STATE_DELETER;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS;

public class SignedStateGarbageCollector implements Runnable {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	private static final int QUEUE_SIZE = 20;

	private LinkedBlockingQueue<SignedState> deletionQueue;
	private LinkedBlockingQueue<SignedState> archivalQueue;

	/**
	 * The amount of time to sleep after attempting to delete/archive all requested states.
	 */
	private final int sleepMillis = 10;

	private volatile boolean alive;

	public SignedStateGarbageCollector() {
		deletionQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
		archivalQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
		alive = true;
	}

	public void deleteBackground(SignedState ss) {
		try {
			deletionQueue.put(ss);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public void archiveBackground(SignedState ss) {
		try {
			archivalQueue.put(ss);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
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
		while (alive) {

			boolean deletePerformed = false;
			Iterator<SignedState> deletionIterator = deletionQueue.iterator();
			while (deletionIterator.hasNext()) {
				SignedState forDelete = deletionIterator.next();
				log.info(STATE_DELETER.getMarker(), " About to tryDelete signed state for round: {}",
						forDelete.getLastRoundReceived());
				if (forDelete.tryDelete()) {
					log.info(STATE_DELETER.getMarker(), " Successfully deleted signed state for round: {}",
							forDelete.getLastRoundReceived());
					deletionIterator.remove();
					deletePerformed = true;

					forDelete.getLeaf().compareSnapshot();
				}
			}

			boolean archivePerformed = false;
			Iterator<SignedState> archivalIterator = archivalQueue.iterator();
			while (archivalIterator.hasNext()) {
				if (archivalIterator.next().tryArchive()) {
					archivalIterator.remove();
					archivePerformed = true;
				}
			}

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
	}
}
