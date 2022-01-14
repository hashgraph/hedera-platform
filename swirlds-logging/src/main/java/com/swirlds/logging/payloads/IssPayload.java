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
 * A message that gets logged when a node receives a signature for a state that is invalid.
 */
public class IssPayload extends AbstractLogPayload {
	private long round;
	private long nodeId;
	private long otherId;

	public IssPayload() {
	}

	/**
	 * @param round
	 * 		the round for which the ISS was received
	 * @param nodeId
	 * 		the node that received the ISS
	 * @param otherId
	 * 		the node that sent the ISS
	 */
	public IssPayload(long round, long nodeId, long otherId) {
		super("Received invalid state signature!");
		this.round = round;
		this.nodeId = nodeId;
		this.otherId = otherId;
	}

	public long getRound() {
		return round;
	}

	public void setRound(long round) {
		this.round = round;
	}

	public long getNodeId() {
		return nodeId;
	}

	public void setNodeId(long nodeId) {
		this.nodeId = nodeId;
	}

	public long getOtherId() {
		return otherId;
	}

	public void setOtherId(long otherId) {
		this.otherId = otherId;
	}
}
