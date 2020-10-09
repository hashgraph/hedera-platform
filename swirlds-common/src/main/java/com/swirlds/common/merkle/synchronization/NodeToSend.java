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

package com.swirlds.common.merkle.synchronization;

import com.swirlds.common.merkle.MerkleNode;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * A wrapper object for a node that the sending synchronizer intends to send to the receiver.
 */
public class NodeToSend {

	private final MerkleNode node;

	private volatile boolean ackReceived;
	private volatile boolean ackStatus;
	private final long unconditionalSendTimeMilliseconds;

	private List<NodeToSend> children;

	public NodeToSend(MerkleNode node) {
		this.node = node;
		this.ackReceived = false;
		this.ackStatus = false;
		if (node != null && !node.isLeaf()) {
			children = new LinkedList<>();
		}
		unconditionalSendTimeMilliseconds = System.currentTimeMillis() +
				ReconnectSettingsFactory.get().getMaxAckDelayMilliseconds();
	}

	public synchronized void addChildren(List<NodeToSend> children) {
		this.children.addAll(children);
	}

	public synchronized void addChildrenToQueue(Queue<NodeToSend> queue) {
		queue.addAll(children);
	}

	public MerkleNode getNode() {
		return node;
	}

	/**
	 * This method is called when the ack for this node is received.
	 * @param affirmative
	 */
	public void registerAck(boolean affirmative) {
		if (affirmative) {
			cancelTransmission();
		}
		ackReceived = true;
	}

	public boolean getAckStatus() {
		return ackStatus;
	}

	/**
	 * Wait for an ack. Will return immediately if an ack has already been received or if an ancestor has received
	 * a positive ack. May sleep a short period if neither are true. There is no guarantee that an ack will have been
	 * received when this method returns.
	 */
	public void waitForAck() {
		if (ackReceived || ackStatus) {
			return;
		}

		long currentTime = System.currentTimeMillis();
		if (currentTime > unconditionalSendTimeMilliseconds) {
			return;
		}

		long sleepTime = unconditionalSendTimeMilliseconds - currentTime;
		try {
			Thread.sleep(sleepTime);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Cancel the transmission of this node and all of its descendants.
	 */
	private void cancelTransmission() {
		Queue<NodeToSend> queue = new LinkedList<>();
		queue.add(this);

		while (queue.size() > 0) {
			NodeToSend next = queue.remove();
			if (next.ackStatus) {
				continue;
			}
			next.ackStatus = true;
			if (node != null && next.node != null && !next.node.isLeaf()) {
				next.addChildrenToQueue(queue);
			}
		}
	}
}
