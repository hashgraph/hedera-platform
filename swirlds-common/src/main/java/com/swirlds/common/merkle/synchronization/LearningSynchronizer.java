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

package com.swirlds.common.merkle.synchronization;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.io.MerkleDataInputStream;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import com.swirlds.common.merkle.synchronization.internal.LearnerThread;
import com.swirlds.common.merkle.synchronization.internal.Lesson;
import com.swirlds.common.merkle.synchronization.internal.QueryResponse;
import com.swirlds.common.merkle.synchronization.internal.ReconnectNodeCount;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.CustomReconnectRoot;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import com.swirlds.common.merkle.synchronization.views.StandardLearnerTreeView;
import com.swirlds.common.threading.StandardWorkGroup;
import com.swirlds.logging.payloads.SynchronizationCompletePayload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static com.swirlds.common.Units.MILLISECONDS_TO_SECONDS;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;

/**
 * Performs synchronization in the role of the learner.
 */
public class LearningSynchronizer implements ReconnectNodeCount {

	private static final String WORK_GROUP_NAME = "learning-synchronizer";

	private static final Logger LOG = LogManager.getLogger(LearningSynchronizer.class);

	/**
	 * Used to get data from the teacher.
	 */
	private final MerkleDataInputStream inputStream;

	/**
	 * Used to transmit data to the teacher.
	 */
	private final MerkleDataOutputStream outputStream;

	private final Queue<MerkleNode> rootsToReceive;
	private final Deque<LearnerTreeView<?>> viewsToInitialize;

	/**
	 * The root of the merkle tree that resulted from the synchronization operation.
	 */
	private MerkleNode newRoot;

	private final Runnable breakConnection;

	private int leafNodesReceived;
	private int internalNodesReceived;
	private int redundantLeafNodes;
	private int redundantInternalNodes;

	private long synchronizationTimeMilliseconds;
	private long hashTimeMilliseconds;
	private long initializationTimeMilliseconds;


	/**
	 * Create a new learning synchronizer.
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
	public LearningSynchronizer(
			final MerkleDataInputStream in,
			final MerkleDataOutputStream out,
			final MerkleNode root,
			final Runnable breakConnection) {

		inputStream = in;
		outputStream = out;

		rootsToReceive = new LinkedList<>();
		viewsToInitialize = new LinkedList<>();
		rootsToReceive.add(root);

		this.breakConnection = breakConnection;
	}

	/**
	 * Perform synchronization in the role of the learner.
	 */
	public void synchronize() throws InterruptedException {
		try {
			receiveTree();
			initialize();
			hash();
			logStatistics();
		} catch (final InterruptedException ex) {
			LOG.warn(RECONNECT.getMarker(), "synchronization interrupted");
			Thread.currentThread().interrupt();
			abort();
			throw ex;
		} catch (final Exception ex) {
			abort();
			throw new MerkleSynchronizationException(ex);
		}
	}

	/**
	 * Attempt to free any and all resources that were acquired during the reconnect attempt.
	 */
	private void abort() {
		LOG.warn(RECONNECT.getMarker(), "deleting partially constructed tree");
		try {
			if (newRoot != null) {
				newRoot.release();
			}
		} catch (final Exception ex) {
			// The tree may be in a partially constructed state. We don't expect exceptions, but they
			// may be more likely to appear during this operation than at other times.
			LOG.error(EXCEPTION.getMarker(), "exception thrown while releasing tree", ex);
		}
	}

	/**
	 * Receive the tree from the teacher.
	 */
	private void receiveTree() throws InterruptedException {
		LOG.info(RECONNECT.getMarker(), "synchronizing tree");
		final long start = System.currentTimeMillis();

		while (!rootsToReceive.isEmpty()) {
			final MerkleNode root = receiveTree(rootsToReceive.remove());
			if (newRoot == null) {
				// The first tree synchronized will contain the root of the tree as a whole
				newRoot = root;
			}
		}

		synchronizationTimeMilliseconds = System.currentTimeMillis() - start;
		LOG.info(RECONNECT.getMarker(), "synchronization complete");
	}

	/**
	 * Initialize the tree.
	 */
	private void initialize() {
		LOG.info(RECONNECT.getMarker(), "initializing tree");
		final long start = System.currentTimeMillis();

		while (!viewsToInitialize.isEmpty()) {
			viewsToInitialize.removeFirst().initialize();
		}

		initializationTimeMilliseconds = System.currentTimeMillis() - start;
		LOG.info(RECONNECT.getMarker(), "initialization complete");
	}

