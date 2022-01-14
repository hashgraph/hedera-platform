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

import java.util.LinkedList;
import java.util.List;

/**
 * Provides information about peers we tried to reconnect with
 */
public class ReconnectPeerInfoPayload extends AbstractLogPayload {
	/** A list of peers we tried to reconnect with */
	private final List<PeerInfo> peerInfo;

	public ReconnectPeerInfoPayload() {
		super("");
		peerInfo = new LinkedList<>();
	}

	public void addPeerInfo(final long peerId, final String info) {
		peerInfo.add(new PeerInfo(peerId, info));
	}

	public List<PeerInfo> getPeerInfo() {
		return peerInfo;
	}

	public static class PeerInfo {
		private final long node;
		private final String message;

		public PeerInfo(long node, String message) {
			this.node = node;
			this.message = message;
		}

		public long getNode() {
			return node;
		}

		public String getMessage() {
			return message;
		}
	}
}
