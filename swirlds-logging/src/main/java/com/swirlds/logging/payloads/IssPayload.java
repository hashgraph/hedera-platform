/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
