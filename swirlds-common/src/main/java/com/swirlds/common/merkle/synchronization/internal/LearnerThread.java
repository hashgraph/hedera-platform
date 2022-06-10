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

package com.swirlds.common.merkle.synchronization.internal;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.CustomReconnectRoot;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.common.utility.ThresholdLimitingHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import static com.swirlds.common.constructable.ClassIdFormatter.classIdString;
import static com.swirlds.logging.LogMarker.RECONNECT;

/**
 * This class manages the learner's work thread for synchronization.
 *
 * @param <T>
 * 		the type of data used by the view to represent a node
 */
public class LearnerThread<T> {

	private static final Logger LOG = LogManager.getLogger(LearnerThread.class);

	private static final String NAME = "send-and-receive";

	private final StandardWorkGroup workGroup;
	private final AsyncInputStream<Lesson<T>> in;
	private final AsyncOutputStream<QueryResponse> out;
	private final AtomicReference<T> root;
	private final LearnerTreeView<T> view;
	private final ReconnectNodeCount nodeCount;

	private final Queue<MerkleNode> rootsToReceive;

	private final ThresholdLimitingHandler<Throwable> exceptionRateLimiter = new ThresholdLimitingHandler<>(1);

	/**
	 * Create a new thread for the learner.
	 *
	 * @param workGroup
	 * 		the work group that will manage the thread
	 * @param in
	 * 		the input stream, this object is responsible for closing the stream when finished
	 * @param out
	 * 		the output stream, this object is responsible for closing the stream when finished
	 * @param rootsToReceive
	 * 		a queue of subtree roots to synchronize
	 * @param root
	 * 		a reference which will eventually hold the root of this subtree
	 * @param view
	 * 		a view used to interface with the subtree
	 * @param nodeCount
	 * 		an object used to keep track of the number of nodes sent during the reconnect
	 */
	public LearnerThread(
			final StandardWorkGroup workGroup,
			final AsyncInputStream<Lesson<T>> in,
			final AsyncOutputStream<QueryResponse> out,
			final Queue<MerkleNode> rootsToReceive,
			final AtomicReference<T> root,
			final LearnerTreeView<T> view,
			final ReconnectNodeCount nodeCount) {
		this.workGroup = workGroup;
		this.in = in;
		this.out = out;
		this.rootsToReceive = rootsToReceive;
		this.root = root;
		this.view = view;
		this.nodeCount = nodeCount;
	}

	public void start() {
		workGroup.execute(NAME, this::run);
	}

	/**
	 * Handle a lesson for the root of a tree with a custom view. When such a node is encountered, instead of iterating
	 * over its children, the node is put into a queue for later handling. Eventually that node and the subtree
	 * below it are synchronized using the specified view.
	 */
	private T handleCustomRootInitialLesson(
			final LearnerTreeView<T> view,
			final ExpectedLesson<T> expectedLesson,
			final Lesson<T> lesson) {

		// The original node is the node at the exact same position in the learner's original tree.
		// If the hash matches the original can be used in the new tree.
		// Sometimes the original node is null if the original tree does not have any node in this position.
		final T originalNode = expectedLesson.getOriginalNode();

		final CustomReconnectRoot<?, ?> customRoot = ConstructableRegistry.createObject(lesson.getCustomViewClassId());
		if (customRoot == null) {
			throw new MerkleSynchronizationException(
					"unable to construct object with class ID " + classIdString(lesson.getCustomViewClassId()));
		}

		if (originalNode != null && view.getClassId(originalNode) == lesson.getCustomViewClassId()) {
			customRoot.setupWithOriginalNode(view.getMerkleRoot(originalNode));
		} else {
			customRoot.setupWithNoData();
		}

		rootsToReceive.add(customRoot);
		return view.convertMerkleRootToViewType(customRoot);
	}

