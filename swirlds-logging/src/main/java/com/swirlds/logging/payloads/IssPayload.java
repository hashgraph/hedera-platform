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

package com.swirlds.logging.payloads;

/**
 * A message that gets logged when a node receives a signature for a state that is invalid.
 */
public class IssPayload extends AbstractLogPayload {
	private long round;
	private long nodeId;
	private long otherId;

	public IssPayload() {
	}

	/**
	 * Create a new payload for an ISS.
	 *
	 * @param message
	 * 		the human-readable message
	 * @param round
	 * 		the round for which the ISS was received
	 * @param nodeId
	 * 		this node
	 * @param otherId
	 * 		the node that sent the ISS
	 */
	public IssPayload(final String message, final long round, final long nodeId, final long otherId) {
		super(message);
		this.round = round;
		this.nodeId = nodeId;
		this.otherId = otherId;
	}

	/**
	 * Get the round when the ISS was observed.
	 */
	public long getRound() {
		return round;
	}

	/**
	 * Set the round when the ISS was observed.
	 */
	public void setRound(long round) {
		this.round = round;
	}

	/**
	 * Get the ID of this node.
	 */
	public long getNodeId() {
		return nodeId;
	}

	/**
	 * Set the ID of this node.
	 */
	public void setNodeId(long nodeId) {
		this.nodeId = nodeId;
	}

	/**
	 * Get the ID of the other node.
	 */
	public long getOtherId() {
		return otherId;
	}

	/**
	 * Set the ID of the other node.
	 */
	public void setOtherId(long otherId) {
		this.otherId = otherId;
	}
}
