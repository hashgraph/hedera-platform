/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.fchashmap.internal;

import com.swirlds.common.threading.QueueThread;
import com.swirlds.common.threading.QueueThreadConfiguration;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.fchashmap.FCHashMapSettingsFactory;
import com.swirlds.logging.payloads.GarbageCollectionQueuePayload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * This thread performs garbage collection on an FCHashMap in a background thread.
 */
public class FCHashMapGarbageCollector<K, V> {

	private static final Logger log = LogManager.getLogger();

	/**
	 * A reference to the internal data structure of the FCHashMap copies.
	 */
	private final ConcurrentMap<K, MutationQueue<V>> data;

	private final AtomicInteger referenceCount;

	private final QueueThread<FCHashMap<K, V>> workQueue;

	/**
	 * The size of the transfer buffer for the queue thread.
	 */
	private static final int QUEUE_BUFFER_SIZE = 10;

	/**
	 * Contains a sequence of events that eventually require cleanup
	 */
	private final ConcurrentLinkedDeque<GarbageCollectionEvent<K, V>> garbageCollectionEvents;

	public FCHashMapGarbageCollector(final ConcurrentMap<K, MutationQueue<V>> data) {
		this.data = data;
		this.garbageCollectionEvents = new ConcurrentLinkedDeque<>();
		this.referenceCount = new AtomicInteger(1);

		final int maximumQueueSize = FCHashMapSettingsFactory.get().getMaximumGCQueueSize();
		final Duration thresholdPeriod = FCHashMapSettingsFactory.get().getGCQueueThresholdPeriod();

		this.workQueue = new QueueThreadConfiguration<FCHashMap<K, V>>()
				.setComponent("FCHashMap")
				.setThreadName("garbage-collector")
				.setHandler(this::handler)
				.setMaxBufferSize(QUEUE_BUFFER_SIZE)
				.setUnlimitedCapacity()
				.addThreshold(
						size -> size > maximumQueueSize,
						size -> log.error(EXCEPTION.getMarker(), new GarbageCollectionQueuePayload(size)),
						thresholdPeriod)
				.build();
	}

	/**
	 * Start the garbage collection thread.
	 */
	public void start() {
		workQueue.start();
	}

	/**
	 * This should be called every time an FCHashMap is deleted. When the reference count reaches 0 the garbage
	 * collection thread terminates.
	 */
	public void decrementReferenceCount() {
		referenceCount.getAndDecrement();
		if (referenceCount.get() == 0) {
			workQueue.stop();
		}
	}

	/**
	 * The garbage collector must be given a copy of each new immutable FCHashMap that is created.
	 *
	 * @param copy
	 * 		A new immutable copy of the FCHashMap that was just created.
	 */
	public void registerCopy(final FCHashMap<K, V> copy) {
		referenceCount.getAndIncrement();
		try {
			workQueue.put(copy);
		} catch (final InterruptedException ex) {
			log.error(EXCEPTION.getMarker(), "interrupted while registering FCHashMap copy");
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Inform the garbage collector of a mutation that will later require cleanup.
	 *
	 * @param key
	 * 		The key in the map where the mutation occurred.
	 * @param mutationQueue
	 * 		The mutation queue that holds a mutation that needs cleanup.
	 * @param version
	 * 		When this copy is no longer in memory then the mutation queue needs to be cleaned.
	 */
	public void registerGarbageCollectionEvent(K key, MutationQueue<V> mutationQueue, long version) {
		garbageCollectionEvents.addLast(new GarbageCollectionEvent<>(key, mutationQueue, version));
	}

	/**
	 * Find the next queue that needs to have garbage collection done.
	 *
	 * @param version
	 * 		A copy version that has expired.
	 * @return A mutation queue that needs cleaning. null if no mutation queues need cleaning (with the given version).
	 */
	private GarbageCollectionEvent<K, V> getNextGarbageCollectionEvent(final long version) {
		final GarbageCollectionEvent<K, V> next = garbageCollectionEvents.peekFirst();
		if (next != null && version + 1 >= next.getVersion()) {
			// When a queue contains a single (non-removal) element there is no need for garbage collection.
			// When a second element is added to the queue at version v, we know that when all copies older
			// than v expire we will need to clean the first element in the queue. The last copy to depend
			// on the first element in the queue is at version v-1, so when v-1 expires it is time to clean the
			// queue.
			garbageCollectionEvents.removeFirst();
			return next;
		}
		return null;
	}

	/**
	 * Given a queue and a version, remove mutations that are no longer needed by any copies.
	 *
	 * @param event
	 * 		Contains a queue that will require garbage collection.
	 * @param version
	 * 		The version of the oldest event that has just been evicted from memory.
	 */
	private void cleanQueue(final GarbageCollectionEvent<K, V> event, final long version) {
		synchronized (event.getMutationQueue()) {

			while (event.getMutationQueue().size() > 1) {
				// There will always be at least one element in the mutationQueue so getFirst() will never fail
				final Mutation<V> next = event.getMutationQueue().getFirst();

				// There exist no copies with an id lower or equal to version.
				// Delete a mutation that happened at or before this version.
				if (next.version <= version) {
					event.getMutationQueue().remove();
				} else {
					break;
				}
			}

			// Decide if the queue needs to be removed from the map after being cleaned.
			if (event.getMutationQueue().size() == 1 && event.getMutationQueue().getFirst().deleted) {
				event.getMutationQueue().delete();
				data.remove(event.getKey());
			}
		}
	}

	/**
	 * For debugging. Check if the garbage collector thread is still running.
	 *
	 * @return whether the garbage collector thread is still running
	 */
	public boolean isRunning() {
		return workQueue.isAlive();
	}

	/**
	 * This method is called every time there is a version to delete.
	 */
	private void handler(final FCHashMap<K, V> versionToDelete) throws InterruptedException {
		versionToDelete.waitUntilReleased();
		final long version = versionToDelete.version();

		GarbageCollectionEvent<K, V> nextEvent;
		while ((nextEvent = getNextGarbageCollectionEvent(version)) != null) {
			cleanQueue(nextEvent, version);
		}
	}
}