	/**
	 * Based on the data in a lesson, get the node that should be inserted into the tree.
	 */
	private T extractNodeFromLesson(
			final LearnerTreeView<T> view,
			final ExpectedLesson<T> expectedLesson,
			final Lesson<T> lesson,
			boolean firstLesson) {

		if (lesson.isCurrentNodeUpToDate()) {
			// We already have the correct node in our tree.
			return expectedLesson.getOriginalNode();
		} else if (lesson.isCustomViewRoot()) {
			// This node is the root of a subtree with a custom view,
			// but we are not yet iterating over that subtree.
			return handleCustomRootInitialLesson(view, expectedLesson, lesson);
		} else {
			final T node;

			if (firstLesson && !view.isRootOfState()) {
				// Special case: roots of subtrees with custom views will have been copied
				// when synchronizing the parent tree.
				node = expectedLesson.getOriginalNode();
			} else {
				// The teacher sent us the node we should use
				node = lesson.getNode();
			}

			if (lesson.isInternalLesson()) {
				view.markForInitialization(node);
			}

			return node;
		}
	}

	/**
	 * Handle queries associated with a lesson.
	 */
	private void handleQueries(
			final LearnerTreeView<T> view,
			final AsyncInputStream<Lesson<T>> in,
			final AsyncOutputStream<QueryResponse> out,
			final List<Hash> queries,
			final T originalParent,
			final T newParent) throws InterruptedException {

		final int childCount = queries.size();
		for (int childIndex = 0; childIndex < childCount; childIndex++) {

			final T originalChild;
			if (view.isInternal(originalParent, true) && view.getNumberOfChildren(originalParent) > childIndex) {
				originalChild = view.getChild(originalParent, childIndex);
			} else {
				originalChild = null;
			}

			final Hash originalHash = view.getNodeHash(originalChild);

			final Hash teacherHash = queries.get(childIndex);
			if (originalHash == null) {
				exceptionRateLimiter.handle(new NullPointerException(),
						(error) -> LOG.warn(RECONNECT.getMarker(), "originalHash for node {} is null", originalChild));
			}
			final boolean nodeAlreadyPresent = originalHash != null && originalHash.equals(teacherHash);
			out.sendAsync(new QueryResponse(nodeAlreadyPresent));

			view.expectLessonFor(newParent, childIndex, originalChild, nodeAlreadyPresent);
			in.anticipateMessage();
		}
	}


	/**
	 * Update node counts for statistics.
	 */
	private void addToNodeCount(final ExpectedLesson<T> expectedLesson, final Lesson<T> lesson, final T newChild) {
		if (lesson.isCurrentNodeUpToDate()) {
			return;
		}

		if (view.isInternal(newChild, false)) {
			nodeCount.incrementInternalCount();
			if (expectedLesson.isNodeAlreadyPresent()) {
				nodeCount.incrementRedundantInternalCount();
			}
		} else {
			nodeCount.incrementLeafCount();
			if (expectedLesson.isNodeAlreadyPresent()) {
				nodeCount.incrementRedundantLeafCount();
			}
		}
	}

	/**
	 * Get the tree/subtree from the teacher.
	 */
	private void run() {
		boolean firstLesson = true;

		try (in; out; view) {

			view.startThreads(workGroup);

			view.expectLessonFor(null, 0, view.getOriginalRoot(), false);
			in.anticipateMessage();

			while (view.hasNextExpectedLesson()) {

				final ExpectedLesson<T> expectedLesson = view.getNextExpectedLesson();
				final Lesson<T> lesson = in.readAnticipatedMessage();

				final T parent = expectedLesson.getParent();

				final T newChild = extractNodeFromLesson(view, expectedLesson, lesson, firstLesson);

				firstLesson = false;

				if (parent == null) {
					root.set(newChild);
				} else {
					view.setChild(parent, expectedLesson.getPositionInParent(), newChild);
				}

				addToNodeCount(expectedLesson, lesson, newChild);

				if (lesson.hasQueries()) {
					final List<Hash> queries = lesson.getQueries();
					handleQueries(view, in, out, queries, expectedLesson.getOriginalNode(), newChild);
				}
			}

		} catch (final InterruptedException ex) {
			LOG.warn(RECONNECT.getMarker(), "learner thread interrupted");
			Thread.currentThread().interrupt();
		} catch (final Exception ex) {
			throw new MerkleSynchronizationException("exception in the learner's receiving thread", ex);
		}
	}

}
