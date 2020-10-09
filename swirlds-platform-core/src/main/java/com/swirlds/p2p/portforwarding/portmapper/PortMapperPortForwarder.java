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

package com.swirlds.p2p.portforwarding.portmapper;

import com.offbynull.portmapper.PortMapperFactory;
import com.offbynull.portmapper.gateway.Bus;
import com.offbynull.portmapper.gateway.Gateway;
import com.offbynull.portmapper.gateways.network.NetworkGateway;
import com.offbynull.portmapper.gateways.network.internalmessages.KillNetworkRequest;
import com.offbynull.portmapper.gateways.process.ProcessGateway;
import com.offbynull.portmapper.gateways.process.internalmessages.KillProcessRequest;
import com.offbynull.portmapper.mapper.MappedPort;
import com.offbynull.portmapper.mapper.PortMapper;
import com.offbynull.portmapper.mapper.PortType;
import com.swirlds.p2p.portforwarding.PortForwarder;
import com.swirlds.p2p.portforwarding.PortMapping;
import com.swirlds.p2p.portforwarding.PortMappingListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PortMapperPortForwarder implements PortForwarder, Runnable {
	private static final int mappingDuration = 60;
	/** log marker related to port forwarding (uPNP & NAT-PMP) */
	private static final Marker MARKER = MarkerManager.getMarker("PORT_FORWARDING");
	private static final Logger log = LogManager.getLogger();

	private Bus networkBus;
	private Bus processBus;
	private PortMapper mapper;
	final private Queue<PortPair> ports = new ConcurrentLinkedQueue<>();
	private volatile String externalIp = null;
	private boolean successful = false;
	private Thread refresher;
	private final List<PortMappingListener> listeners = new LinkedList<>();

	public void addListener(PortMappingListener listener) {
		listeners.add(listener);
	}

	public void addPortMapping(String ip, int internalPort, int externalPort, Protocol protocol, String name) {
		addPortMapping(new PortMapping(ip, internalPort, externalPort, protocol));
	}

	public void addPortMapping(PortMapping portMapping) {
		ports.add(new PortPair(portMapping));
	}

	public void setPortMappings(List<PortMapping> portsToBeMapped) {
		ports.clear();
		for (PortMapping portMapping : portsToBeMapped) {
			addPortMapping(portMapping);
		}
	}

	public void execute() {
		try {
			// Start gateways
			Gateway network = NetworkGateway.create();
			Gateway process = ProcessGateway.create();
			networkBus = network.getBus();
			processBus = process.getBus();

			// Discover port forwarding devices and take the first one found
			List<PortMapper> mappers = PortMapperFactory.discover(networkBus, processBus);
			if (mappers.size() == 0) {
				for (PortMappingListener listener : listeners) {
					listener.noForwardingDeviceFound();
				}
				return;
			}

			mapper = mappers.get(0);

			// Map some internal port to some preferred external port
			//
			// IMPORTANT NOTE: Many devices prevent you from mapping ports that are <= 1024
			// (both internal and external ports). Be mindful of this when choosing which
			// ports you want to map.
			for (PortPair pair : ports) {
				MappedPort mappedPort;
				PortMapping mapping = pair.getSpecified();
				try {
					mappedPort = mapper.mapPort(
							PortType.valueOf(mapping.getProtocol().toString()),
							mapping.getInternalPort(),
							mapping.getExternalPort(), mappingDuration);
					pair.setActual(mappedPort);
					this.setExternalIp(
							mappedPort.getExternalAddress().getHostAddress());
					successful = true;
					for (PortMappingListener listener : listeners) {
						listener.mappingAdded(mapping);
					}
				} catch (NullPointerException | IllegalStateException
						| IllegalArgumentException e) {
					for (PortMappingListener listener : listeners) {
						listener.mappingFailed(mapping, e);
					}
				}
			}

			if (!successful) {
				// no ports were mapped
				return;
			}

			// start the refresher thread that will refresh the port mappings until stopped
			long minSleep = -1;
			for (PortPair pair : ports) {
				MappedPort mappedPort = pair.getActual();
				if (mappedPort == null) {
					continue;
				}
				// the port mapping is valid for getLifetime() seconds, so we will refresh
				// every getLifetime()/2 seconds, to make sure we refresh often enough.
				// Multiply by 1000 to get milliseconds, which is used by Thread.sleep().
				long sleep = mappedPort.getLifetime() * 1000 / 2;
				if (minSleep == -1 || sleep < minSleep) {
					minSleep = sleep;
				}
			}
			if (minSleep > 0) {
				refresher = new Thread(new MappingRefresher(this, minSleep),
						"MappingRefresher");
				refresher.setDaemon(true);
				refresher.start();
			}
		} catch (InterruptedException e) {
			closeService();
		} catch (Exception e) {
			log.error(MARKER, "An exception occurred while trying to do port forwarding:", e);
		}
	}

	public void refreshMappings() {
		Iterator<PortPair> i = ports.iterator();
		while (i.hasNext()) {
			PortPair pair = i.next();
			MappedPort mappedPort = pair.getActual();
			if (mappedPort == null) {
				i.remove();
				continue;
			}
			try {
				mappedPort = mapper.refreshPort(mappedPort,
						mappedPort.getLifetime());
				this.setExternalIp(
						mappedPort.getExternalAddress().getHostAddress());

			} catch (InterruptedException e) {
				closeService();
			} catch (NullPointerException | IllegalArgumentException | IllegalStateException e) {
				log.error(MARKER, "An exception occurred while refreshing a mapped port:", e);
				i.remove();
				for (PortMappingListener listener : listeners) {
					listener.mappingFailed(pair.getSpecified(), e);
				}
			}
		}
	}


	public String getExternalIPAddress() {
		return externalIp;
	}

	private void setExternalIp(String externalIp) {
		if (this.externalIp == null || !this.externalIp.equals(externalIp)) {
			this.externalIp = externalIp;
			for (PortMappingListener listener : listeners) {
				listener.foundExternalIp(externalIp);
			}
		}
	}

	public boolean isSuccessful() {
		return successful;
	}

	public void closeService() {
		if (refresher != null) {
			refresher.interrupt();
		}

		// Unmap port
		if (mapper != null) {
			for (PortPair pair : ports) {
				MappedPort mappedPort = pair.getActual();
				if (mappedPort == null) {
					continue;
				}
				try {
					mapper.unmapPort(mappedPort);
				} catch (Exception e) {
					log.error(MARKER, "An exception occurred while unmapping a port:", e);
				}
			}
		}

		// Stop gateways
		if (networkBus != null) {
			networkBus.send(new KillNetworkRequest());
		}
		if (processBus != null) {
			processBus.send(new KillProcessRequest());
		}
	}

	@Override
	public void run() {
		execute();
		if (!isSuccessful()) {
			// port forwarding doesn't work, shutdown the service
			closeService();
		}
	}

}
