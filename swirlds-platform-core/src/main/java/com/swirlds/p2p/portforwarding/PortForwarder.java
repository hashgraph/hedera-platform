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

package com.swirlds.p2p.portforwarding;

import java.util.List;

public interface PortForwarder {

	public void addListener(PortMappingListener listener);

	public void addPortMapping(String ip, int internalPort, int externalPort,
			Protocol protocol, String name);

	public void setPortMappings(List<PortMapping> portsToBeMapped);

	public void execute();

	public void refreshMappings();

	public String getExternalIPAddress();

	public boolean isSuccessful();

	public void closeService();

	public enum Protocol {
		TCP, UDP
	}

}
