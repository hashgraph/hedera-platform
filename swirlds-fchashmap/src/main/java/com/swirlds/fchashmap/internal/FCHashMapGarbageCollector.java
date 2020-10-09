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

package com.swirlds.fchashmap.internal;

import com.swirlds.fchashmap.FCHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * This thread performs garbage collection on an FCHashMap in a background thread.
 */
public class FCHashMapGarbageCollector<K, V> {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/**
	 * A reference to the internal data structure of the FCHashMap copies.
	 */
	private final ConcurrentHashMap<K, MutationQueue<V>> data;

	private final AtomicInteger referenceCount;

	private Thread thread;

	protected class VersionedCopy {
		FCHashMap<K, V> map;
		long version;

		public VersionedCopy(FCHashMap<K, V> copy, long version) {
			this.map = copy;
			this.version = version;
		}
	}

	/**
	 * Contains copies that are made of the mutable FCHashMap.
	 * Used to detect when copies are ready to be garbage collected.
	 */
	protected ConcurrentLinkedDeque<VersionedCopy> copies;

	protected class GarbageCollectionEvent {
		final K key;
		final MutationQueue<V> mutationQueue;
		final long version;

		public GarbageCollectionEvent(K key, MutationQueue<V> mutationQueue, long version) {
			this.key = key;
			this.mutationQueue = mutationQueue;
			this.version = version;
		}

		public String toString() {
			return "[" + key + "@" + version + ": " + mutationQueue + "]";
		}
	}

	/**
	 * Contains a sequence of events that eventually require cleanup
	 */
	protected ConcurrentLinkedDeque<GarbageCollectionEvent> garbageCollectionEvents;

	public FCHashMapGarbageCollector(ConcurrentHashMap<K, MutationQueue<V>> data) {
		this.data = data;
		this.copies = new ConcurrentLinkedDeque<>();
		this.garbageCollectionEvents = new ConcurrentLinkedDeque<>();
		this.referenceCount = new AtomicInteger(1);
	}

	/**
	 * Start the garbage collection thread.
	 */
	public void start() {
		thread = new Thread(this::run);
		thread.setDaemon(true);
		thread.setName("FCHashMap-garbage-collector");
		thread.start();
	}

	/**
	 * This should be called every time an FCHashMap is deleted. When the reference count reaches 0 the garbage
	 * collection thread terminates.
	 */
	public void decrementReferenceCount() {
		referenceCount.getAndDecrement();
	}

	/**
	 * The garbage collector must be given a copy of each new immutable FCHashMap that is created.
	 *
	 * @param copy
	 * 		A new immutable copy of the FCHashMap that was just created.
	 */
	public void registerCopy(FCHashMap<K, V> copy) {
		referenceCount.getAndIncrement();
		copies.addLast(new VersionedCopy(copy, copy.version()));
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
		garbageCollectionEvents.addLast(new GarbageCollectionEvent(key, mutationQueue, version));
	}

	/**
	 * Returns the version of the oldest copy that needs to be cleaned up.
	 * Returns null if the oldest copy is not ready to be cleaned up or if there are no copies.
	 */
	private Long getNextVersionToClean() {
		Long ret = null;
		VersionedCopy oldestCopy = copies.peekFirst();
		if (oldestCopy != null) {
			if (oldestCopy.map.isReleased()) {
				ret = oldestCopy.version;
				copies.removeFirst();
			}
		}
		return ret;
	}

	/**
	 * Find the next queue that needs to have garbage collection done.
	 *
	 * @param version
	 * 		A copy version that has expired.
	 * @return A mutation queue that needs cleaning. null if no mutation queues need cleaning (with the given version).
	 */
	private GarbageCollectionEvent getNextGarbageCollectionEvent(long version) {
		GarbageCollectionEvent next = garbageCollectionEvents.peekFirst();
		if (next != null) {
			if (version + 1 >= next.version) {
				// When a queue contains a single (non-removal) element there is no need for garbage collection.
				// When a second element is added to the queue at version v, we know that when all copies older
				// than v expire we will need to clean the first element in the queue. The last copy to depend
				// on the first element in the queue is at version v-1, so when v-1 expires it is time to clean the
				// queue.
				garbageCollectionEvents.removeFirst();
				return next;
			}
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
	private void cleanQueue(GarbageCollectionEvent event, long version) {
		synchronized (event.mutationQueue) {
			// Decide if the queue needs to be removed from the map.
			if (event.mutationQueue.size() == 1 && event.mutationQueue.getFirst().deleted) {
				event.mutationQueue.delete();
				data.remove(event.key);
				return;
			}

			while (!event.mutationQueue.isEmpty()) {
				// There will always be at least one element in the mutationQueue so getFirst() will never fail
				Mutation<V> next = event.mutationQueue.getFirst();

				// There exist no copies with an id lower or equal to version.
				// Delete a mutation that happened at or before this version.
				if (next.version <= version) {
					event.mutationQueue.remove();
				} else {
					break;
				}

			}

			// Decide if the queue needs to be removed from the map after being cleaned.
			if (event.mutationQueue.size() == 1 && event.mutationQueue.getFirst().deleted) {
				event.mutationQueue.delete();
				data.remove(event.key);
			}
		}
	}

	/**
	 * Attempt to remove old mutations that are no longer in any FCHashMap copies
	 *
	 * @return Returns true if work was done and false if no work was done
	 */
	private boolean pruneOldMutations() {
		Long version = getNextVersionToClean();
		if (version == null) {
			// No copies to clean up
			return false;
		}
		while (true) {
			GarbageCollectionEvent nextEvent = getNextGarbageCollectionEvent(version);
			if (nextEvent == null) {
				// No more queues for this version number
				break;
			}
			cleanQueue(nextEvent, version);
		}
		return true;
	}

	/**
	 * For debugging. Check if the garbage collector thread is still running.
	 */
	public boolean isRunning() {
		return thread.isAlive();
	}

	private void run() {
		try {
			while (referenceCount.get() > 0) {
				if (!pruneOldMutations()) {
					Thread.onSpinWait();
				}
			}
		} catch (Exception ex) {
			log.error(EXCEPTION.getMarker(),
					"Exception in FCHashMapGarbageCollector", ex);
		}
	}
}
