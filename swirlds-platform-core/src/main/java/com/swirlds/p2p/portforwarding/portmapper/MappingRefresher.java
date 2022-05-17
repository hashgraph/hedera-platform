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

package com.swirlds.p2p.portforwarding.portmapper;

import com.swirlds.p2p.portforwarding.PortForwarder;

public class MappingRefresher implements Runnable {
	private PortForwarder forwarder;
	private long sleep;

	public MappingRefresher(PortForwarder forwarder, long sleep) {
		super();
		this.forwarder = forwarder;
		this.sleep = sleep;
	}

	public void run() {
		// Refresh mapping half-way through the lifetime of the mapping (for example,
		// if the mapping is available for 40 seconds, refresh it every 20 seconds)
		while (true) {
			try {
				if (Thread.interrupted()) {
					Thread.currentThread().interrupt();
					return;
				}

				forwarder.refreshMappings();
				Thread.sleep(sleep);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}
}
