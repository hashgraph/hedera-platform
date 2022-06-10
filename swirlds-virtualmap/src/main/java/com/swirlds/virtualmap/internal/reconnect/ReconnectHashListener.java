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

package com.swirlds.virtualmap.internal.reconnect;

import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.Path;
import com.swirlds.virtualmap.internal.hash.VirtualHashListener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A {@link VirtualHashListener} implementation used by the learner during reconnect. During reconnect, the dirty
 * leaves will be sent from the teacher to the learner in a breadth-first order. The hashing algorithm in the
 * {@link com.swirlds.virtualmap.internal.hash.VirtualHasher} is setup to hash enormous trees in breadth-first order.
 * As the hasher hashes, it notifies this listener which then stores up the changes into different sorted lists.
 * Then, when the "batch" is completed, it flushes the data in the proper order to the data source. This process
 * completely bypasses the {@link com.swirlds.virtualmap.internal.cache.VirtualNodeCache} and the
 * {@link com.swirlds.virtualmap.internal.pipeline.VirtualPipeline}, which is essential for performance and memory
 * reasons, since during reconnect we may need to process the entire data set, which is too large to fit in memory.
 * <p>
 * Three things are required for this listener to work: the {@code firstLeafPath}, the {@code lastLeafPath}, and
 * the {@link VirtualDataSource}.
 * <p>
 * A tree is broken up into "ranks" where "rank 0" is the on that contains root, "rank 1" is the one that contains
 * the left and right children of root, "rank 2" has the children of the nodes in "rank 1", and so forth. The higher
 * the rank, the deeper in the tree the rank lives.
 * <p>
 * A "batch" is a portion of the tree that is independently hashed. The batch will always be processed from the
 * deepest rank (the leaves) to the lowest rank (nearest the top). When we flush, we flush in the opposite order
 * from the closest to the top of the tree to the deepest rank. Each rank is processed in ascending path order.
 * So we store each rank as a separate array and then stream them out in the proper order to disk.
 *
 * @param <K>
 * 		The key
 * @param <V>
 * 		The value
 */
public class ReconnectHashListener<K extends VirtualKey<? super K>, V extends VirtualValue>
		implements VirtualHashListener<K, V> {
	private static final int INITIAL_BATCH_ARRAY_SIZE = 10_000;
	// Maybe it would be better to have the whole state instead of just first/last leaf path so we can use stats
	// while flushing and reconnecting...
	private final VirtualDataSource<K, V> dataSource;
	private final ReconnectNodeRemover<K, V> nodeRemover;
	private final long firstLeafPath;
	private final long lastLeafPath;
	private final List<List<VirtualLeafRecord<K, V>>> batchLeaves = new ArrayList<>();
	private final List<List<VirtualInternalRecord>> batchInternals = new ArrayList<>();
	private List<VirtualLeafRecord<K, V>> rankLeaves;
	private List<VirtualInternalRecord> rankInternals;

	/**
	 * Create a new {@link ReconnectHashListener}.
	 *
	 * @param firstLeafPath
	 * 		The first leaf path. Must be a valid path.
	 * @param lastLeafPath
	 * 		The last leaf path. Must be a valid path.
	 * @param dataSource
	 * 		The data source. Cannot be null.
	 */
	public ReconnectHashListener(
			final long firstLeafPath,
			final long lastLeafPath,
			final VirtualDataSource<K, V> dataSource,
			final ReconnectNodeRemover<K, V> nodeRemover) {

		if (firstLeafPath != Path.INVALID_PATH && !(firstLeafPath > 0 && firstLeafPath <= lastLeafPath)) {
			throw new IllegalArgumentException("The first leaf path is invalid. firstLeafPath=" + firstLeafPath
					+ ", lastLeafPath=" + lastLeafPath);
		}

		if (lastLeafPath != Path.INVALID_PATH && !(lastLeafPath > 0)) {
			throw new IllegalArgumentException("The last leaf path is invalid. firstLeafPath=" + firstLeafPath
					+ ", lastLeafPath=" + lastLeafPath);
		}

		this.firstLeafPath = firstLeafPath;
		this.lastLeafPath = lastLeafPath;
		this.dataSource = Objects.requireNonNull(dataSource);
		this.nodeRemover = nodeRemover;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onBatchStarted() {
		batchLeaves.clear();
		batchInternals.clear();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onRankStarted() {
		rankLeaves = new ArrayList<>(INITIAL_BATCH_ARRAY_SIZE);
		rankInternals = new ArrayList<>(INITIAL_BATCH_ARRAY_SIZE);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onInternalHashed(VirtualInternalRecord internal) {
		rankInternals.add(internal);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onLeafHashed(final VirtualLeafRecord<K, V> leaf) {
		rankLeaves.add(leaf);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onRankCompleted() {
		batchLeaves.add(rankLeaves);
		batchInternals.add(rankInternals);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onBatchCompleted() {
		long maxPath = -1;

		Stream<VirtualInternalRecord> sortedDirtyInternals = Stream.of();
		for (int i = batchInternals.size() - 1; i >= 0; i--) {
			final List<VirtualInternalRecord> batch = batchInternals.get(i);
			if (!batch.isEmpty()) {
				sortedDirtyInternals = Stream.concat(sortedDirtyInternals, batch.stream());
				maxPath = Math.max(maxPath, batch.get(batch.size() - 1).getPath());
			}
		}

		Stream<VirtualLeafRecord<K, V>> sortedDirtyLeaves = Stream.of();
		for (int i = batchLeaves.size() - 1; i >= 0; i--) {
			final List<VirtualLeafRecord<K, V>> batch = batchLeaves.get(i);
			if (!batch.isEmpty()) {
				sortedDirtyLeaves = Stream.concat(sortedDirtyLeaves, batch.stream());
				maxPath = Math.max(maxPath, batch.get(batch.size() - 1).getPath());
			}
		}

		final Stream<VirtualLeafRecord<K, V>> leavesToRemove = nodeRemover.getRecordsToDelete(maxPath);

		// flush it down
		try {
			dataSource.saveRecords(
					firstLeafPath,
					lastLeafPath,
					sortedDirtyInternals,
					sortedDirtyLeaves,
					leavesToRemove);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
