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

package com.swirlds.p2p.portforwarding.portmapper;

import com.offbynull.portmapper.mapper.MappedPort;
import com.swirlds.p2p.portforwarding.PortMapping;

class PortPair {
	private final PortMapping specified;
	private MappedPort actual = null;

	PortPair(PortMapping specified) {
		this.specified = specified;
	}

	public PortMapping getSpecified() {
		return specified;
	}

	synchronized MappedPort getActual() {
		return actual;
	}

	synchronized void setActual(MappedPort actual) {
		this.actual = actual;
	}
}
