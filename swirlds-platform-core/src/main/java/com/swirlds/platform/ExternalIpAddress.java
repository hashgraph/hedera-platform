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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * External Ip Address found by {@link Network#getExternalIpAddress()}
 */
public class ExternalIpAddress {

	public static final ExternalIpAddress NO_IP = new ExternalIpAddress(IpAddressStatus.NO_IP_FOUND, "");
	public static final ExternalIpAddress UPNP_DISABLED = new ExternalIpAddress(IpAddressStatus.ROUTER_UPNP_DISABLED, "");
	private final IpAddressStatus status;
	private final String ipAddress;

	ExternalIpAddress(final IpAddressStatus status, final String ipAddress) {
		this.status = status;
		this.ipAddress = ipAddress;
	}

	ExternalIpAddress(final String ipAddress) {
		this(IpAddressStatus.IP_FOUND, ipAddress);
	}

	public IpAddressStatus getStatus() {
		return status;
	}

	/**
	 * If External IP address is found, then the address will be returned
	 * in ipv4 or ipv6 format. Otherwise, an empty string
	 * @return External ip address or blank
	 */
	public String getIpAddress() {
		return this.ipAddress;
	}

	/**
	 * If External IP address is found, it is returned in ipv4/ipv6 format.
	 * Otherwise, a string describing the status
	 *
	 * @return String representation of {@link ExternalIpAddress}
	 */
	@Override
	public String toString() {
		if (this.status == IpAddressStatus.IP_FOUND) {
			return this.getIpAddress();
		}

		return this.status.name();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ExternalIpAddress)) {
			return false;
		}

		final ExternalIpAddress that = (ExternalIpAddress) o;
		return new EqualsBuilder()
				.append(status, that.status)
				.append(ipAddress, that.ipAddress)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder()
				.append(status)
				.append(ipAddress)
				.hashCode();
	}
}
