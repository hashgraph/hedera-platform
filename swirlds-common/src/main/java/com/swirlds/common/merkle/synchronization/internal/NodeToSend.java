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

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.settings.ReconnectSettingsFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A wrapper object for a node that the sending synchronizer intends to send to the receiver.
 * These objects form a shadow tree that contains nodes that 1) have not yet received learner responses
 * and 2) have been or are about to be sent by the teacher.
 */
public class NodeToSend {

	/**
	 * A node that may be sent. Will not be sent if the learner tells the teacher that it has the node already.
	 */
	private final MerkleNode node;

	/**
	 * True if a response (positive or negative) has been received from the learner.
	 */
	private volatile boolean responseReceived;

	/**
	 * If true then the learner already has this node, if false then it must be sent.
	 */
	private volatile boolean responseStatus;

	/**
	 * If it comes time to send this node, but a response has not yet been heard from the learner, make sure that this
	 * amount of time has passed (since the query was sent) before sending the node.
	 */
	private final long unconditionalSendTimeMilliseconds;

	/**
	 * Wrappers around the children of this node. This list is only populated once the children have had a lesson
	 * sent for them.
	 */
	private final List<NodeToSend> children;

	/**
	 * Create an object that represents a node that may be sent in the future.
	 *
	 * @param node
	 * 		the node that may be sent.
	 */
	public NodeToSend(final MerkleNode node) {
		this.node = node;
		this.responseReceived = false;
		this.responseStatus = false;
		if (node != null && !node.isLeaf()) {
			children = new LinkedList<>();
		} else {
			children = null;
		}

		unconditionalSendTimeMilliseconds = System.currentTimeMillis() +
				ReconnectSettingsFactory.get().getMaxAckDelayMilliseconds();
	}

	/**
	 * Whenever the teacher sends a query to the learner about a node, it registers that node to its parent
	 * in this shadow tree. This allows a positive response corresponding to an ancestor to propagate information
	 * that can prevent the node from needing to be sent.
	 *
	 * @param child
	 * 		the node that is now eligible for sending. It is called a child because it is the child of a node that
	 * 		was just sent.
	 */
	public synchronized void registerChild(final NodeToSend child) {
		if (children == null) {
			throw new IllegalStateException("can not add children to leaf node");
		}
		children.add(child);

		child.responseStatus = responseStatus;
	}

	private synchronized void addChildrenToQueue(final Queue<NodeToSend> queue) {
		queue.addAll(children);
	}

	/**
	 * Get the merkle node that this object is wrapping.
	 */
	public MerkleNode getNode() {
		return node;
	}

	/**
	 * This method is called when the response for this node's query is received.
	 *
	 * @param learnerHasNode true if the learner has the node, otherwise false
	 */
	public void registerResponse(final boolean learnerHasNode) {
		if (learnerHasNode) {
			cancelTransmission();
		}
		responseReceived = true;
	}

	/**
	 * Return true if the learner has confirmed that it has the node in question
	 * (i.e. the node returned by {@link #getNode()}).
	 * @return
	 */
	public boolean getResponseStatus() {
		return responseStatus;
	}

	/**
	 * Wait for a resonse from the learner. Will return immediately if a response has already been received or
	 * if an ancestor has received a positive response. May sleep a short period if neither are true.
	 * There is no guarantee that a response will have been received when this method returns.
	 */
	public void waitForResponse() {
		if (responseReceived || responseStatus) {
			return;
		}

		final long currentTime = System.currentTimeMillis();
		if (currentTime > unconditionalSendTimeMilliseconds) {
			return;
		}

		final long sleepTime = unconditionalSendTimeMilliseconds - currentTime;
		try {
			MILLISECONDS.sleep(sleepTime);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Cancel the transmission of this node and all of its descendants.
	 */
	private void cancelTransmission() {
		final Queue<NodeToSend> queue = new LinkedList<>();
		queue.add(this);

		while (!queue.isEmpty()) {
			final NodeToSend next = queue.remove();
			if (next.responseStatus) {
				continue;
			}
			next.responseStatus = true;
			if (node != null && next.node != null && !next.node.isLeaf()) {
				next.addChildrenToQueue(queue);
			}
		}
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();

		sb.append("nodeToSend: response = ");

		if (!responseReceived) {
			sb.append("?");
		} else {
			sb.append(responseStatus);
		}

		sb.append(", node = ").append(node);

		return sb.toString();
	}
}
