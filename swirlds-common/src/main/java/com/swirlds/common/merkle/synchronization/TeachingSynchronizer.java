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

package com.swirlds.common.merkle.synchronization;

import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.merkle.synchronization.internal.Lesson;
import com.swirlds.common.merkle.synchronization.internal.QueryResponse;
import com.swirlds.common.merkle.synchronization.internal.TeacherReceivingThread;
import com.swirlds.common.merkle.synchronization.internal.TeacherSendingThread;
import com.swirlds.common.merkle.synchronization.internal.TeacherSubtree;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.swirlds.logging.LogMarker.RECONNECT;

/**
 * Performs synchronization in the role of the teacher.
 */
public class TeachingSynchronizer {

	private static final String WORK_GROUP_NAME = "teaching-synchronizer";

	private static final Logger LOG = LogManager.getLogger(TeachingSynchronizer.class);

	/**
	 * Used to get data from the listener.
	 */
	private final MerkleDataInputStream inputStream;

	/**
	 * Used to transmit data to the listener.
	 */
	private final MerkleDataOutputStream outputStream;

	/**
	 * <p>
	 * Subtrees that require reconnect using a custom view.
	 * </p>
	 *
	 * <p>
	 * Although multiple threads may modify this queue, it is still thread safe. This is because only one thread
	 * will attempt to read/write this data structure at any time, and when the thread touching the queue changes
	 * there is a synchronization point that establishes a happens before relationship.
	 * </p>
	 */
	private final Queue<TeacherSubtree> subtrees;

	private final Runnable breakConnection;

	/**
	 * Create a new teaching synchronizer.
	 *
	 * @param in
	 * 		the input stream
	 * @param out
	 * 		the output stream
	 * @param root
	 * 		the root of the tree
	 * @param breakConnection
	 * 		a method that breaks the connection. Used iff
	 * 		an exception is encountered. Prevents deadlock
	 * 		if there is a thread stuck on a blocking IO
	 * 		operation that will never finish due to a
	 * 		failure.
	 */
	public TeachingSynchronizer(
			final MerkleDataInputStream in,
			final MerkleDataOutputStream out,
			final MerkleNode root,
			final Runnable breakConnection) {

		inputStream = in;
		outputStream = out;

		subtrees = new LinkedList<>();
		subtrees.add(new TeacherSubtree(root));

		this.breakConnection = breakConnection;
	}

	/**
	 * Perform synchronization in the role of the teacher.
	 */
	public void synchronize() throws InterruptedException {
		try {
			while (!subtrees.isEmpty()) {
				try (final TeacherSubtree subtree = subtrees.remove()) {
					subtree.getView().waitUntilReady();
					sendTree(subtree.getRoot(), subtree.getView());
				}
			}
		} finally {
			// If we crash, make sure to clean up any remaining subtrees.
			for (final TeacherSubtree subtree : subtrees) {
				subtree.close();
			}
		}
	}

	/**
	 * Send a tree (or subtree).
	 */
	private <T> void sendTree(final MerkleNode root, final TeacherTreeView<T> view) throws InterruptedException {
		LOG.info(RECONNECT.getMarker(), "sending tree rooted at {} with route {}",
				root == null ? null : root.getClass().getName(),
				root == null ? "[]" : root.getRoute());

		// A future improvement might be to reuse threads between subtrees.
		final StandardWorkGroup workGroup = new StandardWorkGroup(WORK_GROUP_NAME, breakConnection);

		final AsyncInputStream<QueryResponse> in =
				new AsyncInputStream<>(inputStream, workGroup, QueryResponse::new);
		final AsyncOutputStream<Lesson<T>> out = buildOutputStream(workGroup, outputStream);

		in.start();
		out.start();

		final AtomicBoolean senderIsFinished = new AtomicBoolean(false);

		new TeacherSendingThread<T>(workGroup, in, out, subtrees, view, senderIsFinished).start();
		new TeacherReceivingThread<>(workGroup, in, view, senderIsFinished).start();

		workGroup.waitForTermination();

		if (workGroup.hasExceptions()) {
			throw new MerkleSynchronizationException("Synchronization failed with exceptions");
		}

		LOG.info(RECONNECT.getMarker(), "finished sending tree");
	}

	/**
	 * Build the output stream. Exposed to allow unit tests to override implementation to simulate latency.
	 */
	protected <T> AsyncOutputStream<Lesson<T>> buildOutputStream(
			final StandardWorkGroup workGroup,
			final SerializableDataOutputStream out) {
		return new AsyncOutputStream<>(out, workGroup);
	}
}
