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
 * Log payload used in {@code com.swirlds.platform.Browser} to indicate a condition where no local machine address
 * matched the loaded address book.
 */
public class NodeAddressMismatchPayload extends AbstractLogPayload {

	/**
	 * The internal IP address for this machine.
	 */
	private String internalIpAddress;

	/**
	 * The external IP address for this machine.
	 */
	private String externalIpAddress;

	/**
	 * Constructs a new payload with the specified {@code internalIpAddress} and no {@code externalIpAddress} specified.
	 *
	 * @param internalIpAddress
	 * 		the internal IP address.
	 * @throws IllegalArgumentException
	 * 		if the {@code internalIpAddress} argument is a null reference.
	 */
	public NodeAddressMismatchPayload(final String internalIpAddress) {
		this(internalIpAddress, null);
	}

	/**
	 * Constructs a new payload with the specified {@code internalIpAddress} and {@code externalIpAddress}.
	 *
	 * @param internalIpAddress
	 * 		the internal IP address.
	 * @param externalIpAddress
	 * 		the external IP address.
	 * @throws IllegalArgumentException
	 * 		if the {@code internalIpAddress} argument is a null reference.
	 */
	public NodeAddressMismatchPayload(final String internalIpAddress, final String externalIpAddress) {
		super("No AddressBook entry found for the available machine IP addresses");

		if (internalIpAddress == null) {
			throw new IllegalArgumentException("The supplied argument 'internalIpAddress' cannot be null!");
		}

		this.internalIpAddress = internalIpAddress;
		this.externalIpAddress = externalIpAddress;
	}

	/**
	 * Gets the internal IP address for this machine.
	 *
	 * @return the internal IP address.
	 */
	public String getInternalIpAddress() {
		return internalIpAddress;
	}

	/**
	 * Sets the internal IP address for this machine.
	 *
	 * @param internalIpAddress
	 * 		the internal IP address.
	 * @throws IllegalArgumentException
	 * 		if the {@code internalIpAddress} argument is a null reference.
	 */
	public void setInternalIpAddress(final String internalIpAddress) {
		if (internalIpAddress == null) {
			throw new IllegalArgumentException("The supplied argument 'internalIpAddress' cannot be null!");
		}
		this.internalIpAddress = internalIpAddress;
	}

	/**
	 * Gets the external IP address (if available) for this machine. This method may return a {@code null} reference.
	 *
	 * @return the external IP address.
	 */
	public String getExternalIpAddress() {
		return externalIpAddress;
	}

	/**
	 * Sets the external IP address (if available) for this machine.
	 *
	 * @param externalIpAddress
	 * 		the external IP address for this machine. A {@code null} reference may be passed if the external IP
	 * 		address is unavailable.
	 */
	public void setExternalIpAddress(final String externalIpAddress) {
		this.externalIpAddress = externalIpAddress;
	}
}
