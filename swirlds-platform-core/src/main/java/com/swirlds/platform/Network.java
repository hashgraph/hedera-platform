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
package com.swirlds.platform;

import com.swirlds.common.CommonUtils;
import com.swirlds.p2p.portforwarding.PortMapping;
import com.swirlds.p2p.portforwarding.PortMappingListener;
import com.swirlds.p2p.portforwarding.portmapper.PortMapperPortForwarder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * A set of static utility methods for finding all the local IP addresses associated with the local machine,
 * or checking whether a particular address is one of them. If the machine is behind a NATing router, then
 * it only deals with the local private addresses, not the external public addresses.
 */
public class Network {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	private static Collection<InetAddress> ownAddresses;
	private static String[] addresses;
	// port forwarder object
	private static PortMapperPortForwarder portForwarder;
	// thread executing the portforwarder
	private static Thread portForwarderThread;
	private static boolean noForwardingDeviceFound = false;

	/**
	 * All the utility methods are static, so the constructor is private, to prevent users from
	 * instantiating the class.
	 */
	private Network() {
	}

	/**
	 * Determine whether a given IP address is an address of this local computer. It can be IPv4 or IPv6. If
	 * there is Network Address Translation (NAT) occurring, then it only checks for the local, private
	 * address, not any public address.
	 *
	 * @param addr
	 * 		an IP address
	 * @return true if addr is an address of this local computer
	 * @throws SocketException
	 * 		if there are any errors getting the addreses
	 */
	static boolean isOwn(InetAddress addr) throws SocketException {
		return getOwnAddresses().contains(addr);
	}

	/**
	 * Return the external IP address (IPv4) of this computer. If none has been found yet, return the empty
	 * string. If the router doesn't have uPNP or NAT-PMP enabled, return a string asking the user to enable
	 * it.
	 *
	 * @return the address, or empty string, or warning message
	 */
	public static ExternalIpAddress getExternalIpAddress() {
		String ip = portForwarder == null ? null
				: portForwarder.getExternalIPAddress();
		if (noForwardingDeviceFound) {
			return ExternalIpAddress.UPNP_DISABLED;
		} else {
			if (ip == null) {
				return ExternalIpAddress.NO_IP;
			}

			return new ExternalIpAddress(ip);
		}
	}

	/**
	 * Return the IP address of this computer. If there are several, it returns the first one, with a
	 * preference for IPv4 over IPv6, and a preference for not using 128.0.0.1 if there is another IPv4
	 * address.
	 *
	 * @return the IP address of this machine, as a string such as "111.222.33.44"
	 */
	public static String getInternalIPAddress() {
		return getOwnAddresses2()[0];
	}

	/**
	 * Forwards the ports for every platform so they can be accessed externally
	 */
	static void doPortForwarding(List<PortMapping> portsToBeMapped) {
		PortMappingListener listener = new PortMappingListener() {
			public void noForwardingDeviceFound() {
				noForwardingDeviceFound = true;
				CommonUtils.tellUserConsole("No port forwarding device found."
						+ " Please enable uPNP or NAT-PMP on the router.");
			}

			public void mappingFailed(PortMapping mapping, Exception e) {
			}

			public void mappingAdded(PortMapping mapping) {
			}

			public void foundExternalIp(String ip) {
				CommonUtils.tellUserConsole(
						"This computer has an external IP address:  " + ip);
			}
		};

		// open a port for incoming connections
		portForwarder = new PortMapperPortForwarder();
		portForwarder.addListener(listener);
		portForwarder.setPortMappings(portsToBeMapped);

		// execute tries to open the ports specified above
		portForwarderThread = new Thread(portForwarder, "PortForwarder");
		portForwarderThread.setDaemon(true);
		portForwarderThread.start();
	}

	/**
	 * Unmaps all the ports that were previously mapped and stops the service
	 */
	static void stopPortForwarding() {
		if (portForwarderThread != null) {
			portForwarderThread.interrupt();
			portForwarder.closeService();
		}
	}

	/**
	 * Get an array of all the local IP addresses that refer to this computer, both IPv4 and IPv6. If there
	 * is Network Address Translation (NAT), then this only considers local, private addresses, and ignores
	 * the external, public addresses.
	 * <p>
	 * If there is a need to show the user only a single address, the first in the array should be good. The
	 * IPv4 addresses come before before the IPv6 addresses in the array. Within the IPv4 addresses, they
	 * are are sorted alphabetically, except 127.0.0.1 is last.
	 *
	 * @return an array of all local addresses, sorted by IP version (4 or 6), then alphabetically.
	 */
	static String[] getOwnAddresses2() {
		if (ownAddresses == null)
			try {
				ownAddresses = computeOwnAddresses();
			} catch (SocketException e) {
				log.error(EXCEPTION.getMarker(), "", e);
			}
		return addresses;
	}

	static Collection<InetAddress> getOwnAddresses() throws SocketException {
		return ownAddresses == null ? ownAddresses = computeOwnAddresses()
				: ownAddresses;
	}

	/**
	 * Recompute the set of IP addresses for this computer, and their string representations.
	 *
	 * @return the set of addresses
	 * @throws SocketException
	 * 		for errors while retrieving the addresses
	 */
	private static Collection<InetAddress> computeOwnAddresses()
			throws SocketException {
		final Set<InetAddress> result = new HashSet<>();
		final List<String> ip4 = new ArrayList<>();
		final List<String> ip6 = new ArrayList<>();

		for (Enumeration<NetworkInterface> interfaces = NetworkInterface
				.getNetworkInterfaces(); interfaces != null
					 && interfaces.hasMoreElements(); ) {
			for (Enumeration<InetAddress> addresses = interfaces.nextElement()
					.getInetAddresses(); addresses != null
						 && addresses.hasMoreElements(); ) {
				InetAddress addr = addresses.nextElement();
				result.add(addr);
				String ip = addr.getHostAddress();
				if (ip != null && !ip.equals("127.0.0.1")) {
					((addr instanceof Inet4Address) ? ip4 : ip6).add(ip);
				}
			}
		}

		Collections.sort(ip4);
		Collections.sort(ip6);
		ip4.add("127.0.0.1");
		ip4.addAll(ip6);
		addresses = ip4.toArray(new String[0]);
		return result;
	}
}
