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

package com.swirlds.logging.payloads;

/**
 * This payload is logged when a node begins a reconnect operation.
 */
public class ReconnectStartPayload extends AbstractLogPayload {

	private boolean receiving;
	private long nodeId;
	private long otherNodeId;
	private long round;

	public ReconnectStartPayload() {

	}

	/**
	 * @param message
	 * 		a human readable message
	 * @param receiving
	 * 		if true then this node is the receiver, i.e. it is the one attempting to reconnect.
	 * 		If false then this node is the sender and is helping another node to reconnect.
	 * @param nodeId
	 * 		this node's ID
	 * @param otherNodeId
	 * 		the other node's ID
	 * @param round
	 * 		the latest signed round known by this node
	 */
	public ReconnectStartPayload(
			final String message,
			final boolean receiving,
			final long nodeId,
			final long otherNodeId,
			final long round) {
		super(message);
		this.receiving = receiving;
		this.nodeId = nodeId;
		this.otherNodeId = otherNodeId;
		this.round = round;
	}

	public boolean isReceiving() {
		return receiving;
	}

	public void setReceiving(boolean receiving) {
		this.receiving = receiving;
	}

	public long getNodeId() {
		return nodeId;
	}

	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}

	public long getOtherNodeId() {
		return otherNodeId;
	}

	public void setOtherNodeId(int otherNodeId) {
		this.otherNodeId = otherNodeId;
	}

	public long getRound() {
		return round;
	}

	public void setRound(long round) {
		this.round = round;
	}
}
