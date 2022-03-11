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

package com.swirlds.platform.crypto;

import com.swirlds.platform.SettingsProvider;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * used to create and receive unencrypted TCP connections
 */
public class TcpFactory implements SocketFactory {
	private final SettingsProvider settings;

	public TcpFactory(final SettingsProvider settings) {
		this.settings = settings;
	}

	@Override
	public ServerSocket createServerSocket(final byte[] ipAddress, final int port) throws IOException {
		final ServerSocket serverSocket = new ServerSocket();
		SocketFactory.configureAndBind(serverSocket, settings, ipAddress, port);
		return serverSocket;
	}

	@Override
	public Socket createClientSocket(final String ipAddress, final int port) throws IOException {
		final Socket clientSocket = new Socket();
		SocketFactory.configureAndConnect(clientSocket, settings, ipAddress, port);
		return clientSocket;
	}
}
