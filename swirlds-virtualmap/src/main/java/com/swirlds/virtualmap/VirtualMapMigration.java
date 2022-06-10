/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.virtualmap;

import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.RecordAccessor;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A utility for migrating data within a virtual map from one format to another.
 */
public final class VirtualMapMigration {

	private VirtualMapMigration() {

	}

	private static final int QUEUE_CAPACITY = 10_000;

	private static final String COMPONENT_NAME = "virtual-map-migration";

	/**
	 * Extract all key-value pairs from a virtual map and pass it to a handler.
	 *
	 * @param source
	 * 		a virtual map to read from, will not be modified by this method
	 * @param threadCount
	 * 		the number of threads used for reading from the original map
	 * @param <K>
	 * 		the type of the key
	 * @param <V>
	 * 		the type of the value
	 */
	public static <K extends VirtualKey<? super K>, V extends VirtualValue> void extractVirtualMapData(
			final VirtualMap<K, V> source,
			final InterruptableConsumer<Pair<K, V>> handler,
			final int threadCount) throws InterruptedException {

		final long firstLeafPath = source.getState().getFirstLeafPath();
		final long lastLeafPath = source.getState().getLastLeafPath();

		final RecordAccessor<K, V> recordAccessor = source.getRoot().getRecords();

		final long size = source.size();

		// A collection of threads iterate over the map. Each thread writes into its own output queue.
		final List<Thread> threads = new ArrayList<>(threadCount);
		final List<BlockingQueue<Pair<K, V>>> threadQueues = new ArrayList<>(threadCount);

		for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {

			final BlockingQueue<Pair<K, V>> queue =
					new LinkedBlockingQueue<>(QUEUE_CAPACITY);
			threadQueues.add(queue);

			// Java only allows final values to be passed into a lambda
			final int index = threadIndex;

			threads.add(new ThreadConfiguration()
					.setComponent(COMPONENT_NAME)
					.setThreadName("reader-" + threadCount)
					.setInterruptableRunnable(() -> {
						for (long path = firstLeafPath + index; path <= lastLeafPath; path += threadCount) {
							final VirtualLeafRecord<K, V> leafRecord =
									recordAccessor.findLeafRecord(path, false);
							queue.put(Pair.of(leafRecord.getKey(), leafRecord.getValue()));
						}
					})
					.build(true));
		}

		// Buffers for reading from the thread output queues.
		final List<List<Pair<K, V>>> buffers = new ArrayList<>(threadCount);
		final List<Integer> bufferIndices = new ArrayList<>(threadCount);
		for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
			buffers.add(new ArrayList<>(QUEUE_CAPACITY));
			bufferIndices.add(0);
		}

		// Aggregate values from queues and pass them to the handler.
		for (int index = 0; index < size; index++) {
			try {

				final int queueIndex = index % threadCount;

				final BlockingQueue<Pair<K, V>> queue = threadQueues.get(queueIndex);
				final List<Pair<K, V>> buffer = buffers.get(queueIndex);

				// Fill the buffer if needed
				int bufferIndex = bufferIndices.get(queueIndex);
				while (buffer.size() <= bufferIndex) {
					buffer.clear();
					queue.drainTo(buffer, QUEUE_CAPACITY);
					bufferIndex = 0;
				}

				handler.accept(buffer.get(bufferIndex));

				bufferIndices.set(queueIndex, bufferIndex + 1);

			} catch (final InterruptedException e) {
				// If we are interrupted, stop all of the reader threads
				threads.forEach(Thread::interrupt);
				throw e;
			}
		}
	}
}
