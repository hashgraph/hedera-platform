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

import java.security.KeyStore;

/** private class to hold streaming server properties */
class StreamingAddress {
	enum StreamMode {
		STREAM_CONSENSUS_EVENT;
		// more can be added later
		
	    private static StreamMode[] allValues = values();
	    public static StreamMode fromOrdinal(int n) {return allValues[n];}
	};
	
	/** Ip address of stream server */
	String serverAddr;
	/** TCP port number of stream server */
	int port;
	/** stream mode */
	StreamMode mode = StreamMode.STREAM_CONSENSUS_EVENT;

	KeyStore trustStore = null; /** keystore to store peer's certifciate   */
	KeyStore privateKS = null;  /** keystore to save own private key pairs */
	public String getStreamAddr() {
		return serverAddr;
	}
	public int getPort() {
		return port;
	}
	public StreamMode getStreamMode() {
		return mode;
	}
	public KeyStore getTrustStore() {
		return trustStore;
	}
	public void setTrustStore(KeyStore trustStore) {
		this.trustStore = trustStore;
	}
	
	public KeyStore getPrivateKS() {
		return privateKS;
	}
	public void setPrivateKS(KeyStore privateKS) {
		this.privateKS = privateKS;
	}
	public StreamingAddress(String serverAddr, int port, StreamMode mode) {
		super();
		this.serverAddr = serverAddr;
		this.port = port;
		this.mode = mode;
	}	
	
}