	/**
	 * Hash the tree.
	 */
	private void hash() throws InterruptedException {
		LOG.info(RECONNECT.getMarker(), "hashing tree");
		final long start = System.currentTimeMillis();

		try {
			CryptoFactory.getInstance().digestTreeAsync(newRoot).get();
		} catch (ExecutionException e) {
			LOG.error(EXCEPTION.getMarker(), "exception while computing hash of reconstructed tree", e);
			return;
		}

		hashTimeMilliseconds = System.currentTimeMillis() - start;
		LOG.info(RECONNECT.getMarker(), "hashing complete");
	}

	/**
	 * Log information about the synchronization.
	 */
	private void logStatistics() {
		LOG.info(RECONNECT.getMarker(), () -> new SynchronizationCompletePayload("Finished synchronization")
				.setTimeInSeconds(synchronizationTimeMilliseconds * MILLISECONDS_TO_SECONDS)
				.setHashTimeInSeconds(hashTimeMilliseconds * MILLISECONDS_TO_SECONDS)
				.setInitializationTimeInSeconds(initializationTimeMilliseconds * MILLISECONDS_TO_SECONDS)
				.setTotalNodes(leafNodesReceived + internalNodesReceived)
				.setLeafNodes(leafNodesReceived)
				.setRedundantLeafNodes(redundantLeafNodes)
				.setInternalNodes(internalNodesReceived)
				.setRedundantInternalNodes(redundantInternalNodes).toString());
	}

	/**
	 * Get the root of the resulting tree. May return an incomplete tree if called before synchronization is finished.
	 */
	public MerkleNode getRoot() {
		return newRoot;
	}

	/**
	 * Receive a tree (or subtree) from the teacher
	 *
	 * @param root
	 * 		the root of the tree (or subtree) that is already possessed
	 * @return the root of the reconstructed tree
	 */
	@SuppressWarnings("unchecked")
	private <T> MerkleNode receiveTree(final MerkleNode root) throws InterruptedException {

		LOG.info(RECONNECT.getMarker(), "receiving tree rooted at {} with route {}",
				root == null ? "(unknown)" : root.getClass().getName(),
				root == null ? "[]" : root.getRoute());

		final StandardWorkGroup workGroup = new StandardWorkGroup(WORK_GROUP_NAME, breakConnection);

		final LearnerTreeView<T> view;
		if (root == null || !root.hasCustomReconnectView()) {
			view = (LearnerTreeView<T>) new StandardLearnerTreeView(root);
		} else {
			assert root instanceof CustomReconnectRoot;
			view = ((CustomReconnectRoot<?, T>) root).buildLearnerView();
		}

		try (view) {
			final AsyncInputStream<Lesson<T>> in = new AsyncInputStream<>(
					inputStream, workGroup, () -> new Lesson<>(view));
			final AsyncOutputStream<QueryResponse> out = buildOutputStream(workGroup, outputStream);

			in.start();
			out.start();

			final AtomicReference<T> reconstructedRoot = new AtomicReference<>();

			new LearnerThread<>(workGroup, in, out, rootsToReceive, reconstructedRoot, view, this).start();

			workGroup.waitForTermination();

			if (workGroup.hasExceptions()) {

				// Depending on where the failure occurred, there may be deserialized objects still sitting in
				// the async input stream's queue that haven't been attached to any tree.
				in.abort();

				final MerkleNode merkleRoot = view.getMerkleRoot(reconstructedRoot.get());
				if (merkleRoot != null && merkleRoot.getReferenceCount() == 0) {
					// If the root has a reference count of 0 then it is not underneath any other tree,
					// and this thread holds the implicit reference to the root.
					// This is the last chance to release that tree in this scenario.
					LOG.warn(RECONNECT.getMarker(), "deleting partially constructed subtree");
					merkleRoot.release();
				}

				throw new MerkleSynchronizationException("Synchronization failed with exceptions");
			}

			viewsToInitialize.addFirst(view);

			return view.getMerkleRoot(reconstructedRoot.get());
		}
	}

	/**
	 * Build the output stream. Exposed to allow unit tests to override implementation to simulate latency.
	 */
	protected AsyncOutputStream<QueryResponse> buildOutputStream(
			final StandardWorkGroup workGroup,
			final SerializableDataOutputStream out) {
		return new AsyncOutputStream<>(out, workGroup);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void incrementLeafCount() {
		leafNodesReceived++;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void incrementRedundantLeafCount() {
		redundantLeafNodes++;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void incrementInternalCount() {
		internalNodesReceived++;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void incrementRedundantInternalCount() {
		redundantInternalNodes++;
	}
}
