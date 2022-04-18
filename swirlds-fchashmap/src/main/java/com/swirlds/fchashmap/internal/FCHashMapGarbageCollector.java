/*
 * (c) 2016-2022 Swirlds, Inc.
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
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import static com.swirlds.logging.LogMarker.ARCHIVE;
import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * This thread performs garbage collection on an FCHashMap in a background thread.
 */
public class FCHashMapGarbageCollector<K, V> {

	private static final Logger log = LogManager.getLogger();

	/**
	 * A reference to the internal data structure of the FCHashMap copies.
	 */
	private final Map<K, Mutation<V>> data;

	private final AtomicInteger referenceCount;

	private final QueueThread<FCHashMap<K, V>> workQueue;

	/**
	 * The size of the transfer buffer for the queue thread.
	 */
	private static final int QUEUE_BUFFER_SIZE = 1;

	private static final int QUEUE_SIZE_LOG_THRESHOLD = 50;
	private static final int WAIT_LOG_THRESHOLD_MS = 1_000;

	/**
	 * Contains a sequence of events that eventually require cleanup
	 */
	private final ConcurrentLinkedDeque<GarbageCollectionEvent<K>> garbageCollectionEvents;

	public FCHashMapGarbageCollector(final Map<K, Mutation<V>> data) {
		this.data = data;
		this.garbageCollectionEvents = new ConcurrentLinkedDeque<>();
		this.referenceCount = new AtomicInteger(1);

		final int maximumQueueSize = FCHashMapSettingsFactory.get().getMaximumGCQueueSize();
		final Duration thresholdPeriod = FCHashMapSettingsFactory.get().getGCQueueThresholdPeriod();

		final QueueThreadConfiguration<FCHashMap<K, V>> workQueueConfiguration =
				new QueueThreadConfiguration<FCHashMap<K, V>>()
						.setComponent("FCHashMap")
						.setThreadName("garbage-collector")
						.setHandler(this::handler)
						.setMaxBufferSize(QUEUE_BUFFER_SIZE)
						.setUnlimitedCapacity();

		if (FCHashMapSettingsFactory.get().isArchiveEnabled()) {
			workQueueConfiguration.addThreshold(
					size -> size > maximumQueueSize,
					size -> log.error(EXCEPTION.getMarker(), new GarbageCollectionQueuePayload(size)),
					thresholdPeriod);
		}

		this.workQueue = workQueueConfiguration.build();
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

			final int size = workQueue.size();
			if (size >= QUEUE_SIZE_LOG_THRESHOLD && size % QUEUE_SIZE_LOG_THRESHOLD == 0) {
				log.debug(ARCHIVE.getMarker(), "FCHashMapGarbageCollector queue contains {} copies", size);
			}

		} catch (final InterruptedException ex) {
			log.error(EXCEPTION.getMarker(), "interrupted while registering FCHashMap copy");
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Inform the garbage collector of a mutation that will later require cleanup.
	 *
	 * @param key
	 * 		the key in the map where the mutation occurred
	 * @param version
	 * 		when this copy is no longer in memory then the mutation queue needs to be cleaned
	 */
	public void registerGarbageCollectionEvent(final K key, final long version) {
		garbageCollectionEvents.addLast(new GarbageCollectionEvent<>(key, version));
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
	 * Given a queue and a version, remove mutations that are no longer needed by any copies.
	 *
	 * @param key
	 * 		the key that requires garbage collection
	 * @param version
	 * 		the version of the map that has been released
	 */
	private void cleanOldMutations(final K key, final long version) {
		data.compute(key, (final K k, final Mutation<V> mutationHead) -> {
			if (mutationHead == null) {
				return null;
			}

			Mutation<V> parent = mutationHead;
			Mutation<V> target = parent.getPrevious();

			while (target != null) {

				// truncate all older mutations
				if (target.getVersion() <= version) {
					parent.setPrevious(null);
					break;
				}

				parent = target;
				target = parent.getPrevious();
			}

			if (mutationHead.getPrevious() == null && mutationHead.getValue() == null) {
				// entry can be deleted if just a single deletion record remains
				return null;
			}

			return mutationHead;
		});
	}

	/**
	 * This method is called every time there is a map that requires garbage collection.
	 */
	protected void handler(final FCHashMap<K, V> mapToDelete) throws InterruptedException {

		final Instant start = Instant.now();
		mapToDelete.waitUntilReleased();
		final Instant finish = Instant.now();
		final Duration timeSpentWaiting = Duration.between(start, finish);
		if (timeSpentWaiting.toMillis() >= WAIT_LOG_THRESHOLD_MS) {
			log.debug(ARCHIVE.getMarker(), "spent " + timeSpentWaiting.toMillis() +
					"ms waiting for FCHashMap to be released");
		}

		final long version = mapToDelete.version();

		GarbageCollectionEvent<K> event;
		while ((event = garbageCollectionEvents.peekFirst()) != null) {
			if (event.getVersion() > version) {
				break;
			}

			garbageCollectionEvents.pop();
			cleanOldMutations(event.getKey(), version);
		}
	}
}
